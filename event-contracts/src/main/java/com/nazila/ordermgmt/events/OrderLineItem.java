package com.nazila.ordermgmt.events;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineItem(UUID productId, int quantity, BigDecimal unitPrice) {
}
