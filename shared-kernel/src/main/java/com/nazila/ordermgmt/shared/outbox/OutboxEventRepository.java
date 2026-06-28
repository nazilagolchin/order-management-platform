package com.nazila.ordermgmt.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.UUID;

/**
 * Each service declares a concrete repository extending this for its own
 * {@link OutboxEvent} subclass, e.g. {@code interface OrderOutboxEventRepository
 * extends OutboxEventRepository<OrderOutboxEvent> {}} — Spring Data JPA
 * implements it like any other repository interface.
 */
@NoRepositoryBean
public interface OutboxEventRepository<T extends OutboxEvent> extends JpaRepository<T, UUID> {

    List<T> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
