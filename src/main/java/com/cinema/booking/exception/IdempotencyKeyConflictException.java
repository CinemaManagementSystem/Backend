package com.cinema.booking.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("This Idempotency-Key was already used for a different booking request.");
    }
}
