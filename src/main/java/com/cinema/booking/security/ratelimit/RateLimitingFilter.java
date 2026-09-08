package com.cinema.booking.security.ratelimit;

import com.cinema.booking.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        HttpServletRequest requestToUse = request;
        String requestBody = null;

        if (rateLimiterService.requiresCachedBody(request)) {
            CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
            requestToUse = cachedRequest;
            requestBody = cachedRequest.getCachedBodyAsString();
        }

        RateLimiterService.RateLimitCheck check = rateLimiterService.check(requestToUse, requestBody);
        if (!check.allowed()) {
            writeRateLimitResponse(response, requestToUse, check.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(requestToUse, response);
    }

    private void writeRateLimitResponse(
            HttpServletResponse response,
            HttpServletRequest request,
            long retryAfter
    ) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfter));

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Rate limit exceeded. Please try again in " + retryAfter + " seconds.")
                .path(request.getRequestURI())
                .build();

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;
        private final Charset charset;

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.charset = request.getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(request.getCharacterEncoding());
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        private String getCachedBodyAsString() {
            return new String(cachedBody, charset);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Synchronous servlet processing does not need async callbacks here.
                }

                @Override
                public int read() {
                    return inputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
