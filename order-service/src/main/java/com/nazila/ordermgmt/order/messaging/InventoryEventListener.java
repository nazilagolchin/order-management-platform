package com.nazila.ordermgmt.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.events.EventEnvelope;
import com.nazila.ordermgmt.events.EventType;
import com.nazila.ordermgmt.events.InventoryReservationFailedEvent;
import com.nazila.ordermgmt.events.Topics;
import com.nazila.ordermgmt.order.service.OrderService;
import com.nazila.ordermgmt.shared.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to the saga events inventory-service publishes. Today that's only
 * the failure path: a successful reservation has no consumer yet because
 * payment-service (the next saga step) doesn't exist until Milestone 3 — see
 * {@code docs/saga-flow.md}. Any other event type on this topic is ignored
 * rather than treated as an error, since new event types will be added here
 * over time without every existing consumer needing to change.
 */
@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public InventoryEventListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = "order-service")
    public void onMessage(String message) {
        EventEnvelope envelope = readEnvelope(message);
        CorrelationContext.set(envelope.correlationId());
        try {
            if (EventType.INVENTORY_RESERVATION_FAILED.equals(envelope.eventType())) {
                InventoryReservationFailedEvent payload =
                        objectMapper.convertValue(envelope.payload(), InventoryReservationFailedEvent.class);
                orderService.handleInventoryReservationFailed(payload.orderId(), payload.reason());
            } else {
                log.debug("Ignoring event type {} on {}", envelope.eventType(), Topics.INVENTORY_EVENTS);
            }
        } finally {
            CorrelationContext.clear();
        }
    }

    private EventEnvelope readEnvelope(String message) {
        try {
            return objectMapper.readValue(message, EventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed event envelope on " + Topics.INVENTORY_EVENTS, e);
        }
    }
}
