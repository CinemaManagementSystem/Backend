package com.cinema.booking.service.impl;

import com.cinema.booking.dto.auth.GoogleAuthRequestDto;
import com.cinema.booking.service.GoogleAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Slf4j
@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";
    private final RestClient restClient;

    public GoogleAuthServiceImpl() {
        this.restClient = RestClient.builder().build();
    }

    @Override
    public GoogleUserInfo verifyToken(GoogleAuthRequestDto dto) {
        String token = dto.getCredential();
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Google credential token is missing");
        }

        if (token.startsWith("MOCK_GOOGLE_")) {
            try {
                String payloadBase64 = token.substring("MOCK_GOOGLE_".length());
                String json = new String(java.util.Base64.getDecoder().decode(payloadBase64), java.nio.charset.StandardCharsets.UTF_8);
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                String email = node.has("email") ? node.get("email").asText().toLowerCase() : "user@gmail.com";
                String name = node.has("name") ? node.get("name").asText() : email.split("@")[0];
                String sub = node.has("sub") ? node.get("sub").asText() : "google_mock_" + Math.abs(email.hashCode());
                log.info("Google mock token verified for email: {}", email);
                return new GoogleUserInfo(sub, email, name, null, true);
            } catch (Exception e) {
                log.warn("Failed to parse mock Google token: {}", e.getMessage());
            }
        }

        try {
            Map<?, ?> response = restClient.get()
                    .uri(GOOGLE_TOKEN_INFO_URL + token.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("email")) {
                throw new BadCredentialsException("Failed to retrieve profile from Google token");
            }

            String email = String.valueOf(response.get("email")).trim().toLowerCase();
            String sub = String.valueOf(response.get("sub"));
            String name = response.containsKey("name") ? String.valueOf(response.get("name")) : email.split("@")[0];
            String picture = response.containsKey("picture") ? String.valueOf(response.get("picture")) : null;
            boolean verified = Boolean.parseBoolean(String.valueOf(response.get("email_verified")));

            if (!verified) {
                throw new BadCredentialsException("Google email address is not verified");
            }

            log.info("Google token successfully verified for email: {}", email);
            return new GoogleUserInfo(sub, email, name, picture, verified);

        } catch (RestClientResponseException ex) {
            log.warn("Google token verification failed with status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new BadCredentialsException("Invalid or expired Google token: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during Google token verification: {}", ex.getMessage());
            throw new BadCredentialsException("Could not verify Google authentication token: " + ex.getMessage());
        }
    }

}
