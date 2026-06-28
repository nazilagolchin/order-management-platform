package com.nazila.ordermgmt.shared.exception;

/**
 * Thrown when a request conflicts with the current state of the resource —
 * e.g. an idempotency key reused with a different payload, or a concurrent
 * update losing an optimistic-locking race. Mapped to {@code 409 Conflict}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
