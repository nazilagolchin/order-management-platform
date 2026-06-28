# notification-service

Planned for **Milestone 3**.

Consumes the terminal saga events (`OrderConfirmedEvent`, `OrderCancelledEvent`,
`PaymentFailedEvent`, `InventoryReservationFailedEvent`) and records/simulates
customer notifications.

See the [root README](../README.md#roadmap) for the overall delivery roadmap and
[docs/saga-flow.md](../docs/saga-flow.md) for how this service participates in
the order saga.
