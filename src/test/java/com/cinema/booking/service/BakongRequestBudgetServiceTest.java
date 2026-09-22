package com.cinema.booking.service;

import com.cinema.booking.repository.BakongRequestBudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.scheduling.enabled=false",
        "bakong.mock-mode=false",
        "bakong.token=test-production-token",
        "bakong.email=test@cinema.local",
        "bakong.daily-request-limit=2"
})
class BakongRequestBudgetServiceTest {

    @Autowired
    private BakongRequestBudgetService service;

    @Autowired
    private BakongRequestBudgetRepository repository;

    @BeforeEach
    void resetBudget() {
        repository.deleteAll();
    }

    @Test
    void globalDailyBudgetBlocksRequestsBeyondConfiguredLimit() {
        assertTrue(service.tryAcquire().allowed());
        assertTrue(service.tryAcquire().allowed());

        BakongRequestBudgetService.Permit blocked = service.tryAcquire();

        assertFalse(blocked.allowed());
        assertTrue(blocked.retryAfterSeconds() > 0);
    }

    @Test
    void providerRateLimitPersistsCooldownForAllPayments() {
        assertTrue(service.tryAcquire().allowed());
        service.markRateLimited(600L);

        BakongRequestBudgetService.Permit blocked = service.tryAcquire();

        assertFalse(blocked.allowed());
        assertTrue(blocked.retryAfterSeconds() > 500);
    }
}
