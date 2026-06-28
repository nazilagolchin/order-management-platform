package com.nazila.ordermgmt.shared.exception;

/**
 * Thrown when a request is well-formed but violates a domain invariant
 * (e.g. cancelling an order that already shipped). Mapped to
 * {@code 422 Unprocessable Entity}.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
