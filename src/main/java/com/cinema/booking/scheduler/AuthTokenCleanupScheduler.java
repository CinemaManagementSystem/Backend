package com.cinema.booking.scheduler;

import com.cinema.booking.security.AuthTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthTokenCleanupScheduler {

    private final AuthTokenService authTokenService;

    @Scheduled(fixedRateString = "${jwt.token-cleanup-rate-ms:3600000}")
    public void deleteExpiredTokens() {
        authTokenService.deleteExpiredTokens();
    }
}
