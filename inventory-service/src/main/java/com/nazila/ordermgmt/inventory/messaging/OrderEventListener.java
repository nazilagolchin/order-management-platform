package com.nazila.ordermgmt.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.events.EventEnvelope;
import com.nazila.ordermgmt.events.EventType;
import com.nazila.ordermgmt.events.OrderCreatedEvent;
import com.nazila.ordermgmt.events.Topics;
import com.nazila.ordermgmt.inventory.service.InventoryService;
import com.nazila.ordermgmt.shared.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public OrderEventListener(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "inventory-service")
    public void onMessage(String message) {
        EventEnvelope envelope = readEnvelope(message);
        CorrelationContext.set(envelope.correlationId());
        try {
            if (EventType.ORDER_CREATED.equals(envelope.eventType())) {
                OrderCreatedEvent payload = objectMapper.convertValue(envelope.payload(), OrderCreatedEvent.class);
                inventoryService.reserveStock(payload, envelope.correlationId());
            } else {
                log.debug("Ignoring event type {} on {}", envelope.eventType(), Topics.ORDER_EVENTS);
            }
        } finally {
            CorrelationContext.clear();
        }
    }

    private EventEnvelope readEnvelope(String message) {
        try {
            return objectMapper.readValue(message, EventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed event envelope on " + Topics.ORDER_EVENTS, e);
        }
    }
}
