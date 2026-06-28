# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Java/Spring Boot **monorepo** for an event-driven order management platform. It's structured as
multiple independently-buildable services that will communicate over Kafka, but **only
`order-service` and `shared-kernel` are implemented today** (Milestone 1). `inventory-service`,
`payment-service`, and `notification-service` exist only as placeholder directories with a README
describing what they will do — there is no source code in them yet. Don't assume code exists in
those modules; check before referencing files there.

The project intentionally builds production-grade distributed-systems patterns (outbox, sagas,
idempotency, optimistic locking) rather than a plain CRUD API — see the README's "Why this project
exists" section. When making changes, preserve these patterns rather than simplifying them away.

## Commands

All commands run from the repo root (Maven multi-module reactor).

```bash
# Build everything
mvn -B -ntp install

# Unit tests only (fast, no Docker) — what `mvn test` runs in CI parity
mvn test

# Unit + Testcontainers integration tests (requires Docker running) — what CI runs
mvn verify

# Run a single test class
mvn -pl order-service test -Dtest=OrderServiceImplTest
mvn -pl order-service verify -Dit.test=OrderApiIntegrationTest

# Run order-service only, against Postgres started separately
docker compose up postgres
mvn -pl order-service -am spring-boot:run

# Full stack via Docker (Postgres, Redis, Redpanda, order-service)
docker compose up --build
# order-service: http://localhost:8081, Swagger UI: /swagger-ui.html, health: /actuator/health
```

Test naming convention enforced by the Surefire/Failsafe config in `order-service/pom.xml`:
classes named `*IntegrationTest.java` (or `*IT.java`) are excluded from `mvn test`
(Surefire) and only run under `mvn verify` (Failsafe), because they require Docker via
Testcontainers. Plain unit tests have no Spring context and run in milliseconds.

CI (`.github/workflows/ci.yml`) runs a single job: `mvn -B -ntp verify` on JDK 21.

## Architecture

### Module boundaries

- **`shared-kernel`** — a *library*, not a service. Auto-configures (via
  `SharedKernelAutoConfiguration`, Spring Boot's `@AutoConfiguration` mechanism — no
  component-scan wiring needed in consumers) two cross-cutting concerns for every service that
  depends on it: a `CorrelationIdFilter` (stamps/propagates `X-Correlation-Id`, backed by MDC)
  and a `GlobalApiExceptionHandler` (`@RestControllerAdvice`) that maps the shared exception
  types (`ResourceNotFoundException`, `ConflictException`, `BusinessRuleViolationException`) to a
  single `ApiError` response contract. It deliberately holds **no domain entities or business
  rules** — if a change would require putting a domain concept here, that's a sign two services
  have an undeclared coupling, not a shared-kernel gap.
- **`order-service`** — owns the `Order`/`OrderItem` aggregate end-to-end: REST API, JPA
  persistence (Postgres + Flyway), idempotent order creation, optimistic locking, OpenAPI docs.
  This is the only service with real business logic right now.
- **`inventory-service`, `payment-service`, `notification-service`** — empty placeholders
  (README only). Per the roadmap: inventory reserves stock (Milestone 2), payment simulates
  charging (Milestone 3), notification reacts to terminal saga events (Milestone 3).

Each service owns exactly one database/schema; no service queries another's tables directly. The
only inter-service contract is events (Kafka, via the outbox pattern) and the API each exposes —
see `docs/architecture.md`.

### Request flow inside order-service

```
HTTP request → CorrelationIdFilter (shared-kernel)
             → OrderController (web/) — validation, DTO mapping
             → OrderServiceImpl (service/) — @Transactional boundary, business rules
             → OrderRepository (Spring Data JPA, repository/)
             → GlobalApiExceptionHandler (shared-kernel) — error path only
```

`Order` (domain/) is the aggregate root: it owns `OrderItem`s, computes totals, and enforces
invariants in code (e.g. `cancel()` on an already-cancelled order throws rather than no-op'ing).
Keep business rules in the domain model, not the service layer or controller.

### Key production patterns already implemented (don't regress these)

- **Idempotency keys**: `POST /api/orders` takes an `Idempotency-Key` header.
  `OrderServiceImpl.handleIdempotentReplay` + `IdempotencyKeyHasher` + a partial unique index on
  `idempotency_key` (Flyway migration, requires real Postgres — H2 won't enforce it) implement
  replay-returns-original / different-payload-returns-409 semantics.
- **Optimistic locking**: `Order.version` is a JPA `@Version` column; a losing concurrent update
  surfaces as `409`, not a silent overwrite.
- **Centralized errors**: every error response is an `ApiError` (shared-kernel), with a
  `correlationId` populated from the request's `X-Correlation-Id`.

### Patterns that are designed but NOT yet implemented (Milestone 2+)

These are documented in detail in `docs/outbox-pattern.md` and `docs/saga-flow.md` — read them
before implementing, since the design (table shape, relay behavior, choreography vs.
orchestration rationale) is already decided:

- The outbox table + relay for publishing events transactionally with the business write.
- Kafka/Redpanda event publishing and consumption, including the choreography-based saga
  (`order-service` ↔ `inventory-service` ↔ `payment-service` ↔ `notification-service`).
- Retry-with-backoff and Dead Letter Topic (`<topic>.DLT`) handling for consumers.
- Event envelope versioning (a `version` field on every event, from day one).

If asked to start Milestone 2 work, follow the relay/event shape already specified in those docs
rather than inventing a new design.

## Testing strategy

Three levels, see `docs/testing-strategy.md` for full rationale:

1. **Domain unit tests** (`order/domain/OrderTest.java`) — aggregate invariants, no Spring context.
2. **Service unit tests** (`order/service/OrderServiceImplTest.java`) — branching logic with a
   mocked `OrderRepository`.
3. **Integration tests** (`order/integration/OrderApiIntegrationTest.java`) — full HTTP API against
   a real `PostgreSQLContainer` via Testcontainers, wired with `@DynamicPropertySource`. Real
   Postgres is used deliberately (not H2) because the idempotency partial-unique-index and
   `NUMERIC(19,2)` money semantics aren't faithfully emulated by H2.

`hibernate.ddl-auto` is `validate`, never `update` — schema changes go through Flyway migrations
in `order-service/src/main/resources/db/migration/`, not entity annotations.
