package com.nazila.ordermgmt.order.domain;

import com.nazila.ordermgmt.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void newOrderStartsPendingWithZeroTotal() {
        Order order = Order.create(UUID.randomUUID(), "USD");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void addingItemsRecalculatesTotal() {
        Order order = Order.create(UUID.randomUUID(), "USD");

        order.addItem(OrderItem.of(UUID.randomUUID(), 2, new BigDecimal("10.00")));
        order.addItem(OrderItem.of(UUID.randomUUID(), 1, new BigDecimal("5.50")));

        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("25.50"));
        assertThat(order.getItems()).hasSize(2);
    }

    @Test
    void cancellingPendingOrderSucceeds() {
        Order order = Order.create(UUID.randomUUID(), "USD");

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancellingAlreadyCancelledOrderThrows() {
        Order order = Order.create(UUID.randomUUID(), "USD");
        order.cancel();

        assertThatThrownBy(order::cancel).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void confirmingNonPendingOrderThrows() {
        Order order = Order.create(UUID.randomUUID(), "USD");
        order.cancel();

        assertThatThrownBy(order::confirm).isInstanceOf(BusinessRuleViolationException.class);
    }
}
