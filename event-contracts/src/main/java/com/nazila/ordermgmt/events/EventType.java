package com.nazila.ordermgmt.events;

/**
 * String discriminators carried in {@link EventEnvelope#eventType()}. Plain
 * constants rather than an enum: producers and consumers are deployed
 * independently, and a new event type shouldn't require every service to
 * recompile against a shared enum.
 */
public final class EventType {

    public static final String ORDER_CREATED = "OrderCreatedEvent";
    public static final String INVENTORY_RESERVED = "InventoryReservedEvent";
    public static final String INVENTORY_RESERVATION_FAILED = "InventoryReservationFailedEvent";

    private EventType() {
    }
}
