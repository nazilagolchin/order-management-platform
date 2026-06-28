package com.nazila.ordermgmt.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.events.EventEnvelope;
import com.nazila.ordermgmt.events.EventType;
import com.nazila.ordermgmt.events.InventoryReservationFailedEvent;
import com.nazila.ordermgmt.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryEventListenerTest {

    @Mock
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private InventoryEventListener listener;

    @Test
    void reservationFailedEventCancelsTheOrder() throws Exception {
        listener = new InventoryEventListener(orderService, objectMapper);
        UUID orderId = UUID.randomUUID();
        EventEnvelope envelope = EventEnvelope.of(EventType.INVENTORY_RESERVATION_FAILED, orderId, "corr-1",
                new InventoryReservationFailedEvent(orderId, "out of stock"));

        listener.onMessage(objectMapper.writeValueAsString(envelope));

        verify(orderService).handleInventoryReservationFailed(orderId, "out of stock");
    }

    @Test
    void unrelatedEventTypeIsIgnored() throws Exception {
        listener = new InventoryEventListener(orderService, objectMapper);
        UUID orderId = UUID.randomUUID();
        EventEnvelope envelope = EventEnvelope.of("InventoryReservedEvent", orderId, "corr-2",
                new com.nazila.ordermgmt.events.InventoryReservedEvent(orderId));

        listener.onMessage(objectMapper.writeValueAsString(envelope));

        verify(orderService, never()).handleInventoryReservationFailed(orderId, null);
    }

    @Test
    void malformedMessageThrowsSoItGetsRetriedAndEventuallyDeadLettered() {
        listener = new InventoryEventListener(orderService, objectMapper);

        assertThatThrownBy(() -> listener.onMessage("not valid json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
