CREATE SEQUENCE IF NOT EXISTS way4_file_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS way4_aura_binding (
    id UUID PRIMARY KEY,
    binding_type VARCHAR(40) NOT NULL,
    source_code VARCHAR(128) NOT NULL,
    aura_code VARCHAR(128) NOT NULL,
    binding_version INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    source_reference VARCHAR(255) NOT NULL,
    created_by VARCHAR(96) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_way4_binding_version CHECK (binding_version > 0),
    CONSTRAINT ck_way4_binding_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE INDEX IF NOT EXISTS ix_way4_binding_resolution
    ON way4_aura_binding(binding_type, source_code, active, valid_from, valid_to);
CREATE UNIQUE INDEX IF NOT EXISTS uk_way4_binding_active_version
    ON way4_aura_binding(binding_type, source_code, binding_version)
    WHERE active = TRUE;

CREATE TABLE IF NOT EXISTS way4_file_batch (
    id UUID PRIMARY KEY,
    file_number BIGINT NOT NULL,
    extended_file_name VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    xml_sha256 VARCHAR(64),
    xsd_sha256 VARCHAR(64),
    mapping_version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_way4_file_number UNIQUE(file_number),
    CONSTRAINT uk_way4_file_name UNIQUE(extended_file_name),
    CONSTRAINT uk_way4_file_idempotency UNIQUE(idempotency_key),
    CONSTRAINT ck_way4_file_status CHECK (status IN ('DRAFT','VALIDATED','STAGED','SUBMITTED',
        'ACKNOWLEDGED','COMPLETED','XSD_REJECTED','STAGE_FAILED','SUBMISSION_FAILED',
        'PARTIALLY_COMPLETED','FAILED_FINAL'))
);

CREATE TABLE IF NOT EXISTS way4_application_state (
    id UUID PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    reg_number VARCHAR(96) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(40) NOT NULL,
    way4_reference VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_way4_application_source UNIQUE(source_type, source_id),
    CONSTRAINT uk_way4_application_reg_number UNIQUE(reg_number),
    CONSTRAINT ck_way4_application_status CHECK (status IN ('PENDING','GENERATED','SUBMITTED',
        'ACCEPTED','RECONCILED','MAPPING_BLOCKED','WAY4_REJECTED_RETRYABLE',
        'WAY4_REJECTED_FINAL','RECONCILIATION_REQUIRED'))
);

-- No AURA binding is seeded here: example LOCAL_* values are not production referentials.
-- No MID/TID rule is created until the authority decision is formally approved.
