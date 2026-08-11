CREATE TABLE IF NOT EXISTS onboarding_way4_export_state (
    case_id UUID PRIMARY KEY REFERENCES merchant_onboarding_case(id),
    application_reg_number VARCHAR(96) NOT NULL,
    connector_file_id UUID,
    status VARCHAR(32) NOT NULL,
    way4_client_id VARCHAR(160),
    merchant_contract_number VARCHAR(160),
    mid VARCHAR(64),
    tids_json TEXT,
    return_file_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_onboarding_way4_reg_number UNIQUE(application_reg_number),
    CONSTRAINT ck_onboarding_way4_status CHECK(status IN ('PENDING','GENERATED','RECONCILED','REJECTED'))
);

CREATE INDEX IF NOT EXISTS ix_onboarding_way4_status
    ON onboarding_way4_export_state(status, updated_at);
