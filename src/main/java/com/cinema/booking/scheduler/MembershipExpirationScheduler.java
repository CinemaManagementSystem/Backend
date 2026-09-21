package com.cinema.booking.scheduler;

import com.cinema.booking.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MembershipExpirationScheduler {

    private final MembershipService membershipService;

    @Scheduled(fixedDelayString = "${membership.expiry-rate-ms:3600000}")
    public void expireMemberships() {
        membershipService.expireMemberships();
    }
}
