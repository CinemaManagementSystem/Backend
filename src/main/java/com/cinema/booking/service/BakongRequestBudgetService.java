package com.cinema.booking.service;

import java.time.LocalDateTime;

public interface BakongRequestBudgetService {

    Permit tryAcquire();

    LocalDateTime markRateLimited(Long retryAfterSeconds);

    record Permit(boolean allowed, long retryAfterSeconds, int used, int limit, String message) {
        public static Permit allowed(int used, int limit) {
            return new Permit(true, 0, used, limit, null);
        }

        public static Permit blocked(long retryAfterSeconds, String message) {
            return new Permit(false, retryAfterSeconds, 0, 0, message);
        }
    }
}
