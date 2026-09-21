package com.cinema.booking.dto.payments;

import java.math.BigDecimal;

public record BakongCheckResult(
        boolean paid,
        String status, // "PAID", "PENDING", "FAILED", "NOT_FOUND", "VERIFICATION_ERROR"
        String message, 
        String hash,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        String currency,
        boolean authoritative,
        boolean retryable
) {
    public static BakongCheckResult paid(String hash, String fromAccountId, String toAccountId) {
        return paid(hash, fromAccountId, toAccountId, null, null);
    }

    public static BakongCheckResult paid(String hash, String fromAccountId, String toAccountId,
                                         BigDecimal amount, String currency) {
        return new BakongCheckResult(true, "PAID", "Transaction confirmed successfully", hash,
                fromAccountId, toAccountId, amount, currency, true, false);
    }

    public static BakongCheckResult pending(String hash, String message) {
        // A successful HTTP response with Bakong responseCode=1 is an
        // authoritative answer that this MD5 is not paid yet.
        return new BakongCheckResult(false, "PENDING", message, hash, null, null, null, null, true, true);
    }

    public static BakongCheckResult notFound(String hash) {
        return new BakongCheckResult(false, "NOT_FOUND", "Transaction not found on Bakong network",
                hash, null, null, null, null, true, true);
    }

    public static BakongCheckResult failed(String hash, String message) {
        return new BakongCheckResult(false, "FAILED", message, hash, null, null, null, null, false, false);
    }

    public static BakongCheckResult verificationError(String hash, String message) {
        // Configuration, transport, and malformed-response failures are not
        // proof that the customer did not pay.
        return new BakongCheckResult(false, "VERIFICATION_ERROR", message, hash, null, null, null, null, false, true);
    }
}
