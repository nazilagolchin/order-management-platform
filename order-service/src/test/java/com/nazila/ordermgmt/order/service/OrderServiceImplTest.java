package com.nazila.ordermgmt.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.events.EventType;
import com.nazila.ordermgmt.order.domain.Order;
import com.nazila.ordermgmt.order.domain.OrderItem;
import com.nazila.ordermgmt.order.domain.OrderStatus;
import com.nazila.ordermgmt.order.outbox.OrderOutboxEvent;
import com.nazila.ordermgmt.order.outbox.OrderOutboxEventRepository;
import com.nazila.ordermgmt.order.repository.OrderRepository;
import com.nazila.ordermgmt.order.web.dto.CreateOrderRequest;
import com.nazila.ordermgmt.order.web.dto.OrderItemRequest;
import com.nazila.ordermgmt.order.web.dto.OrderResponse;
import com.nazila.ordermgmt.shared.exception.ConflictException;
import com.nazila.ordermgmt.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOutboxEventRepository outboxEventRepository;

    private OrderServiceImpl orderService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderRepository, outboxEventRepository, new IdempotencyKeyHasher(objectMapper), objectMapper);
    }

    private CreateOrderRequest sampleRequest() {
        return new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("10.00"))));
    }

    @Test
    void createOrderPersistsOrderWithComputedTotal() {
        CreateOrderRequest request = sampleRequest();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request, null);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void createOrderWritesAnOrderCreatedOutboxEventInTheSameCall() {
        CreateOrderRequest request = sampleRequest();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<OrderOutboxEvent> captor = ArgumentCaptor.forClass(OrderOutboxEvent.class);
        when(outboxEventRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request, null);

        OrderOutboxEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(EventType.ORDER_CREATED);
        assertThat(event.getAggregateId()).isEqualTo(response.id());
        assertThat(event.getPayload()).contains(response.id().toString());
    }

    @Test
    void createOrderWithoutIdempotencyKeyNeverLooksUpExisting() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.createOrder(sampleRequest(), null);

        verify(orderRepository, org.mockito.Mockito.never()).findByIdempotencyKey(any());
    }

    @Test
    void replayingSameIdempotencyKeyWithSamePayloadReturnsExistingOrderWithoutSaving() {
        CreateOrderRequest request = sampleRequest();
        IdempotencyKeyHasher hasher = new IdempotencyKeyHasher(new ObjectMapper());
        String key = "retry-key-1";

        Order existing = Order.create(request.customerId(), request.currency());
        existing.addItem(OrderItem.of(request.items().get(0).productId(), request.items().get(0).quantity(),
                request.items().get(0).unitPrice()));
        existing.markIdempotent(key, hasher.hash(request));

        when(orderRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        OrderResponse response = orderService.createOrder(request, key);

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(orderRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void reusingIdempotencyKeyWithDifferentPayloadThrowsConflict() {
        String key = "retry-key-2";
        Order existing = Order.create(UUID.randomUUID(), "USD");
        existing.markIdempotent(key, "some-other-hash");

        when(orderRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.createOrder(sampleRequest(), key))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getOrderThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelOrderTransitionsToCancelledAndSaves() {
        Order order = Order.create(UUID.randomUUID(), "USD");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(order.getId());

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(captor.getValue().getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    void handleInventoryReservationFailedCancelsAPendingOrder() {
        Order order = Order.create(UUID.randomUUID(), "USD");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.handleInventoryReservationFailed(order.getId(), "out of stock");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void handleInventoryReservationFailedIsANoOpForAnAlreadyCancelledOrder() {
        Order order = Order.create(UUID.randomUUID(), "USD");
        order.cancel();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.handleInventoryReservationFailed(order.getId(), "out of stock");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleInventoryReservationFailedThrowsWhenOrderIsMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.handleInventoryReservationFailed(id, "out of stock"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
