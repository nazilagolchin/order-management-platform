package com.nazila.ordermgmt.events;

import java.util.UUID;

/** Published by inventory-service when every line item on an order was reserved. */
public record InventoryReservedEvent(UUID orderId) {
}
