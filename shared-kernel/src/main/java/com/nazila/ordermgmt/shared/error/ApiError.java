package com.nazila.ordermgmt.shared.error;

import java.time.Instant;
import java.util.List;

/**
 * Centralized error response shape returned by every service in the
 * platform. Keeping this in shared-kernel means a client only ever has to
 * learn one error contract, regardless of which service answered the
 * request.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> fieldViolations
) {

    public static ApiError of(int status, String error, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, List.of());
    }

    public static ApiError withFieldViolations(int status, String error, String message, String path,
                                                String correlationId, List<FieldViolation> fieldViolations) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, fieldViolations);
    }

    public record FieldViolation(String field, String message) {
    }
}
