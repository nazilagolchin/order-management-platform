# inventory-service

Owns the `Inventory` aggregate: one row per product tracking available stock.

Consumes `OrderCreatedEvent` from the `order.events` Kafka topic and reserves
stock for every line item, all-or-nothing, via a pessimistic row lock (so two
concurrent orders for the same product can't oversell it). The outcome is
published through this service's own transactional outbox as
`InventoryReservedEvent` or `InventoryReservationFailedEvent` on
`inventory.events`. See [docs/outbox-pattern.md](../docs/outbox-pattern.md)
and [docs/saga-flow.md](../docs/saga-flow.md) for how this fits into the
overall saga.

A `GET /api/inventory/{productId}` endpoint exposes current stock for
inspection; it is not part of the saga itself.

Runs on port `8082` locally / `8082` in Docker Compose; Swagger UI is at
`/swagger-ui.html`.

See the [root README](../README.md#roadmap) for the overall delivery roadmap.
