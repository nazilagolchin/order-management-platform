# payment-service

Planned for **Milestone 3**.

Consumes `InventoryReservedEvent`, simulates a payment provider call, and
publishes `PaymentCompletedEvent` or `PaymentFailedEvent`.

See the [root README](../README.md#roadmap) for the overall delivery roadmap and
[docs/saga-flow.md](../docs/saga-flow.md) for how this service participates in
the order saga.
