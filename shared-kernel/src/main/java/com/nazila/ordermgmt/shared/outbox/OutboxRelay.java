package com.nazila.ordermgmt.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls a service's {@code outbox_events} table for unpublished rows and
 * publishes each one to Kafka, keyed by aggregate id so every event for a
 * given aggregate stays ordered within its partition. A row that fails to
 * publish (broker unreachable, timeout) is simply left unpublished — it's
 * retried on the next poll, which is what makes this "at least once" instead
 * of "best effort". See {@code docs/outbox-pattern.md}.
 *
 * <p>Subclasses only need to supply the repository for their own
 * {@link OutboxEvent} subclass and a topic per event type; call {@link
 * #relay()} from a {@code @Scheduled} method (kept on the subclass, not
 * here, so each service controls its own poll interval via configuration).
 */
public abstract class OutboxRelay<T extends OutboxEvent> {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository<T> repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    protected OutboxRelay(OutboxEventRepository<T> repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    protected abstract String topicFor(String eventType);

    @Transactional
    public void relay() {
        List<T> batch = repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (T event : batch) {
            try {
                kafkaTemplate.send(topicFor(event.getEventType()), event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                log.debug("Published outbox event {} ({}) for aggregate {}",
                        event.getId(), event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.warn("Failed to publish outbox event {} ({}); will retry on next poll",
                        event.getId(), event.getEventType(), e);
            }
        }
    }
}
