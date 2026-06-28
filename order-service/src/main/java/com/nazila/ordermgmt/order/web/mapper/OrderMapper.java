package com.nazila.ordermgmt.order.web.mapper;

import com.nazila.ordermgmt.order.domain.Order;
import com.nazila.ordermgmt.order.domain.OrderItem;
import com.nazila.ordermgmt.order.web.dto.OrderItemResponse;
import com.nazila.ordermgmt.order.web.dto.OrderResponse;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion(),
                order.getItems().stream().map(OrderMapper::toResponse).toList()
        );
    }

    public static OrderItemResponse toResponse(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getQuantity(), item.getUnitPrice());
    }
}
