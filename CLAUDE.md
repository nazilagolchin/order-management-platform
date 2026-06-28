# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Java/Spring Boot **monorepo** for an event-driven order management platform, communicating
over Kafka. As of Milestone 2, `event-contracts`, `shared-kernel`, `order-service`, and
`inventory-service` are all implemented with real code. `payment-service` and
`notification-service` still exist only as placeholder directories with a README describing
what they will do (Milestone 3) — there is no source code in them yet. Don't assume code
exists in those two modules; check before referencing files there.

The project intentionally builds production-grade distributed-systems patterns (outbox, sagas,
idempotency, optimistic locking, retry/DLT) rather than a plain CRUD API — see the README's "Why
this project exists" section. When making changes, preserve these patterns rather than
simplifying them away.

## Commands

All commands run from the repo root (Maven multi-module reactor: `event-contracts` →
`shared-kernel` → `order-service` / `inventory-service`).

```bash
# Build everything
mvn -B -ntp install

# Unit tests only (fast, no Docker) — what `mvn test` runs in CI parity
mvn test

# Unit + integration tests (Testcontainers Postgres + embedded Kafka; requires Docker for
# Postgres only — Kafka in tests is in-process, no broker container needed) — what CI runs
mvn verify

# Run a single test class
mvn -pl order-service test -Dtest=OrderServiceImplTest
mvn -pl inventory-service verify -Dit.test=InventoryReservationIntegrationTest

# Run one service against its own Postgres + Redpanda started separately
docker compose up postgres redpanda
mvn -pl order-service -am spring-boot:run
docker compose up inventory-postgres redpanda
mvn -pl inventory-service -am spring-boot:run

# Full stack via Docker (2x Postgres, Redis, Redpanda, order-service, inventory-service)
docker compose up --build
# order-service:     http://localhost:8081/swagger-ui.html
# inventory-service: http://localhost:8082/swagger-ui.html
```

Test naming convention enforced by the Surefire/Failsafe config in each service's `pom.xml`:
classes named `*IntegrationTest.java` (or `*IT.java`) are excluded from `mvn test`
(Surefire) and only run under `mvn verify` (Failsafe). Plain unit tests have no Spring context
and run in milliseconds; integration tests boot a full Spring context with a real
`PostgreSQLContainer` (Testcontainers) and an `@EmbeddedKafka` broker (in-process, no Docker
needed for Kafka itself — only Postgres needs Docker).

CI (`.github/workflows/ci.yml`) runs a single job: `mvn -B -ntp verify` on JDK 21.

## Architecture

### Module boundaries

- **`event-contracts`** — plain Java, zero framework dependencies. The Kafka wire format:
  `EventEnvelope` (id, eventType, aggregateId, eventVersion, occurredAt, correlationId,
  payload), the event payload records (`OrderCreatedEvent`, `InventoryReservedEvent`,
  `InventoryReservationFailedEvent`), and `Topics`/`EventType` constants. Both producer and
  consumer services depend on this so they agree on a schema without depending on each other.
- **`shared-kernel`** — a *library*, not a service. Auto-configures (via `@AutoConfiguration`
  classes registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — no component-scan wiring needed in consumers) cross-cutting concerns every service gets for
  free:
  - `SharedKernelAutoConfiguration` — `CorrelationIdFilter` (stamps/propagates
    `X-Correlation-Id` via MDC) and `GlobalApiExceptionHandler` (`@RestControllerAdvice`
    mapping `ResourceNotFoundException`/`ConflictException`/`BusinessRuleViolationException`
    to one `ApiError` contract).
  - `JacksonMoneyAutoConfiguration` — enables `USE_BIG_DECIMAL_FOR_FLOATS` so a `BigDecimal`
    round-tripped through `EventEnvelope.payload()` (typed `Object`, then converted to a
    concrete event with `ObjectMapper#convertValue`) doesn't lose scale (`5.00` → `5.0` is a
    real bug, not cosmetic — see the commit history / `InventoryServiceImplTest` if this
    regresses).
  - `KafkaErrorHandlingAutoConfiguration` — a `DefaultErrorHandler` bean (exponential backoff,
    then `DeadLetterPublishingRecoverer` to `<topic>.DLT`) that Spring Boot's Kafka
    autoconfiguration applies to every `@KafkaListener` container automatically.
  - `outbox` package — `OutboxEvent` (`@MappedSuperclass`; each service adds a thin `@Entity`
    subclass for its own `outbox_events` table), `OutboxEventRepository<T>` (`@NoRepositoryBean`
    generic base), and `OutboxRelay<T>` (the poll-and-publish loop). Each service supplies its
    own repository, entity, and a one-line `topicFor(eventType)` override — see
    `OrderOutboxRelay` / `InventoryOutboxRelay`.

  It deliberately holds **no domain entities or business rules** — if a change would require
  putting a domain concept here, that's a sign two services have an undeclared coupling, not a
  shared-kernel gap. The outbox/Kafka infra above is cross-cutting plumbing, not domain logic,
  which is why it lives here instead of being duplicated per service.
- **`order-service`** — owns the `Order`/`OrderItem` aggregate: REST API, JPA persistence
  (Postgres + Flyway), idempotent order creation, optimistic locking, OpenAPI docs, an outbox
  row written in the same transaction as order creation, and a `InventoryEventListener` that
  reacts to `InventoryReservationFailedEvent` by cancelling the order (idempotent: a
  redelivered event for an already-`CANCELLED` order is a no-op, not a conflict).
- **`inventory-service`** — owns `Inventory` (stock per product, pessimistic-locked on
  reservation — see the class javadoc on `Inventory` for why pessimistic over optimistic here)
  and `StockReservation` (the idempotency record keyed by `orderId`, so a redelivered
  `OrderCreatedEvent` doesn't double-reserve). Consumes `order.events`, reserves stock
  all-or-nothing across every line item, and publishes the outcome through its own outbox.
  Exposes `GET /api/inventory/{productId}` for inspection only — that endpoint is not part of
  the saga.
- **`payment-service`, `notification-service`** — empty placeholders (README only), Milestone 3.

Each service owns exactly one database/schema — in Docker Compose, **a separate Postgres
container each** (`postgres` for order-service, `inventory-postgres` for inventory-service; see
`docs/architecture.md` for why a separate container rather than just a separate database in the
same instance). No service queries another's tables directly; the only inter-service contract is
events (Kafka, via the outbox pattern, using `event-contracts`) and the API each exposes.

### Request flow inside order-service

```
HTTP request → CorrelationIdFilter (shared-kernel)
             → OrderController (web/) — validation, DTO mapping
             → OrderServiceImpl (service/) — @Transactional boundary, business rules,
                                              writes the OrderCreatedEvent outbox row
             → OrderRepository (Spring Data JPA, repository/)
             → GlobalApiExceptionHandler (shared-kernel) — error path only
```

`OrderOutboxRelay` (outbox/) is a separate `@Scheduled` poller, not part of the request path —
it picks up unpublished outbox rows and publishes them to `order.events`.

`InventoryEventListener` (messaging/) is the inbound side: a `@KafkaListener` on
`inventory.events` that calls back into `OrderServiceImpl.handleInventoryReservationFailed`.

`Order` (domain/) is the aggregate root: it owns `OrderItem`s, computes totals, and enforces
invariants in code (e.g. `cancel()` on an already-cancelled order throws rather than no-op'ing).
Keep business rules in the domain model, not the service layer or controller.

### Request flow inside inventory-service

```
order.events (Kafka) → OrderEventListener (messaging/) — deserializes EventEnvelope,
                                                            restores correlationId into MDC
                      → InventoryServiceImpl (service/) — @Transactional: idempotency check
                                                            (StockReservation by orderId),
                                                            pessimistic-locked reserve, writes
                                                            the outcome outbox row
                      → InventoryRepository / StockReservationRepository (Spring Data JPA)
```

`InventoryOutboxRelay` (outbox/) independently polls and publishes to `inventory.events`, same
pattern as `order-service`.

### Key production patterns implemented (don't regress these)

- **Idempotency keys** (HTTP): `POST /api/orders` takes an `Idempotency-Key` header.
  `OrderServiceImpl.handleIdempotentReplay` + `IdempotencyKeyHasher` + a partial unique index on
  `idempotency_key` (Flyway migration, requires real Postgres — H2 won't enforce it) implement
  replay-returns-original / different-payload-returns-409 semantics.
- **Idempotent consumers** (Kafka): delivery is at least once. `inventory-service` records a
  `StockReservation` keyed by `orderId` *before* deciding to reserve, and checks for an existing
  one first — a redelivered `OrderCreatedEvent` is recognized and skipped. `order-service`'s
  `handleInventoryReservationFailed` is a no-op if the order isn't still `PENDING`.
- **Optimistic locking**: `Order.version` is a JPA `@Version` column; a losing concurrent update
  surfaces as `409`, not a silent overwrite. (`Inventory` also carries `@Version` as
  defense-in-depth, but reservation itself uses a pessimistic row lock — see its javadoc.)
- **Outbox pattern**: every state-changing write that needs to notify another service inserts an
  outbox row in the *same* transaction; a separate `@Scheduled` relay publishes it. Never publish
  to Kafka directly from a request-handling or event-listener transaction.
- **Retry + DLT**: automatic for every `@KafkaListener`, via shared-kernel — don't add
  per-listener try/catch-and-give-up logic; let `DefaultErrorHandler` retry and dead-letter it.
- **Centralized errors**: every error response is an `ApiError` (shared-kernel), with a
  `correlationId` populated from the request's `X-Correlation-Id`.

### Patterns that are designed but NOT yet implemented (Milestone 3)

Documented in `docs/saga-flow.md`:

- `payment-service` consuming `InventoryReservedEvent`, simulating a charge, publishing
  `PaymentCompletedEvent`/`PaymentFailedEvent`.
- `order-service` reacting to those terminal payment events (`Order.confirm()` already exists
  in the domain model for this, unused until then).
- `notification-service` consuming terminal events.
- A platform-level integration test spinning up every service's Spring context together.

If asked to start Milestone 3 work, follow the event shape already specified in
`docs/saga-flow.md`, and mirror the `order-service`/`inventory-service` pattern (own outbox
table + relay, own idempotency record, listener delegates to a service method) rather than
inventing a new one.

## Testing strategy

Three levels per service, see `docs/testing-strategy.md` for full rationale:

1. **Domain unit tests** (e.g. `order/domain/OrderTest.java`, `inventory/domain/InventoryTest.java`)
   — aggregate invariants, no Spring context.
2. **Service unit tests** (e.g. `OrderServiceImplTest`, `InventoryServiceImplTest`) — branching
   logic with mocked repositories. Also covers the `@KafkaListener` classes themselves
   (`InventoryEventListenerTest`, `OrderEventListenerTest`) with a mocked downstream service —
   these assert routing/ignoring by event type and that a malformed message throws (so it gets
   retried and eventually dead-lettered), not the Kafka transport itself.
3. **Integration tests** (`*IntegrationTest.java`) — full HTTP API + Kafka against a real
   `PostgreSQLContainer` (Testcontainers) and an `@EmbeddedKafka` broker. Real Postgres is used
   deliberately (not H2) because the idempotency partial-unique-index and `NUMERIC(19,2)` money
   semantics aren't faithfully emulated by H2. `@EmbeddedKafka` (not a Testcontainers Kafka
   container) because it's in-process and fast — no broker container needed for this kind of test.

`hibernate.ddl-auto` is `validate`, never `update`, in every service — schema changes go through
Flyway migrations in `<service>/src/main/resources/db/migration/`, not entity annotations.

A test (or production) `ObjectMapper` built manually with `new ObjectMapper()` does **not** get
`shared-kernel`'s `JacksonMoneyAutoConfiguration` or JSR-310 module — that's only wired in by
Spring Boot's autoconfiguration in a real application context. Tests that build their own
`ObjectMapper` need `.findAndRegisterModules()` (for `Instant`) and, if they round-trip a
`BigDecimal` through a generic `Object` field, `.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)` too.
