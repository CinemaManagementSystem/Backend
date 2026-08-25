package com.cinema.booking.dto.payments;

public record BakongCheckResult(
        boolean paid,
        String status, // "PAID", "PENDING", "FAILED", "NOT_FOUND"
        String message, 
        String hash,
        String fromAccountId,
        String toAccountId
) {
    public static BakongCheckResult paid(String hash, String fromAccountId, String toAccountId) {
        return new BakongCheckResult(true, "PAID", "Transaction confirmed successfully", hash, fromAccountId, toAccountId);
    }

    public static BakongCheckResult pending(String hash, String message) {
        return new BakongCheckResult(false, "PENDING", message, hash, null, null);
    }

    public static BakongCheckResult notFound(String hash) {
        return new BakongCheckResult(false, "NOT_FOUND", "Transaction not found on Bakong network", hash, null, null);
    }

    public static BakongCheckResult failed(String hash, String message) {
        return new BakongCheckResult(false, "FAILED", message, hash, null, null);
    }
}
