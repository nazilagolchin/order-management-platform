# Testing strategy

## Levels

| Level | What it covers | Tooling | Speed |
|---|---|---|---|
| Domain unit tests | Invariants on the aggregate itself (`Order.cancel()` twice throws, totals recompute on `addItem`) | JUnit 5, AssertJ | Milliseconds, no Spring context |
| Service unit tests | Branching logic in the service layer (idempotency replay vs. conflict, not-found, cancellation) with a mocked repository | JUnit 5, Mockito, AssertJ | Milliseconds, no Spring context |
| Integration tests | The real HTTP API against a real database, full create → get → cancel flow | `@SpringBootTest`, Testcontainers, `TestRestTemplate` | Seconds, real container |

This mirrors the test pyramid on purpose: domain and service tests are cheap enough to
run on every keystroke, so they carry the bulk of the coverage; integration tests are
fewer and targeted at the seams (HTTP serialization, the database constraint backing
idempotency, the exception-to-status-code mapping) that unit tests with mocks can't
actually verify.

## Why Testcontainers instead of H2

An in-memory database like H2 would make tests faster, but it would also let bugs through
that only show up against real PostgreSQL: this project relies on a partial unique index
(`idempotency_key IS NOT NULL`) for the idempotency contract, and on `NUMERIC(19,2)`
semantics for money — neither of which H2 emulates exactly. Testing against the real
engine via Testcontainers means a green test suite is actually evidence the code works
against what runs in production, not against a different database that happens to speak
similar SQL. The cost — slower tests, Docker required — is paid only by the integration
tests; unit tests stay fast because they never touch a container.

## What "a full order-flow integration test" means here

`OrderApiIntegrationTest` (`order-service/src/test/java/.../integration/`) drives the
actual Spring MVC dispatcher over real HTTP, against a `PostgreSQLContainer` started by
Testcontainers and wired in via `@DynamicPropertySource`. It covers:

- create → get returns the same order with the computed total
- cancel → cancel again returns `422` (not a stack trace)
- a missing order returns `404` with a populated `correlationId`
- replaying an `Idempotency-Key` with an identical payload returns the original order
  without creating a duplicate
- reusing an `Idempotency-Key` with a different payload returns `409`

## Build wiring

Unit tests run under `mvn test` via Surefire. Integration tests are named
`*IntegrationTest.java` and excluded from Surefire / included in Failsafe, so `mvn test`
stays fast and Docker-free, while `mvn verify` (used in CI) runs both. This split exists
so a contributor without Docker installed locally can still run the full unit suite —
only `mvn verify` requires Docker.

## Kafka integration testing (Milestone 2)

Each service's integration test now also exercises Kafka, using `spring-kafka-test`'s
`@EmbeddedKafka` (an in-process broker) rather than a Testcontainers Kafka container:
`order-service`'s suite asserts that creating an order is actually published to
`order.events` by the outbox relay; `inventory-service`'s suite publishes a real
`OrderCreatedEvent` and asserts stock is decremented and the correct event
(`InventoryReservedEvent` or `InventoryReservationFailedEvent`) comes out on
`inventory.events`. `@EmbeddedKafka` was chosen over a Testcontainers broker for this:
it's purpose-built for exactly this scenario, starts in-process (no Docker, no container
startup latency), and Postgres — where the real driver-specific behavior actually matters
— still goes through Testcontainers.

## Saga-level testing (Milestone 3)

Once `payment-service` exists, a platform-level integration test will spin up Postgres +
Kafka and every service's Spring context together, publish a real `OrderCreatedEvent`, and
assert the order reaches `CONFIRMED` or `CANCELLED` with the correct compensating side
effects — the automated equivalent of the [business flow](../README.md#business-flow)
diagram in the README. Today, that round trip is only verified one hop at a time (order →
inventory), per service.
