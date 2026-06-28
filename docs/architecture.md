# Architecture

## Service boundaries

Each service owns exactly one aggregate and one database. No service queries another
service's tables directly, even within the same Postgres instance — the only contract
between services is the events they publish and the APIs they expose.

| Service               | Owns                          | Status                |
|------------------------|-------------------------------|------------------------|
| `order-service`        | `Order`, `OrderItem`          | Implemented (Milestone 1) |
| `inventory-service`    | `Inventory` (stock levels)    | Planned (Milestone 2) |
| `payment-service`      | `Payment`                     | Planned (Milestone 3) |
| `notification-service` | `Notification`                | Planned (Milestone 3) |
| `shared-kernel`        | Cross-cutting concerns only — no domain entities | Implemented |

`shared-kernel` is a library, not a service: it ships the `ApiError` contract, the
domain exceptions every service maps to HTTP statuses, and the correlation-id filter. It
is deliberately kept small. The moment it would need to hold a domain entity or
business rule shared between two services, that's a signal the two services have an
undeclared coupling that needs a different fix (an API call, an event, or merging the
services) — not a shared model.

## Why these technology choices

- **PostgreSQL per service, not a shared database.** A shared database between services
  is the most common way a "microservices" system quietly becomes a distributed
  monolith: any service can change another's table, and now you can't deploy
  independently anyway. Each service gets its own schema (in this project, its own
  Flyway migration set) from day one, even before there's a second instance with its
  own infrastructure.
- **Kafka (via Redpanda locally) over a request/response call between services.** The
  business flow is naturally asynchronous — `order-service` doesn't need an immediate
  answer from `inventory-service`, it needs to know eventually. A broker also means
  `inventory-service` being down doesn't take `order-service` down with it; events queue
  up and get processed when it recovers.
- **Redpanda instead of Kafka + Zookeeper for local development.** Same wire protocol and
  client compatibility as Kafka, but one container instead of two, and a few seconds of
  startup instead of dozens. The production target is any Kafka-API-compatible broker —
  swapping back to Apache Kafka for a real deployment is a docker-compose / Helm chart
  change, not a code change.
- **Flyway over Hibernate `ddl-auto: update`.** Migrations are reviewable, ordered, and
  reproducible across environments. `ddl-auto` is set to `validate`, not `update` —
  Hibernate is allowed to confirm the schema matches the entities, never to silently
  alter it.
- **Maven multi-module monorepo over one repo per service.** See the main
  [README](../README.md#architecture) for the reasoning — short version: at this team
  size, repo-per-service buys ceremony, not isolation.

## Request flow inside `order-service`

```
HTTP request
  → CorrelationIdFilter (shared-kernel)       — stamps/propagates X-Correlation-Id
  → OrderController                            — validates, deserializes
  → OrderService                                — transaction boundary, business rules
  → OrderRepository (Spring Data JPA)           — persistence
  → GlobalApiExceptionHandler (shared-kernel)    — only on the error path
```

Nothing about this changes once Kafka enters the picture in Milestone 2 — the outbox
relay is an additional consumer of the same transaction, not a different request path.
See [`outbox-pattern.md`](outbox-pattern.md).
