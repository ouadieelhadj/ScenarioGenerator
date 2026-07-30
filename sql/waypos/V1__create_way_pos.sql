DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'way_pos_user') THEN
        CREATE ROLE way_pos_user LOGIN PASSWORD 'postgres123';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS pos_cards (
    pan_hash            VARCHAR(64) PRIMARY KEY,
    pan_masked          VARCHAR(32) NOT NULL,
    expiry_yymm         VARCHAR(4) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    available_balance   BIGINT NOT NULL CHECK (available_balance >= 0),
    blocked_balance     BIGINT NOT NULL DEFAULT 0 CHECK (blocked_balance >= 0),
    pin_pvv             VARCHAR(4),
    pin_pvki            INTEGER,
    mdk_under_lmk       TEXT,
    mdk_kcv             VARCHAR(6),
    mdk_length          INTEGER CHECK (mdk_length IN (16,24)),
    pan_sequence_number VARCHAR(2),
    arpc_arc_hex        VARCHAR(4),
    last_atc            INTEGER,
    version             BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE pos_cards
    ADD COLUMN IF NOT EXISTS pin_pvv VARCHAR(4),
    ADD COLUMN IF NOT EXISTS pin_pvki INTEGER,
    ADD COLUMN IF NOT EXISTS mdk_under_lmk TEXT,
    ADD COLUMN IF NOT EXISTS mdk_kcv VARCHAR(6),
    ADD COLUMN IF NOT EXISTS mdk_length INTEGER,
    ADD COLUMN IF NOT EXISTS pan_sequence_number VARCHAR(2),
    ADD COLUMN IF NOT EXISTS arpc_arc_hex VARCHAR(4),
    ADD COLUMN IF NOT EXISTS last_atc INTEGER;

CREATE TABLE IF NOT EXISTS pos_terminal_profiles (
    terminal_id     VARCHAR(8) PRIMARY KEY,
    merchant_id     VARCHAR(15) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    extended_set    BOOLEAN NOT NULL DEFAULT TRUE,
    mac_data        VARCHAR(3) NOT NULL CHECK (mac_data IN ('BIN','HEX')),
    mac_required    BOOLEAN NOT NULL DEFAULT TRUE,
    batch_id        VARCHAR(6) NOT NULL DEFAULT '000000',
    batch_status    VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    tak_under_lmk   TEXT,
    tak_kcv         VARCHAR(6),
    tak_length      INTEGER CHECK (tak_length IN (8,16)),
    tpk_under_lmk   TEXT,
    tpk_kcv         VARCHAR(6),
    tpk_length      INTEGER CHECK (tpk_length IN (8,16)),
    version         BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE pos_terminal_profiles
    ADD COLUMN IF NOT EXISTS tpk_under_lmk TEXT,
    ADD COLUMN IF NOT EXISTS tpk_kcv VARCHAR(6),
    ADD COLUMN IF NOT EXISTS tpk_length INTEGER,
    ADD COLUMN IF NOT EXISTS batch_status VARCHAR(24) NOT NULL DEFAULT 'OPEN';

UPDATE pos_terminal_profiles SET batch_id = right(batch_id, 6)
    WHERE length(batch_id) > 6;
ALTER TABLE pos_terminal_profiles
    ALTER COLUMN batch_id TYPE VARCHAR(6);

CREATE TABLE IF NOT EXISTS pos_terminal_keys (
    id                  BIGSERIAL PRIMARY KEY,
    terminal_id         VARCHAR(8) NOT NULL REFERENCES pos_terminal_profiles(terminal_id),
    key_type            VARCHAR(8) NOT NULL,
    key_id              VARCHAR(32) NOT NULL,
    key_status          VARCHAR(16) NOT NULL,
    key_algorithm       VARCHAR(1),
    kcv                 VARCHAR(6),
    master_key_id       VARCHAR(32) NOT NULL,
    master_key_type     VARCHAR(8) NOT NULL,
    ansi_x917_block     BYTEA,
    key_under_lmk       TEXT,
    key_length          INTEGER CHECK (key_length IN (8,16)),
    action_code         VARCHAR(1) NOT NULL DEFAULT '0',
    replacement_key_id  VARCHAR(32),
    delivered_at        TIMESTAMPTZ,
    acknowledged_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_pos_terminal_key UNIQUE (terminal_id, key_type, key_id)
);

CREATE INDEX IF NOT EXISTS ix_pos_terminal_keys_delivery
    ON pos_terminal_keys(terminal_id, key_status, id);

CREATE TABLE IF NOT EXISTS pos_interface_keys (
    interface_code  VARCHAR(32) PRIMARY KEY,
    pek_under_lmk   TEXT NOT NULL,
    pek_kcv         VARCHAR(6) NOT NULL,
    pek_length      INTEGER NOT NULL CHECK (pek_length IN (8,16)),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pos_security_keys (
    key_code        VARCHAR(32) PRIMARY KEY,
    key_type        VARCHAR(8) NOT NULL,
    key_under_lmk   TEXT NOT NULL,
    kcv             VARCHAR(6) NOT NULL,
    key_length      INTEGER NOT NULL CHECK (key_length IN (8,16)),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pos_bin_routes (
    id                  BIGSERIAL PRIMARY KEY,
    bin_from            VARCHAR(11) NOT NULL,
    bin_to              VARCHAR(11) NOT NULL,
    interface_code      VARCHAR(32) NOT NULL,
    priority            INTEGER NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (length(bin_from) = length(bin_to))
);

CREATE INDEX IF NOT EXISTS ix_pos_bin_routes_active_priority
    ON pos_bin_routes(active, priority DESC);

CREATE TABLE IF NOT EXISTS pos_authorizations (
    transaction_id          VARCHAR(64) PRIMARY KEY,
    idempotency_key         VARCHAR(128) NOT NULL UNIQUE,
    mti                     VARCHAR(4) NOT NULL,
    processing_code         VARCHAR(6),
    pan_masked              VARCHAR(32),
    pan_hash                VARCHAR(64),
    amount_minor            BIGINT,
    currency                VARCHAR(3),
    stan                    VARCHAR(6),
    rrn                     VARCHAR(12),
    terminal_id             VARCHAR(8),
    merchant_id             VARCHAR(15),
    batch_id                VARCHAR(6),
    network_id              VARCHAR(3),
    operation_name          VARCHAR(64),
    route_code              VARCHAR(32),
    status                  VARCHAR(32) NOT NULL,
    response_code           VARCHAR(3),
    authorization_code      VARCHAR(6),
    arpc_hex                 VARCHAR(510),
    original_transaction_id VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);

ALTER TABLE pos_authorizations
    ADD COLUMN IF NOT EXISTS batch_id VARCHAR(6),
    ADD COLUMN IF NOT EXISTS network_id VARCHAR(3),
    ADD COLUMN IF NOT EXISTS operation_name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS arpc_hex VARCHAR(510);

CREATE INDEX IF NOT EXISTS ix_pos_authorizations_pan_hash
    ON pos_authorizations(pan_hash);
CREATE INDEX IF NOT EXISTS ix_pos_authorizations_terminal_stan
    ON pos_authorizations(terminal_id, stan);

CREATE TABLE IF NOT EXISTS pos_transaction_events (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  VARCHAR(64) NOT NULL REFERENCES pos_authorizations(transaction_id),
    event_type      VARCHAR(32) NOT NULL,
    payload         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pos_holds (
    transaction_id  VARCHAR(64) PRIMARY KEY REFERENCES pos_authorizations(transaction_id),
    pan_hash        VARCHAR(64) NOT NULL,
    amount_minor    BIGINT NOT NULL CHECK (amount_minor > 0),
    status          VARCHAR(16) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS pos_outbox (
    id                  BIGSERIAL PRIMARY KEY,
    transaction_id      VARCHAR(64) NOT NULL REFERENCES pos_authorizations(transaction_id),
    message_type        VARCHAR(32) NOT NULL,
    destination         VARCHAR(32) NOT NULL,
    payload             JSONB,
    payload_ciphertext  BYTEA NOT NULL,
    payload_iv          BYTEA NOT NULL,
    payload_key_id      VARCHAR(16) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_response_code  VARCHAR(3),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_pos_outbox_recovery UNIQUE
        (transaction_id, message_type, destination)
);

ALTER TABLE pos_outbox
    ADD COLUMN IF NOT EXISTS payload_ciphertext BYTEA,
    ADD COLUMN IF NOT EXISTS payload_iv BYTEA,
    ADD COLUMN IF NOT EXISTS payload_key_id VARCHAR(16),
    ADD COLUMN IF NOT EXISTS last_response_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pos_outbox ALTER COLUMN payload DROP NOT NULL;

CREATE TABLE IF NOT EXISTS pos_batch_uploads (
    id                  BIGSERIAL PRIMARY KEY,
    terminal_id         VARCHAR(8) NOT NULL REFERENCES pos_terminal_profiles(terminal_id),
    batch_id            VARCHAR(6) NOT NULL,
    message_fingerprint VARCHAR(64) NOT NULL,
    original_mti        VARCHAR(4) NOT NULL,
    processing_code     VARCHAR(6) NOT NULL,
    network_id          VARCHAR(3),
    amount_minor        BIGINT NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    response_code       VARCHAR(2),
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_pos_batch_upload UNIQUE
        (terminal_id, batch_id, message_fingerprint)
);

CREATE TABLE IF NOT EXISTS pos_file_updates (
    id                  BIGSERIAL PRIMARY KEY,
    terminal_id         VARCHAR(8) NOT NULL REFERENCES pos_terminal_profiles(terminal_id),
    message_fingerprint VARCHAR(64) NOT NULL,
    request_data        BYTEA NOT NULL,
    status              VARCHAR(16) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_pos_file_update_fingerprint UNIQUE
        (terminal_id, message_fingerprint)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    pos_cards, pos_terminal_profiles, pos_terminal_keys, pos_interface_keys,
    pos_security_keys,
    pos_bin_routes, pos_authorizations,
    pos_transaction_events, pos_holds, pos_outbox,
    pos_batch_uploads, pos_file_updates TO way_pos_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO way_pos_user;
