-- Émission virtuelle par coffre PAN. Cette migration reste append-only.

ALTER TABLE issuing_card_instrument
    ADD COLUMN IF NOT EXISTS issued_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS issuance_idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS issuance_fingerprint VARCHAR(64);

UPDATE issuing_card_instrument
SET issued_by = COALESCE(issued_by, 'MIGRATION'),
    issuance_idempotency_key =
        COALESCE(issuance_idempotency_key, 'MIGRATION-' || id::text),
    issuance_fingerprint =
        COALESCE(issuance_fingerprint,
                 encode(sha256(('MIGRATION-' || id::text)::bytea), 'hex'))
WHERE issued_by IS NULL
   OR issuance_idempotency_key IS NULL
   OR issuance_fingerprint IS NULL;

ALTER TABLE issuing_card_instrument
    ALTER COLUMN issued_by SET NOT NULL,
    ALTER COLUMN issuance_idempotency_key SET NOT NULL,
    ALTER COLUMN issuance_fingerprint SET NOT NULL;

ALTER TABLE issuing_card_instrument
    ADD CONSTRAINT uk_issuing_instrument_idempotency
    UNIQUE (issuer_id, issued_by, issuance_idempotency_key);

CREATE TABLE IF NOT EXISTS issuing_payment_identifier (
    id                  UUID PRIMARY KEY,
    issuer_id           VARCHAR(64) NOT NULL,
    instrument_id       UUID NOT NULL,
    identifier_type     VARCHAR(32) NOT NULL,
    vault_reference     VARCHAR(128) NOT NULL UNIQUE,
    masked_value        VARCHAR(32) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    effective_from      TIMESTAMPTZ NOT NULL,
    effective_to        TIMESTAMPTZ,
    CONSTRAINT fk_issuing_identifier_instrument
        FOREIGN KEY (instrument_id) REFERENCES issuing_card_instrument (id)
);

CREATE INDEX IF NOT EXISTS idx_issuing_identifier_instrument
    ON issuing_payment_identifier (issuer_id, instrument_id, status);
