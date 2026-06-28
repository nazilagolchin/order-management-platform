# inventory-service

Planned for **Milestone 2**.

Consumes `OrderCreatedEvent` from Kafka, reserves stock against the `Inventory`
table, and publishes `InventoryReservedEvent` or `InventoryReservationFailedEvent`.

See the [root README](../README.md#roadmap) for the overall delivery roadmap and
[docs/saga-flow.md](../docs/saga-flow.md) for how this service participates in
the order saga.
