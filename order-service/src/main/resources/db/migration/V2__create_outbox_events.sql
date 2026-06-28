CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT NOT NULL,
    event_version   INTEGER NOT NULL,
    correlation_id  VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
