-- Achat e-commerce Acquisition. Le PAN/CVC et les donnees 3DS ne sont pas
-- persistes dans cette table.

CREATE TABLE IF NOT EXISTS acquiring_ecommerce_transaction (
    id UUID PRIMARY KEY,
    acquirer_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    profile_id UUID NOT NULL REFERENCES ecommerce_acceptance_profile (id),
    contract_id UUID NOT NULL REFERENCES payment_contract (id),
    merchant_order_id VARCHAR(128) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[0-9]{3}$'),
    payment_identifier_type VARCHAR(24) NOT NULL,
    network_route VARCHAR(32) NOT NULL
        CHECK (network_route IN ('LOCAL_ISSUING', 'DMAS_MASTERCARD', 'SWAM', 'VISA')),
    authentication_status VARCHAR(24) NOT NULL,
    network_stan VARCHAR(6) NOT NULL,
    network_rrn VARCHAR(12) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_code VARCHAR(8),
    authorization_code VARCHAR(12),
    approved_amount_minor BIGINT NOT NULL DEFAULT 0,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_acq_ecom_transaction_reference
        UNIQUE (acquirer_id, transaction_id),
    CONSTRAINT uk_acq_ecom_idempotency
        UNIQUE (acquirer_id, idempotency_key)
);

-- Idempotent upgrade for databases created before LOCAL_ISSUING existed.
ALTER TABLE acquiring_ecommerce_transaction
    DROP CONSTRAINT IF EXISTS acquiring_ecommerce_transaction_network_route_check;
ALTER TABLE acquiring_ecommerce_transaction
    ADD CONSTRAINT acquiring_ecommerce_transaction_network_route_check
    CHECK (network_route IN ('LOCAL_ISSUING', 'DMAS_MASTERCARD', 'SWAM', 'VISA'));

CREATE INDEX IF NOT EXISTS idx_acq_ecom_transaction_status
    ON acquiring_ecommerce_transaction (status, created_at);
