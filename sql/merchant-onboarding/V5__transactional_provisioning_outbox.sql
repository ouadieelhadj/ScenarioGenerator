CREATE TABLE IF NOT EXISTS onboarding_outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL REFERENCES merchant_onboarding_case(id),
    event_type VARCHAR(96) NOT NULL,
    schema_version VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    payload JSONB NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(96),
    locked_at TIMESTAMP WITH TIME ZONE,
    lease_until TIMESTAMP WITH TIME ZONE,
    processed_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(1000),
    last_correlation_id VARCHAR(96),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_onboarding_outbox_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_onboarding_outbox_status CHECK
        (status IN ('PENDING','PROCESSING','COMPLETED','FAILED_FINAL')),
    CONSTRAINT ck_onboarding_outbox_attempts CHECK (attempt_count BETWEEN 0 AND 8),
    CONSTRAINT ck_onboarding_outbox_lock CHECK (
        (status = 'PROCESSING' AND locked_by IS NOT NULL AND locked_at IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND locked_by IS NULL AND locked_at IS NULL AND lease_until IS NULL))
);

CREATE INDEX IF NOT EXISTS ix_onboarding_outbox_dispatch
    ON onboarding_outbox(status, available_at, lease_until, created_at);

CREATE TABLE IF NOT EXISTS onboarding_outbox_retry_order (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES onboarding_outbox(id),
    ordered_by VARCHAR(96) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    previous_attempts INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_onboarding_outbox_retry_event
    ON onboarding_outbox_retry_order(event_id, created_at);
