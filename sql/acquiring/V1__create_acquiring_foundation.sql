-- Fondation Acquisition TPE/e-commerce.
-- Prerequis : sql/issuing/V7__generalize_payment_contract.sql.
-- Aucun PAN, CVC, PIN block ou secret cryptographique n'est stocke ici.

CREATE TABLE IF NOT EXISTS acceptance_product_version (
    id UUID PRIMARY KEY,
    acquirer_id VARCHAR(64) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_version INTEGER NOT NULL CHECK (product_version > 0),
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('TPE', 'ECOMMERCE', 'BOTH')),
    default_currency VARCHAR(3) NOT NULL CHECK (default_currency ~ '^[0-9]{3}$'),
    status VARCHAR(24) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_acceptance_product_version
        UNIQUE (acquirer_id, product_code, product_version)
);

CREATE TABLE IF NOT EXISTS merchant (
    id UUID PRIMARY KEY,
    acquirer_id VARCHAR(64) NOT NULL,
    legal_name VARCHAR(160) NOT NULL,
    trading_name VARCHAR(160) NOT NULL,
    registration_number VARCHAR(64) NOT NULL,
    country VARCHAR(2) NOT NULL,
    mcc VARCHAR(4) NOT NULL CHECK (mcc ~ '^[0-9]{4}$'),
    status VARCHAR(24) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    creation_idempotency_key VARCHAR(128) NOT NULL,
    creation_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_merchant_registration UNIQUE (acquirer_id, registration_number),
    CONSTRAINT uk_merchant_idempotency
        UNIQUE (acquirer_id, created_by, creation_idempotency_key)
);

CREATE TABLE IF NOT EXISTS merchant_outlet (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchant (id),
    outlet_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    country VARCHAR(2) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_merchant_outlet_code UNIQUE (merchant_id, outlet_code)
);

CREATE TABLE IF NOT EXISTS acquiring_contract_detail (
    contract_id UUID PRIMARY KEY REFERENCES payment_contract (id),
    acquirer_id VARCHAR(64) NOT NULL,
    merchant_id UUID NOT NULL REFERENCES merchant (id),
    merchant_acceptor_id VARCHAR(15) NOT NULL,
    mcc VARCHAR(4) NOT NULL CHECK (mcc ~ '^[0-9]{4}$'),
    settlement_currency VARCHAR(3) NOT NULL CHECK (settlement_currency ~ '^[0-9]{3}$'),
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('TPE', 'ECOMMERCE', 'BOTH')),
    CONSTRAINT uk_acquiring_mid UNIQUE (acquirer_id, merchant_acceptor_id)
);

CREATE TABLE IF NOT EXISTS acquiring_device_contract_detail (
    contract_id UUID PRIMARY KEY REFERENCES payment_contract (id),
    acquirer_id VARCHAR(64) NOT NULL,
    outlet_id UUID NOT NULL REFERENCES merchant_outlet (id),
    terminal_id VARCHAR(8) NOT NULL,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('TPE', 'BOTH')),
    extended_set BOOLEAN NOT NULL,
    mac_data VARCHAR(3) NOT NULL CHECK (mac_data IN ('BIN', 'HEX')),
    mac_required BOOLEAN NOT NULL,
    CONSTRAINT uk_acquiring_tid UNIQUE (acquirer_id, terminal_id)
);

CREATE TABLE IF NOT EXISTS terminal_device (
    id UUID PRIMARY KEY,
    acquirer_id VARCHAR(64) NOT NULL,
    serial_number VARCHAR(96) NOT NULL,
    model_code VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_terminal_serial UNIQUE (acquirer_id, serial_number)
);

CREATE TABLE IF NOT EXISTS terminal_assignment (
    id UUID PRIMARY KEY,
    terminal_device_id UUID NOT NULL REFERENCES terminal_device (id),
    outlet_id UUID NOT NULL REFERENCES merchant_outlet (id),
    device_contract_id UUID NOT NULL REFERENCES payment_contract (id),
    assigned_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_terminal_active_assignment
    ON terminal_assignment (terminal_device_id) WHERE active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_contract_active_assignment
    ON terminal_assignment (device_contract_id) WHERE active;

CREATE TABLE IF NOT EXISTS ecommerce_store (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchant (id),
    store_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    allowed_domain VARCHAR(255) NOT NULL,
    return_url VARCHAR(512) NOT NULL,
    notification_url VARCHAR(512) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ecommerce_store_code UNIQUE (merchant_id, store_code)
);

CREATE TABLE IF NOT EXISTS ecommerce_acceptance_profile (
    id UUID PRIMARY KEY,
    acquirer_id VARCHAR(64) NOT NULL,
    store_id UUID NOT NULL REFERENCES ecommerce_store (id),
    contract_id UUID NOT NULL REFERENCES payment_contract (id),
    logical_terminal_id VARCHAR(8) NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[0-9]{3}$'),
    capture_mode VARCHAR(24) NOT NULL CHECK (capture_mode IN ('IMMEDIATE', 'DEFERRED')),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_ecommerce_logical_tid UNIQUE (acquirer_id, logical_terminal_id)
);

CREATE TABLE IF NOT EXISTS acquiring_outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_acquiring_outbox_pending
    ON acquiring_outbox_event (status, created_at);
