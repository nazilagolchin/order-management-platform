package com.nazila.ordermgmt.inventory.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazila.ordermgmt.events.EventEnvelope;
import com.nazila.ordermgmt.events.EventType;
import com.nazila.ordermgmt.events.InventoryReservedEvent;
import com.nazila.ordermgmt.events.OrderCreatedEvent;
import com.nazila.ordermgmt.events.OrderLineItem;
import com.nazila.ordermgmt.events.Topics;
import com.nazila.ordermgmt.inventory.web.dto.InventoryResponse;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the headline Milestone 2 promise end-to-end: a real {@code
 * OrderCreatedEvent} on {@code order.events} gets consumed, stock is
 * decremented in a real PostgreSQL database, and {@code InventoryReservedEvent}
 * comes out the other side on {@code inventory.events} via this service's own
 * outbox relay. Kafka is an embedded in-process broker (no Docker, fast);
 * Postgres is a real Testcontainers instance, consistent with
 * {@code docs/testing-strategy.md}.
 */
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {Topics.ORDER_EVENTS, Topics.INVENTORY_EVENTS})
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class InventoryReservationIntegrationTest {

    private static final UUID SEEDED_PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventory_management")
            .withUsername("inventory_service")
            .withPassword("inventory_service");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty(EmbeddedKafkaBroker.SPRING_EMBEDDED_KAFKA_BROKERS));
        registry.add("outbox.relay.poll-interval-ms", () -> "200");
    }

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Producer<String, String> producer;
    private Consumer<String, String> inventoryEventsConsumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producer = new DefaultKafkaProducerFactory<String, String>(producerProps).createProducer();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-consumer", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        inventoryEventsConsumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(inventoryEventsConsumer, Topics.INVENTORY_EVENTS);
    }

    @AfterEach
    void tearDown() {
        producer.close();
        inventoryEventsConsumer.close();
    }

    @Test
    void reservingStockForASeededProductDecrementsItAndPublishesReservedEvent() throws Exception {
        int before = getAvailableQuantity(SEEDED_PRODUCT_ID);
        UUID orderId = UUID.randomUUID();

        publishOrderCreated(orderId, SEEDED_PRODUCT_ID, 3);

        ConsumerRecord<String, String> record = awaitRecordForOrder(orderId);
        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
        assertThat(envelope.eventType()).isEqualTo(EventType.INVENTORY_RESERVED);
        InventoryReservedEvent payload = objectMapper.convertValue(envelope.payload(), InventoryReservedEvent.class);
        assertThat(payload.orderId()).isEqualTo(orderId);

        assertThat(getAvailableQuantity(SEEDED_PRODUCT_ID)).isEqualTo(before - 3);
    }

    @Test
    void reservingMoreThanAvailableLeavesStockUnchangedAndPublishesFailedEvent() throws Exception {
        int before = getAvailableQuantity(SEEDED_PRODUCT_ID);
        UUID orderId = UUID.randomUUID();

        publishOrderCreated(orderId, SEEDED_PRODUCT_ID, before + 1000);

        ConsumerRecord<String, String> record = awaitRecordForOrder(orderId);
        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
        assertThat(envelope.eventType()).isEqualTo(EventType.INVENTORY_RESERVATION_FAILED);

        assertThat(getAvailableQuantity(SEEDED_PRODUCT_ID)).isEqualTo(before);
    }

    private void publishOrderCreated(UUID orderId, UUID productId, int quantity) {
        OrderCreatedEvent payload = new OrderCreatedEvent(orderId, UUID.randomUUID(), "USD", BigDecimal.TEN,
                List.of(new OrderLineItem(productId, quantity, new BigDecimal("10.00"))));
        EventEnvelope envelope = EventEnvelope.of(EventType.ORDER_CREATED, orderId, "test-correlation", payload);
        producer.send(new ProducerRecord<>(Topics.ORDER_EVENTS, orderId.toString(), toJson(envelope)));
        producer.flush();
    }

    private int getAvailableQuantity(UUID productId) {
        InventoryResponse response = restTemplate.getForObject(
                "http://localhost:" + port + "/api/inventory/" + productId, InventoryResponse.class);
        return response.availableQuantity();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ConsumerRecord<String, String> awaitRecordForOrder(UUID orderId) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = inventoryEventsConsumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.key().equals(orderId.toString())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No event published for order " + orderId + " within the timeout");
    }
}
