CREATE TABLE inventory (
    product_id          UUID PRIMARY KEY,
    available_quantity   INTEGER NOT NULL CHECK (available_quantity >= 0),
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE stock_reservations (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uq_stock_reservations_order_id ON stock_reservations (order_id);

-- Seed stock for the sample product ids used in docs/api-examples.md, so a
-- freshly started stack can run the documented end-to-end create-order flow
-- without first having to provision inventory by hand.
INSERT INTO inventory (product_id, available_quantity) VALUES
    ('22222222-2222-2222-2222-222222222222', 50),
    ('33333333-3333-3333-3333-333333333333', 50);
