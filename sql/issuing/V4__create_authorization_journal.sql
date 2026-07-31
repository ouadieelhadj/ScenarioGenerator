-- Journal financier append-only et structures de reservation/limites.

CREATE TABLE issuing_authorization (
    id UUID PRIMARY KEY,
    issuer_id VARCHAR(64) NOT NULL,
    caller_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    payment_identifier_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL,
    original_transaction_id VARCHAR(128),
    amount_minor BIGINT NOT NULL,
    approved_amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    internal_response_code VARCHAR(64) NOT NULL,
    authorization_code VARCHAR(16),
    retryable BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_issuing_auth_identifier FOREIGN KEY (payment_identifier_id)
        REFERENCES issuing_payment_identifier(id),
    CONSTRAINT uk_issuing_auth_idempotency
        UNIQUE (issuer_id, caller_id, idempotency_key),
    CONSTRAINT uk_issuing_auth_transaction
        UNIQUE (issuer_id, caller_id, transaction_id),
    CONSTRAINT ck_issuing_auth_amounts CHECK (
        amount_minor >= 0 AND approved_amount_minor >= 0
        AND approved_amount_minor <= amount_minor)
);

CREATE INDEX idx_issuing_auth_lookup
    ON issuing_authorization (issuer_id, transaction_id, status);

CREATE TABLE issuing_authorization_event (
    id UUID PRIMARY KEY,
    authorization_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    details_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_issuing_auth_event FOREIGN KEY (authorization_id)
        REFERENCES issuing_authorization(id)
);

CREATE INDEX idx_issuing_auth_event_timeline
    ON issuing_authorization_event (authorization_id, created_at);

CREATE TABLE issuing_authorization_hold (
    id UUID PRIMARY KEY,
    issuer_id VARCHAR(64) NOT NULL,
    authorization_id UUID NOT NULL,
    funding_reference VARCHAR(128) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_issuing_hold_auth FOREIGN KEY (authorization_id)
        REFERENCES issuing_authorization(id),
    CONSTRAINT uk_issuing_hold_funding_ref
        UNIQUE (issuer_id, funding_reference),
    CONSTRAINT ck_issuing_hold_amount CHECK (amount_minor >= 0)
);

CREATE TABLE issuing_limit_counter (
    id UUID PRIMARY KEY,
    issuer_id VARCHAR(64) NOT NULL,
    contract_id UUID NOT NULL,
    counter_type VARCHAR(32) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    consumed_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_issuing_counter_contract FOREIGN KEY (contract_id)
        REFERENCES issuing_card_contract(id),
    CONSTRAINT uk_issuing_limit_counter
        UNIQUE (issuer_id, contract_id, counter_type, period_key),
    CONSTRAINT ck_issuing_counter_amount CHECK (consumed_minor >= 0)
);
