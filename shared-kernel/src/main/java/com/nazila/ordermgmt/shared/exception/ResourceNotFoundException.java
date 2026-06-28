package com.nazila.ordermgmt.shared.exception;

/**
 * Thrown when a client references an entity that does not exist.
 * Mapped to {@code 404 Not Found} by each service's exception handler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
