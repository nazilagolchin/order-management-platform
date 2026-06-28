package com.nazila.ordermgmt.shared.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    @Test
    void ofProducesErrorWithoutFieldViolations() {
        ApiError error = ApiError.of(404, "Not Found", "Order not found", "/api/orders/123", "corr-1");

        assertThat(error.status()).isEqualTo(404);
        assertThat(error.fieldViolations()).isEmpty();
        assertThat(error.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void withFieldViolationsCarriesEachViolation() {
        var violations = java.util.List.of(new ApiError.FieldViolation("customerId", "must not be blank"));

        ApiError error = ApiError.withFieldViolations(400, "Bad Request", "Validation failed",
                "/api/orders", "corr-2", violations);

        assertThat(error.fieldViolations()).hasSize(1);
        assertThat(error.fieldViolations().get(0).field()).isEqualTo("customerId");
    }
}
