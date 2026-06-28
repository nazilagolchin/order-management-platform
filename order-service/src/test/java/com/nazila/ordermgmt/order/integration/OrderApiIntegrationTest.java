package com.nazila.ordermgmt.order.integration;

import com.nazila.ordermgmt.order.web.dto.CreateOrderRequest;
import com.nazila.ordermgmt.order.web.dto.OrderItemRequest;
import com.nazila.ordermgmt.order.web.dto.OrderResponse;
import com.nazila.ordermgmt.shared.error.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the order-service HTTP API against a real PostgreSQL instance,
 * exercising the full create -&gt; get -&gt; cancel flow plus the
 * idempotency-key contract described in the README.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OrderApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_management")
            .withUsername("order_service")
            .withPassword("order_service");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private CreateOrderRequest sampleRequest() {
        return new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(
                        new OrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("19.99")),
                        new OrderItemRequest(UUID.randomUUID(), 1, new BigDecimal("5.00"))));
    }

    @Test
    void createThenGetReturnsTheSameOrder() {
        ResponseEntity<OrderResponse> createResponse =
                restTemplate.postForEntity(url("/api/orders"), sampleRequest(), OrderResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getHeaders().getLocation()).isNotNull();
        OrderResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.totalAmount()).isEqualByComparingTo(new BigDecimal("44.98"));

        ResponseEntity<OrderResponse> getResponse =
                restTemplate.getForEntity(url("/api/orders/" + created.id()), OrderResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().id()).isEqualTo(created.id());
        assertThat(getResponse.getBody().items()).hasSize(2);
    }

    @Test
    void cancellingAnOrderTwiceReturnsUnprocessableEntityOnSecondAttempt() {
        OrderResponse created = restTemplate.postForEntity(url("/api/orders"), sampleRequest(), OrderResponse.class)
                .getBody();
        assertThat(created).isNotNull();

        ResponseEntity<OrderResponse> firstCancel =
                restTemplate.postForEntity(url("/api/orders/" + created.id() + "/cancel"), null, OrderResponse.class);
        assertThat(firstCancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstCancel.getBody().status()).isEqualTo("CANCELLED");

        ResponseEntity<ApiError> secondCancel =
                restTemplate.postForEntity(url("/api/orders/" + created.id() + "/cancel"), null, ApiError.class);
        assertThat(secondCancel.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void gettingAMissingOrderReturnsNotFoundWithCorrelationId() {
        ResponseEntity<ApiError> response =
                restTemplate.getForEntity(url("/api/orders/" + UUID.randomUUID()), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().correlationId()).isNotBlank();
    }

    @Test
    void replayingIdempotencyKeyReturnsOriginalOrderInsteadOfCreatingANewOne() {
        CreateOrderRequest request = sampleRequest();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Idempotency-Key", "test-key-" + UUID.randomUUID());
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<OrderResponse> first =
                restTemplate.postForEntity(url("/api/orders"), entity, OrderResponse.class);
        ResponseEntity<OrderResponse> second =
                restTemplate.postForEntity(url("/api/orders"), entity, OrderResponse.class);

        assertThat(first.getBody().id()).isEqualTo(second.getBody().id());
    }

    @Test
    void reusingIdempotencyKeyWithDifferentPayloadReturnsConflict() {
        HttpHeaders headers = new HttpHeaders();
        String key = "test-key-" + UUID.randomUUID();
        headers.add("Idempotency-Key", key);

        restTemplate.postForEntity(url("/api/orders"), new HttpEntity<>(sampleRequest(), headers), OrderResponse.class);

        ResponseEntity<ApiError> conflict = restTemplate.postForEntity(
                url("/api/orders"), new HttpEntity<>(sampleRequest(), headers), ApiError.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
