package com.nazila.ordermgmt.order.outbox;

import com.nazila.ordermgmt.shared.outbox.OutboxEventRepository;

public interface OrderOutboxEventRepository extends OutboxEventRepository<OrderOutboxEvent> {
}
