package com.nazila.ordermgmt.inventory.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.events.EventEnvelope;
import com.nazila.ordermgmt.events.EventType;
import com.nazila.ordermgmt.events.OrderCreatedEvent;
import com.nazila.ordermgmt.events.OrderLineItem;
import com.nazila.ordermgmt.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private InventoryService inventoryService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
    private OrderEventListener listener;

    @Test
    void orderCreatedEventTriggersReservation() throws Exception {
        listener = new OrderEventListener(inventoryService, objectMapper);
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent payload = new OrderCreatedEvent(orderId, UUID.randomUUID(), "USD", BigDecimal.TEN,
                List.of(new OrderLineItem(UUID.randomUUID(), 2, new BigDecimal("5.00"))));
        EventEnvelope envelope = EventEnvelope.of(EventType.ORDER_CREATED, orderId, "corr-1", payload);

        listener.onMessage(objectMapper.writeValueAsString(envelope));

        verify(inventoryService).reserveStock(payload, "corr-1");
    }

    @Test
    void unrelatedEventTypeIsIgnored() throws Exception {
        listener = new OrderEventListener(inventoryService, objectMapper);
        UUID orderId = UUID.randomUUID();
        EventEnvelope envelope = EventEnvelope.of("SomeOtherEvent", orderId, "corr-2", java.util.Map.of());

        listener.onMessage(objectMapper.writeValueAsString(envelope));

        verify(inventoryService, never()).reserveStock(any(), any());
    }

    @Test
    void malformedMessageThrowsSoItGetsRetriedAndEventuallyDeadLettered() {
        listener = new OrderEventListener(inventoryService, objectMapper);

        assertThatThrownBy(() -> listener.onMessage("not valid json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
