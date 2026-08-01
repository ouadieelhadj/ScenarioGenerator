-- Etat 3DS du membre LanaCash. Les PAN, OTP et valeurs d'authentification
-- brutes ne sont jamais persistes dans cette table.

CREATE TABLE IF NOT EXISTS three_ds_member_authentication (
    three_ds_server_trans_id UUID PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL UNIQUE,
    correlation_id VARCHAR(128) NOT NULL,
    ds_trans_id UUID,
    acs_trans_id UUID,
    program VARCHAR(16) NOT NULL CHECK (program IN ('VISA', 'MASTERCARD')),
    flow VARCHAR(24) NOT NULL CHECK (flow IN ('FRICTIONLESS', 'CHALLENGE')),
    issuer_mode VARCHAR(32) NOT NULL
        CHECK (issuer_mode IN ('MEMBER', 'EXTERNAL_SIMULATOR')),
    trans_status CHAR(1) NOT NULL,
    pan_fingerprint VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    eci VARCHAR(2),
    evidence_fingerprint VARCHAR(64),
    evidence_consumed_at TIMESTAMPTZ,
    challenge_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- Mise a niveau idempotente des bases initialisees avec la premiere version.
ALTER TABLE three_ds_member_authentication
    ALTER COLUMN trans_status TYPE CHAR(1)
    USING trans_status::CHAR(1);

ALTER TABLE three_ds_member_authentication
    ADD COLUMN IF NOT EXISTS evidence_consumed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uk_three_ds_member_ds_trans_id
    ON three_ds_member_authentication (ds_trans_id)
    WHERE ds_trans_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_three_ds_member_acs_trans_id
    ON three_ds_member_authentication (acs_trans_id)
    WHERE acs_trans_id IS NOT NULL;
