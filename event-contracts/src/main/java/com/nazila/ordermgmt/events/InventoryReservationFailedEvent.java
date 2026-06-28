package com.nazila.ordermgmt.events;

import java.util.UUID;

/** Published by inventory-service when stock couldn't be reserved for an order. */
public record InventoryReservationFailedEvent(UUID orderId, String reason) {
}
