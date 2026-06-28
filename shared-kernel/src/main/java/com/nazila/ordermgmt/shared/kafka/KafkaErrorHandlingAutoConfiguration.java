package com.nazila.ordermgmt.shared.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Gives every {@code @KafkaListener} in every service the same retry/DLT
 * policy by default: three retries with exponential backoff, then the
 * message is published to {@code <original-topic>.DLT} instead of being
 * dropped or blocking the partition for every event behind it. Spring Boot's
 * listener container factory autoconfiguration picks up this {@code
 * DefaultErrorHandler} bean automatically. See {@code docs/saga-flow.md}.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaErrorHandlingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxElapsedTime(10_000L); // ~3 attempts before giving up and routing to the DLT

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
