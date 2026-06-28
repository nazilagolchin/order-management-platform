package com.nazila.ordermgmt.order.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(

        @NotNull(message = "customerId is required")
        UUID customerId,

        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO 4217 code, e.g. USD")
        String currency,

        @NotEmpty(message = "an order must contain at least one item")
        @Valid
        List<OrderItemRequest> items
) {
}
