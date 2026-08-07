-- Identifiants attribues exclusivement par Acquiring et recus idempotents d'onboarding.

CREATE SEQUENCE IF NOT EXISTS acquiring_mid_sequence
    START WITH 100000000000000 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS acquiring_tid_sequence
    START WITH 10000000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS acquiring_onboarding_receipt (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    payload_fingerprint VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
