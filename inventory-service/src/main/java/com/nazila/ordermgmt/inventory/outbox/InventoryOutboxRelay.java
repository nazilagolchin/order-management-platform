package com.nazila.ordermgmt.inventory.outbox;

import com.nazila.ordermgmt.events.Topics;
import com.nazila.ordermgmt.shared.outbox.OutboxRelay;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InventoryOutboxRelay extends OutboxRelay<InventoryOutboxEvent> {

    public InventoryOutboxRelay(InventoryOutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        super(repository, kafkaTemplate);
    }

    @Override
    protected String topicFor(String eventType) {
        return Topics.INVENTORY_EVENTS;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
    public void poll() {
        relay();
    }
}
