package com.nazila.ordermgmt.order.service;

import com.nazila.ordermgmt.order.web.dto.CreateOrderRequest;
import com.nazila.ordermgmt.order.web.dto.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey);

    OrderResponse getOrder(UUID id);

    Page<OrderResponse> listOrders(UUID customerId, Pageable pageable);

    OrderResponse cancelOrder(UUID id);

    /**
     * Reacts to an {@code InventoryReservationFailedEvent} from the saga:
     * cancels the order if it's still {@code PENDING}. Must be safe to call
     * more than once with the same {@code orderId} (Kafka delivery is at
     * least once), so an order that's already {@code CANCELLED} is a no-op
     * rather than a conflict.
     */
    void handleInventoryReservationFailed(UUID orderId, String reason);
}
