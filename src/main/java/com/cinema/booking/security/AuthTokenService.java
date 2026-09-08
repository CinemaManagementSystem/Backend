package com.cinema.booking.security;

import com.cinema.booking.entity.RefreshToken;
import com.cinema.booking.entity.RevokedAccessToken;
import com.cinema.booking.entity.User;
import com.cinema.booking.repository.RefreshTokenRepository;
import com.cinema.booking.repository.RevokedAccessTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private static final int REFRESH_TOKEN_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpirationMillis;

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateRawRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        LocalDateTime now = now();

        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setUser(user);
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plusSeconds(refreshExpirationMillis / 1000L));

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public TokenRefreshResult rotateRefreshToken(String rawToken) {
        RefreshToken currentToken = getActiveRefreshToken(rawToken);
        currentToken.setRevokedAt(now());
        refreshTokenRepository.save(currentToken);

        String newRefreshToken = createRefreshToken(currentToken.getUser());
        return new TokenRefreshResult(currentToken.getUser(), newRefreshToken);
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hashToken(rawToken.trim()))
                .ifPresent(refreshToken -> {
                    if (refreshToken.getRevokedAt() == null) {
                        refreshToken.setRevokedAt(now());
                        refreshTokenRepository.save(refreshToken);
                    }
                });
    }

    @Transactional
    public void revokeAccessToken(String bearerToken) {
        String token = extractBearerToken(bearerToken);
        if (token == null) {
            return;
        }

        String tokenId;
        Date expiration;
        try {
            tokenId = jwtService.extractTokenId(token);
            expiration = jwtService.extractExpiration(token);
        } catch (Exception ex) {
            return;
        }
        if (tokenId == null || tokenId.isBlank() || expiration.before(new Date())) {
            return;
        }

        if (!revokedAccessTokenRepository.existsByTokenId(tokenId)) {
            RevokedAccessToken revokedAccessToken = new RevokedAccessToken();
            revokedAccessToken.setTokenId(tokenId);
            revokedAccessToken.setRevokedAt(now());
            revokedAccessToken.setExpiresAt(LocalDateTime.ofInstant(expiration.toInstant(), ZoneOffset.UTC));
            revokedAccessTokenRepository.save(revokedAccessToken);
        }
    }

    public boolean isAccessTokenRevoked(String token) {
        String tokenId = jwtService.extractTokenId(token);
        return tokenId != null
                && revokedAccessTokenRepository.existsByTokenIdAndExpiresAtAfter(tokenId, now());
    }

    @Transactional
    public void deleteExpiredTokens() {
        LocalDateTime now = now();
        refreshTokenRepository.deleteByExpiresAtBefore(now);
        revokedAccessTokenRepository.deleteByExpiresAtBefore(now);
    }

    private RefreshToken getActiveRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashToken(rawToken.trim()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!refreshToken.isActive(now())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        return refreshToken;
    }

    private String generateRawRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String extractBearerToken(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return null;
        }
        return bearerToken.substring(7).trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public record TokenRefreshResult(User user, String refreshToken) {
    }
}
