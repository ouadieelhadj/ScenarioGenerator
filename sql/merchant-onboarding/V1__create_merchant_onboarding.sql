-- Portail d'affiliation commercant - lot backend initial.
-- Aucun mot de passe, PAN, CVC, PIN block ou secret n'est stocke ici.

CREATE TABLE IF NOT EXISTS merchant_portal_account (
    id UUID PRIMARY KEY,
    login VARCHAR(96) NOT NULL,
    email VARCHAR(254) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by_commercial VARCHAR(96) NOT NULL,
    identity_user_id VARCHAR(96),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_merchant_portal_account_login UNIQUE (login),
    CONSTRAINT uk_merchant_portal_account_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS merchant_onboarding_case (
    id UUID PRIMARY KEY,
    case_reference VARCHAR(40) NOT NULL,
    account_id UUID NOT NULL REFERENCES merchant_portal_account (id),
    acquirer_id VARCHAR(64) NOT NULL,
    created_by_commercial VARCHAR(96) NOT NULL,
    legal_name VARCHAR(160),
    trading_name VARCHAR(160),
    registration_number VARCHAR(64),
    country VARCHAR(2),
    mcc VARCHAR(4),
    settlement_account_reference VARCHAR(96),
    settlement_currency VARCHAR(3),
    product_id UUID,
    acceptance_channel VARCHAR(16),
    outlet_code VARCHAR(64),
    outlet_name VARCHAR(160),
    outlet_address VARCHAR(255),
    terminal_count INTEGER NOT NULL DEFAULT 0 CHECK (terminal_count BETWEEN 0 AND 999),
    status VARCHAR(32) NOT NULL,
    submitted_by VARCHAR(96),
    checked_by VARCHAR(96),
    rejection_reason VARCHAR(500),
    acquiring_merchant_id UUID,
    merchant_acceptor_id VARCHAR(15),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_merchant_onboarding_reference UNIQUE (case_reference),
    CONSTRAINT uk_merchant_onboarding_registration UNIQUE (acquirer_id, registration_number)
);

CREATE TABLE IF NOT EXISTS merchant_workflow_request (
    id BIGSERIAL PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES merchant_onboarding_case (id),
    module_code VARCHAR(48) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    object_reference VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_by VARCHAR(96) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    decided_by VARCHAR(96),
    decided_at TIMESTAMPTZ,
    CONSTRAINT uk_merchant_workflow_case UNIQUE (case_id)
);

CREATE INDEX IF NOT EXISTS idx_merchant_workflow_pending
    ON merchant_workflow_request (status, created_at);

CREATE TABLE IF NOT EXISTS merchant_provisioning_job (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES merchant_onboarding_case (id),
    idempotency_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_merchant_provisioning_case UNIQUE (case_id),
    CONSTRAINT uk_merchant_provisioning_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_merchant_provisioning_pending
    ON merchant_provisioning_job (status, created_at);
