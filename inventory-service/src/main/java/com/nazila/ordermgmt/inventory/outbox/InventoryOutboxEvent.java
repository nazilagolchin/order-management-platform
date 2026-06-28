package com.nazila.ordermgmt.inventory.outbox;

import com.nazila.ordermgmt.shared.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class InventoryOutboxEvent extends OutboxEvent {

    protected InventoryOutboxEvent() {
        // JPA
    }

    private InventoryOutboxEvent(UUID id, UUID orderId, String eventType, String payload, int eventVersion, String correlationId) {
        super(id, "Order", orderId, eventType, payload, eventVersion, correlationId, Instant.now());
    }

    public static InventoryOutboxEvent of(UUID eventId, UUID orderId, String eventType, String payload,
                                           int eventVersion, String correlationId) {
        return new InventoryOutboxEvent(eventId, orderId, eventType, payload, eventVersion, correlationId);
    }
}
