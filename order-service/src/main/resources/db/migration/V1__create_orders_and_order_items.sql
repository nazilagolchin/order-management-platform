CREATE TABLE orders (
    id                        UUID PRIMARY KEY,
    customer_id               UUID NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    total_amount              NUMERIC(19, 2) NOT NULL,
    currency                  VARCHAR(3) NOT NULL,
    idempotency_key           VARCHAR(255),
    idempotency_request_hash  VARCHAR(64),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_orders_idempotency_key ON orders (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_orders_customer_id ON orders (customer_id);

CREATE TABLE order_items (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id  UUID NOT NULL,
    quantity    INTEGER NOT NULL,
    unit_price  NUMERIC(19, 2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
