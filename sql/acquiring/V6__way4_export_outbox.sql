CREATE TABLE IF NOT EXISTS acquiring_way4_export_outbox (
    id UUID PRIMARY KEY,
    onboarding_case_id UUID NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(96),
    locked_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(1000),
    way4_file_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_acquiring_way4_export_key UNIQUE(idempotency_key),
    CONSTRAINT ck_acquiring_way4_export_status CHECK
        (status IN ('PENDING','PROCESSING','COMPLETED','MAPPING_BLOCKED','FAILED_FINAL')),
    CONSTRAINT ck_acquiring_way4_export_attempts CHECK (attempts BETWEEN 0 AND 8)
);
ALTER TABLE acquiring_way4_export_outbox
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(96),
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS ix_acquiring_way4_export_dispatch
    ON acquiring_way4_export_outbox(status, available_at, created_at);
CREATE INDEX IF NOT EXISTS ix_acquiring_way4_export_lease
    ON acquiring_way4_export_outbox(status, lease_until);
