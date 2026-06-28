package com.nazila.ordermgmt.events;

/** Kafka topic names. Each topic is owned by exactly one producing service. */
public final class Topics {

    /** Produced by order-service. */
    public static final String ORDER_EVENTS = "order.events";

    /** Produced by inventory-service. */
    public static final String INVENTORY_EVENTS = "inventory.events";

    private Topics() {
    }
}
