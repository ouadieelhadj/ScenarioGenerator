-- Lot 0 / Phase 1 du module cartes et issuing.
-- Les tables ne contiennent aucun PAN clair, PIN block ou secret HSM.

CREATE TABLE IF NOT EXISTS issuing_card_product (
    id                          UUID PRIMARY KEY,
    issuer_id                   VARCHAR(64) NOT NULL,
    product_code                VARCHAR(64) NOT NULL,
    product_version             INTEGER NOT NULL CHECK (product_version > 0),
    card_type                   VARCHAR(32) NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    status                      VARCHAR(24) NOT NULL,
    purchase_enabled            BOOLEAN NOT NULL,
    cash_enabled                BOOLEAN NOT NULL,
    ecommerce_enabled           BOOLEAN NOT NULL,
    created_by                  VARCHAR(64) NOT NULL,
    creation_idempotency_key    VARCHAR(128) NOT NULL,
    creation_fingerprint        VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_issuing_product_version
        UNIQUE (issuer_id, product_code, product_version),
    CONSTRAINT uk_issuing_product_idempotency
        UNIQUE (issuer_id, created_by, creation_idempotency_key)
);

CREATE TABLE IF NOT EXISTS issuing_card_contract (
    id                          UUID PRIMARY KEY,
    issuer_id                   VARCHAR(64) NOT NULL,
    external_reference          VARCHAR(128) NOT NULL,
    customer_id                 VARCHAR(128) NOT NULL,
    cardholder_id               VARCHAR(128) NOT NULL,
    funding_contract_id         VARCHAR(128) NOT NULL,
    product_id                  UUID NOT NULL,
    status                      VARCHAR(24) NOT NULL,
    created_by                  VARCHAR(64) NOT NULL,
    creation_idempotency_key    VARCHAR(128) NOT NULL,
    creation_fingerprint        VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_issuing_contract_product
        FOREIGN KEY (product_id) REFERENCES issuing_card_product (id),
    CONSTRAINT uk_issuing_contract_external
        UNIQUE (issuer_id, external_reference),
    CONSTRAINT uk_issuing_contract_idempotency
        UNIQUE (issuer_id, created_by, creation_idempotency_key)
);

CREATE TABLE IF NOT EXISTS issuing_card_instrument (
    id                          UUID PRIMARY KEY,
    issuer_id                   VARCHAR(64) NOT NULL,
    contract_id                 UUID NOT NULL,
    pan_vault_reference         VARCHAR(128) NOT NULL UNIQUE,
    masked_pan                  VARCHAR(32) NOT NULL,
    expiry_yymm                 VARCHAR(4) NOT NULL,
    status                      VARCHAR(32) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_issuing_instrument_contract
        FOREIGN KEY (contract_id) REFERENCES issuing_card_contract (id)
);

CREATE INDEX IF NOT EXISTS idx_issuing_instrument_contract
    ON issuing_card_instrument (issuer_id, contract_id);

CREATE TABLE IF NOT EXISTS issuing_outbox_event (
    id                          UUID PRIMARY KEY,
    aggregate_type              VARCHAR(64) NOT NULL,
    aggregate_id                VARCHAR(64) NOT NULL,
    event_type                  VARCHAR(96) NOT NULL,
    correlation_id              VARCHAR(128) NOT NULL,
    payload_json                TEXT NOT NULL,
    status                      VARCHAR(16) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    published_at                TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_issuing_outbox_pending
    ON issuing_outbox_event (status, created_at);
