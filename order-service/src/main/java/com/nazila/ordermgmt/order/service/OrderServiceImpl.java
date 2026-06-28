package com.nazila.ordermgmt.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.events.EventEnvelope;
import com.nazila.ordermgmt.events.EventType;
import com.nazila.ordermgmt.events.OrderCreatedEvent;
import com.nazila.ordermgmt.events.OrderLineItem;
import com.nazila.ordermgmt.order.domain.Order;
import com.nazila.ordermgmt.order.domain.OrderItem;
import com.nazila.ordermgmt.order.domain.OrderStatus;
import com.nazila.ordermgmt.order.outbox.OrderOutboxEvent;
import com.nazila.ordermgmt.order.outbox.OrderOutboxEventRepository;
import com.nazila.ordermgmt.order.repository.OrderRepository;
import com.nazila.ordermgmt.order.web.dto.CreateOrderRequest;
import com.nazila.ordermgmt.order.web.dto.OrderResponse;
import com.nazila.ordermgmt.order.web.mapper.OrderMapper;
import com.nazila.ordermgmt.shared.correlation.CorrelationContext;
import com.nazila.ordermgmt.shared.exception.ConflictException;
import com.nazila.ordermgmt.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderOutboxEventRepository outboxEventRepository;
    private final IdempotencyKeyHasher idempotencyKeyHasher;
    private final ObjectMapper objectMapper;

    public OrderServiceImpl(OrderRepository orderRepository,
                             OrderOutboxEventRepository outboxEventRepository,
                             IdempotencyKeyHasher idempotencyKeyHasher,
                             ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyKeyHasher = idempotencyKeyHasher;
        this.objectMapper = objectMapper;
    }

    @Override
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            OrderResponse replay = handleIdempotentReplay(request, idempotencyKey);
            if (replay != null) {
                return replay;
            }
        }

        Order order = Order.create(request.customerId(), request.currency());
        for (var itemRequest : request.items()) {
            order.addItem(OrderItem.of(itemRequest.productId(), itemRequest.quantity(), itemRequest.unitPrice()));
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            order.markIdempotent(idempotencyKey, idempotencyKeyHasher.hash(request));
        }

        Order saved = orderRepository.save(order);
        log.info("Created order {} for customer {} with {} item(s), total {} {}",
                saved.getId(), saved.getCustomerId(), saved.getItems().size(), saved.getTotalAmount(), saved.getCurrency());

        outboxEventRepository.save(buildOrderCreatedOutboxEvent(saved));

        return OrderMapper.toResponse(saved);
    }

    /**
     * Inserted in the same transaction as the {@code Order} row (this
     * method, like the rest of the class, runs under {@code @Transactional})
     * so the event can never be lost between the commit and the publish —
     * see {@code docs/outbox-pattern.md}.
     */
    private OrderOutboxEvent buildOrderCreatedOutboxEvent(Order order) {
        List<OrderLineItem> items = order.getItems().stream()
                .map(item -> new OrderLineItem(item.getProductId(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        OrderCreatedEvent payload = new OrderCreatedEvent(
                order.getId(), order.getCustomerId(), order.getCurrency(), order.getTotalAmount(), items);

        EventEnvelope envelope = EventEnvelope.of(
                EventType.ORDER_CREATED, order.getId(), CorrelationContext.current(), payload);

        return OrderOutboxEvent.of(envelope.eventId(), order.getId(), envelope.eventType(),
                writeJson(envelope), envelope.eventVersion(), envelope.correlationId());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }

    /**
     * Returns the previously created order if {@code idempotencyKey} is a
     * safe replay of an identical request, throws {@link ConflictException}
     * if it's being reused for a different payload, or returns {@code null}
     * if the key hasn't been seen before (caller should proceed to create).
     */
    private OrderResponse handleIdempotentReplay(CreateOrderRequest request, String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    String incomingHash = idempotencyKeyHasher.hash(request);
                    if (!incomingHash.equals(existing.getIdempotencyRequestHash())) {
                        throw new ConflictException(
                                "Idempotency-Key '" + idempotencyKey + "' was already used with a different request payload.");
                    }
                    log.info("Replayed idempotent create for order {} via Idempotency-Key {}",
                            existing.getId(), idempotencyKey);
                    return OrderMapper.toResponse(existing);
                })
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + id + " was not found."));
        return OrderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(UUID customerId, Pageable pageable) {
        Page<Order> page = customerId != null
                ? orderRepository.findByCustomerId(customerId, pageable)
                : orderRepository.findAll(pageable);
        return page.map(OrderMapper::toResponse);
    }

    @Override
    public OrderResponse cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + id + " was not found."));

        order.cancel();
        Order saved = orderRepository.save(order);
        log.info("Cancelled order {}", saved.getId());
        return OrderMapper.toResponse(saved);
    }

    @Override
    public void handleInventoryReservationFailed(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " was not found."));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Ignoring InventoryReservationFailedEvent for order {} in status {} (already handled)",
                    orderId, order.getStatus());
            return;
        }

        order.cancel();
        orderRepository.save(order);
        log.info("Cancelled order {} after inventory reservation failed: {}", orderId, reason);
    }
}
