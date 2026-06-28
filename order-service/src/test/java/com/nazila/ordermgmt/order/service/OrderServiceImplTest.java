package com.nazila.ordermgmt.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.order.domain.Order;
import com.nazila.ordermgmt.order.domain.OrderItem;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, new IdempotencyKeyHasher(new ObjectMapper()));
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
}
