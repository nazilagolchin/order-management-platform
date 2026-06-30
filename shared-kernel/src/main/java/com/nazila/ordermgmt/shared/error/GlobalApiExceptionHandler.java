package com.nazila.ordermgmt.shared.error;

import com.nazila.ordermgmt.shared.correlation.CorrelationContext;
import com.nazila.ordermgmt.shared.exception.BusinessRuleViolationException;
import com.nazila.ordermgmt.shared.exception.ConflictException;
import com.nazila.ordermgmt.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Single source of truth for how every service in the platform turns an
 * exception into an HTTP response. Centralizing this in shared-kernel means
 * an API consumer (or interviewer reading the code) only has to learn one
 * error contract, see {@link ApiError}, no matter which service answered.
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLocking(OptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "The resource was modified concurrently. Please retry with the latest version.", request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiError> handleBusinessRuleViolation(BusinessRuleViolationException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiError.FieldViolation(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ApiError body = ApiError.withFieldViolations(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more fields.",
                request.getRequestURI(),
                CorrelationContext.current(),
                violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("IllegalArgumentException on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                CorrelationContext.current());
        return ResponseEntity.status(status).body(body);
    }
}
