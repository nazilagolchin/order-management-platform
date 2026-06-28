package com.nazila.ordermgmt.order.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(

        @NotNull(message = "productId is required")
        UUID productId,

        @Positive(message = "quantity must be greater than zero")
        int quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.01", message = "unitPrice must be greater than zero")
        BigDecimal unitPrice
) {
}
