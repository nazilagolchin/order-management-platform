package com.nazila.ordermgmt.order.outbox;

import com.nazila.ordermgmt.shared.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OrderOutboxEvent extends OutboxEvent {

    protected OrderOutboxEvent() {
        // JPA
    }

    private OrderOutboxEvent(UUID id, UUID orderId, String eventType, String payload, int eventVersion, String correlationId) {
        super(id, "Order", orderId, eventType, payload, eventVersion, correlationId, Instant.now());
    }

    public static OrderOutboxEvent of(UUID eventId, UUID orderId, String eventType, String payload,
                                       int eventVersion, String correlationId) {
        return new OrderOutboxEvent(eventId, orderId, eventType, payload, eventVersion, correlationId);
    }
}
