package com.cinema.booking.service;

import com.cinema.booking.config.KhqrConfig;
import com.cinema.booking.entity.BakongRequestBudget;
import com.cinema.booking.repository.BakongRequestBudgetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
public class BakongRequestBudgetService {

    private static final ZoneId PHNOM_PENH_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private final BakongRequestBudgetRepository repository;
    private final KhqrConfig config;
    private final TransactionTemplate transactionTemplate;

    public BakongRequestBudgetService(
            BakongRequestBudgetRepository repository,
            KhqrConfig config,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.config = config;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Permit tryAcquire() {
        if (config.isMockMode()) {
            return Permit.allowed(0, config.getDailyRequestLimit());
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Permit permit = transactionTemplate.execute(status -> acquireInTransaction());
                return permit != null ? permit : Permit.blocked(secondsUntilTomorrow(), "Bakong request budget is unavailable");
            } catch (DataIntegrityViolationException ex) {
                if (attempt == 1) throw ex;
                // Another instance created today's budget row first. Retry in a new transaction.
            }
        }
        return Permit.blocked(secondsUntilTomorrow(), "Bakong request budget is unavailable");
    }

    public LocalDateTime markRateLimited(Long retryAfterSeconds) {
        LocalDateTime now = now();
        LocalDateTime cooldown = retryAfterSeconds != null && retryAfterSeconds > 0
                ? now.plusSeconds(retryAfterSeconds)
                : tomorrow();
        return transactionTemplate.execute(status -> {
            BakongRequestBudget budget = getOrCreateTodayForUpdate();
            if (budget.getRateLimitedUntil() == null || budget.getRateLimitedUntil().isBefore(cooldown)) {
                budget.setRateLimitedUntil(cooldown);
                repository.save(budget);
            }
            log.warn("Bakong global cooldown activated until {} after provider rate limit", budget.getRateLimitedUntil());
            return budget.getRateLimitedUntil();
        });
    }

    private Permit acquireInTransaction() {
        BakongRequestBudget budget = getOrCreateTodayForUpdate();
        LocalDateTime now = now();
        if (budget.getRateLimitedUntil() != null && now.isBefore(budget.getRateLimitedUntil())) {
            return Permit.blocked(secondsBetween(now, budget.getRateLimitedUntil()),
                    "Bakong verification is temporarily unavailable. Please try again later.");
        }
        int limit = Math.max(1, Math.min(config.getDailyRequestLimit(), 100));
        if (budget.getRequestCount() >= limit) {
            budget.setRateLimitedUntil(tomorrow());
            repository.save(budget);
            return Permit.blocked(secondsBetween(now, budget.getRateLimitedUntil()),
                    "Bakong daily verification budget has been reached.");
        }
        budget.setRequestCount(budget.getRequestCount() + 1);
        repository.save(budget);
        log.debug("Bakong daily request permit granted: used={}, limit={}", budget.getRequestCount(), limit);
        return Permit.allowed(budget.getRequestCount(), limit);
    }

    private BakongRequestBudget getOrCreateTodayForUpdate() {
        LocalDate today = LocalDate.now(PHNOM_PENH_ZONE);
        return repository.findByDateForUpdate(today).orElseGet(() -> {
            BakongRequestBudget created = new BakongRequestBudget();
            created.setRequestDate(today);
            created.setRequestCount(0);
            return repository.saveAndFlush(created);
        });
    }

    private LocalDateTime now() {
        return LocalDateTime.now(PHNOM_PENH_ZONE);
    }

    private LocalDateTime tomorrow() {
        return LocalDate.now(PHNOM_PENH_ZONE).plusDays(1).atStartOfDay();
    }

    private long secondsUntilTomorrow() {
        return secondsBetween(now(), tomorrow());
    }

    private long secondsBetween(LocalDateTime start, LocalDateTime end) {
        return Math.max(1L, Duration.between(start, end).getSeconds());
    }

    public record Permit(boolean allowed, long retryAfterSeconds, int used, int limit, String message) {
        static Permit allowed(int used, int limit) {
            return new Permit(true, 0, used, limit, null);
        }

        static Permit blocked(long retryAfterSeconds, String message) {
            return new Permit(false, retryAfterSeconds, 0, 0, message);
        }
    }
}
