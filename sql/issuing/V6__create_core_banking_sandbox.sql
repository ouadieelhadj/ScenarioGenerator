-- Sandbox Core Banking JSON/REST, persistant et idempotent.
-- Il est destine aux recettes locales du module Issuing et non a la production.

CREATE TABLE issuing_core_banking_sandbox_account (
    issuer_id VARCHAR(64) NOT NULL,
    funding_contract_id VARCHAR(128) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    available_balance_minor BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_issuing_cb_sandbox_account
        PRIMARY KEY (issuer_id, funding_contract_id),
    CONSTRAINT ck_issuing_cb_sandbox_balance
        CHECK (available_balance_minor >= 0),
    CONSTRAINT ck_issuing_cb_sandbox_status
        CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE TABLE issuing_core_banking_sandbox_authorization (
    id UUID PRIMARY KEY,
    issuer_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    funding_contract_id VARCHAR(128) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    response_status VARCHAR(32) NOT NULL,
    response_code VARCHAR(64) NOT NULL,
    approved_amount_minor BIGINT NOT NULL,
    funding_reference VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_issuing_cb_sandbox_idempotency
        UNIQUE (issuer_id, idempotency_key),
    CONSTRAINT ck_issuing_cb_sandbox_amount
        CHECK (amount_minor > 0 AND approved_amount_minor >= 0)
);

CREATE INDEX ix_issuing_cb_sandbox_transaction
    ON issuing_core_banking_sandbox_authorization
        (issuer_id, transaction_id);
