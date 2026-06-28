package com.nazila.ordermgmt.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Published by order-service when a new order is persisted in {@code PENDING}. */
public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        String currency,
        BigDecimal totalAmount,
        List<OrderLineItem> items) {
}
