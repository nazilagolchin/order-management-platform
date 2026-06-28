package com.nazila.ordermgmt.order.domain;

/**
 * Lifecycle of an order. {@code CONFIRMED} and {@code CANCELLED} are
 * currently reached only via direct API calls; from Milestone 2 onward they
 * become the terminal states of the order saga driven by inventory and
 * payment events (see {@code docs/saga-flow.md}).
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
