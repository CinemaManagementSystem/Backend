package com.cinema.booking.security.ratelimit;

import com.cinema.booking.util.SecurityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RateLimiterService {

    private static final long DEFAULT_WINDOW_SECONDS = 60L;
    private static final String UNKNOWN_IP = "unknown";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> requestCounts = new ConcurrentHashMap<>();

    @Value("${rate.limit.enabled:true}")
    private boolean enabled;

    @Value("${rate.limit.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    private final List<RateLimitPolicy> policies = List.of(
            policy("auth-login", "POST", "/api/auth/login", 5, KeyStrategy.IP_AND_LOGIN_PRINCIPAL, "anonymous"),
            policy("auth-register", "POST", "/api/auth/register", 3, KeyStrategy.IP, "anonymous"),
            policy("auth-refresh", "POST", "/api/auth/refresh", 10, KeyStrategy.IP_AND_REFRESH_TOKEN, "anonymous"),
            policy("auth-logout", "POST", "/api/auth/logout", 20, KeyStrategy.USER_OR_IP, "authenticated user"),

            policy("payment-create", "POST", "/api/payments", 5, KeyStrategy.USER_OR_IP, "USER"),
            policy("payment-status", "GET", "/api/payments/*/status", 30, KeyStrategy.USER_AND_PATH, "USER"),
            policy("payment-confirm", "POST", "/api/payments/*/confirm", 10, KeyStrategy.USER_OR_IP, "STAFF/ADMIN"),

            policy("booking-create", "POST", "/api/bookings", 10, KeyStrategy.USER_OR_IP, "USER"),
            policy("booking-seat-create", "POST", "/api/booking-seats", 30, KeyStrategy.USER_OR_IP, "USER"),

            policy("order-create", "POST", "/api/orders", 10, KeyStrategy.USER_OR_IP, "USER"),
            policy("order-item-create", "POST", "/api/order-items", 30, KeyStrategy.USER_OR_IP, "USER"),

            policy("product-create-upload", "POST", "/api/products", 10, KeyStrategy.USER_OR_IP, "STAFF/ADMIN"),
            policy("product-update-upload", "PUT", "/api/products/*", 10, KeyStrategy.USER_OR_IP, "STAFF/ADMIN"),

            writePolicy("locations-write", "/api/locations/**"),
            writePolicy("theaters-write", "/api/theaters/**"),
            writePolicy("screens-write", "/api/screens/**"),
            writePolicy("seats-write", "/api/seats/**"),
            writePolicy("movie-category-write", "/api/movie-category/**"),
            writePolicy("movies-write", "/api/movies/**"),
            writePolicy("shows-write", "/api/shows/**"),
            writePolicy("users-write", "/api/users/**"),
            writePolicy("product-categories-write", "/api/product-categories/**"),
            writePolicy("payment-transactions-write", "/api/payment-transactions/**"),

            policy("api-read-fallback", "GET", "/api/**", 100, KeyStrategy.IP, "public/authenticated read"),
            policy("api-fallback", "*", "/api/**", 100, KeyStrategy.IP, "authenticated")
    );

    public RateLimitCheck check(HttpServletRequest request, String requestBody) {
        RateLimitPolicy policy = findPolicy(request);
        if (!enabled || policy == null) {
            return RateLimitCheck.allowed(policy);
        }

        String key = policy.id() + ":" + resolveKey(policy, request, requestBody);
        long windowMillis = policy.windowSeconds() * 1000L;
        long now = System.currentTimeMillis();
        long windowStart = now - windowMillis;

        ConcurrentLinkedQueue<Long> timestamps = requestCounts.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>());
        synchronized (timestamps) {
            evictExpired(timestamps, windowStart);
            if (timestamps.size() < policy.maxRequests()) {
                timestamps.add(now);
                return RateLimitCheck.allowed(policy);
            }

            Long oldest = timestamps.peek();
            long retryAfterSeconds = oldest == null
                    ? policy.windowSeconds()
                    : Math.max(1L, ((oldest + windowMillis) - now + 999L) / 1000L);
            return RateLimitCheck.blocked(policy, retryAfterSeconds);
        }
    }

    public boolean requiresCachedBody(HttpServletRequest request) {
        String method = normalizeMethod(request.getMethod());
        String path = request.getRequestURI();
        return "POST".equals(method)
                && ("/api/auth/login".equals(path) || "/api/auth/refresh".equals(path));
    }

    public List<RateLimitPolicy> getPolicies() {
        return policies;
    }

    public void reset() {
        requestCounts.clear();
    }

    private RateLimitPolicy findPolicy(HttpServletRequest request) {
        String method = normalizeMethod(request.getMethod());
        String path = request.getRequestURI();
        return policies.stream()
                .filter(policy -> methodMatches(policy.method(), method))
                .filter(policy -> pathMatcher.match(policy.pattern(), path))
                .findFirst()
                .orElse(null);
    }

    private String normalizeMethod(String method) {
        return method == null ? "" : method.toUpperCase(Locale.ROOT);
    }

    private boolean methodMatches(String policyMethod, String requestMethod) {
        return "*".equals(policyMethod)
                || policyMethod.equals(requestMethod)
                || ("WRITE".equals(policyMethod) && WRITE_METHODS.contains(requestMethod));
    }

    private String resolveKey(RateLimitPolicy policy, HttpServletRequest request, String requestBody) {
        String clientIp = extractClientIp(request);
        String principal = currentPrincipal();
        return switch (policy.keyStrategy()) {
            case IP -> clientIp;
            case IP_AND_LOGIN_PRINCIPAL -> clientIp + ":" + loginPrincipal(requestBody);
            case IP_AND_REFRESH_TOKEN -> clientIp + ":" + refreshTokenFingerprint(requestBody);
            case USER_OR_IP -> principal.isBlank() ? clientIp : principal;
            case USER_AND_PATH -> (principal.isBlank() ? clientIp : principal) + ":" + request.getRequestURI();
        };
    }

    private String currentPrincipal() {
        return SecurityUtil.getCurrentUsername()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .orElse("");
    }

    private String loginPrincipal(String requestBody) {
        JsonNode root = readJson(requestBody);
        if (root == null) {
            return "unknown-principal";
        }
        String username = textField(root, "username");
        if (!username.isBlank()) {
            return username;
        }
        String email = textField(root, "email");
        return email.isBlank() ? "unknown-principal" : email;
    }

    private String refreshTokenFingerprint(String requestBody) {
        JsonNode root = readJson(requestBody);
        if (root == null) {
            return "unknown-refresh-token";
        }
        String token = textField(root, "refreshToken");
        return token.isBlank() ? "unknown-refresh-token" : sha256Prefix(token);
    }

    private JsonNode readJson(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(requestBody);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String textField(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value == null || value.asText().isBlank()
                ? ""
                : value.asText().trim().toLowerCase(Locale.ROOT);
    }

    private String sha256Prefix(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : UNKNOWN_IP;
    }

    private void evictExpired(ConcurrentLinkedQueue<Long> timestamps, long windowStart) {
        while (!timestamps.isEmpty()) {
            Long oldest = timestamps.peek();
            if (oldest != null && oldest < windowStart) {
                timestamps.poll();
            } else {
                break;
            }
        }
    }

    private static RateLimitPolicy policy(
            String id,
            String method,
            String pattern,
            int maxRequests,
            KeyStrategy keyStrategy,
            String role
    ) {
        return new RateLimitPolicy(id, method, pattern, maxRequests, DEFAULT_WINDOW_SECONDS, keyStrategy, role);
    }

    private static RateLimitPolicy writePolicy(String id, String pattern) {
        return policy(id, "WRITE", pattern, 30, KeyStrategy.USER_OR_IP, "STAFF/ADMIN");
    }

    public enum KeyStrategy {
        IP,
        IP_AND_LOGIN_PRINCIPAL,
        IP_AND_REFRESH_TOKEN,
        USER_OR_IP,
        USER_AND_PATH
    }

    public record RateLimitCheck(boolean allowed, long retryAfterSeconds, RateLimitPolicy policy) {
        static RateLimitCheck allowed(RateLimitPolicy policy) {
            return new RateLimitCheck(true, 0L, policy);
        }

        static RateLimitCheck blocked(RateLimitPolicy policy, long retryAfterSeconds) {
            return new RateLimitCheck(false, retryAfterSeconds, policy);
        }
    }

    public record RateLimitPolicy(
            String id,
            String method,
            String pattern,
            int maxRequests,
            long windowSeconds,
            KeyStrategy keyStrategy,
            String role
    ) {
    }
}
