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
}
