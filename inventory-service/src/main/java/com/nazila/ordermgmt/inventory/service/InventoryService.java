package com.nazila.ordermgmt.inventory.service;

import com.nazila.ordermgmt.events.OrderCreatedEvent;

import java.util.UUID;

public interface InventoryService {

    /**
     * Reacts to {@code OrderCreatedEvent}: reserves stock for every line
     * item, all-or-nothing, and records the outcome as an outbox event. Must
     * be safe to call more than once for the same order (Kafka delivery is
     * at least once) — see {@code docs/saga-flow.md}.
     */
    void reserveStock(OrderCreatedEvent event, String correlationId);

    int getAvailableQuantity(UUID productId);
}
