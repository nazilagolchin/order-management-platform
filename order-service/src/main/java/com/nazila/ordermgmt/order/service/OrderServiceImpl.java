package com.nazila.ordermgmt.order.service;

import com.nazila.ordermgmt.order.domain.Order;
import com.nazila.ordermgmt.order.domain.OrderItem;
import com.nazila.ordermgmt.order.repository.OrderRepository;
import com.nazila.ordermgmt.order.web.dto.CreateOrderRequest;
import com.nazila.ordermgmt.order.web.dto.OrderResponse;
import com.nazila.ordermgmt.order.web.mapper.OrderMapper;
import com.nazila.ordermgmt.shared.exception.ConflictException;
import com.nazila.ordermgmt.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final IdempotencyKeyHasher idempotencyKeyHasher;

    public OrderServiceImpl(OrderRepository orderRepository, IdempotencyKeyHasher idempotencyKeyHasher) {
        this.orderRepository = orderRepository;
        this.idempotencyKeyHasher = idempotencyKeyHasher;
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

        return OrderMapper.toResponse(saved);
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
}
