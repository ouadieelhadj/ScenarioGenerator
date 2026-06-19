-- ═══════════════════════════════════════════════════════════
-- ScenarioGenerator — DMAS Schema (D1)
-- ═══════════════════════════════════════════════════════════

\connect scenariogenerator

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'dmas_acquirer_user') THEN
        CREATE USER dmas_acquirer_user WITH PASSWORD 'postgres123';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'dmas_issuer_user') THEN
        CREATE USER dmas_issuer_user WITH PASSWORD 'postgres123';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE scenariogenerator TO dmas_acquirer_user, dmas_issuer_user;

CREATE TABLE IF NOT EXISTS key_store (
    id               BIGSERIAL PRIMARY KEY,
    member_group_id  VARCHAR(20)  NOT NULL,
    key_type         VARCHAR(3)   NOT NULL,
    key_length       INTEGER      NOT NULL DEFAULT 24,
    encrypted_value  VARCHAR(64)  NOT NULL,
    kcv              VARCHAR(6)   NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    description      VARCHAR(255),
    created_at       TIMESTAMP    DEFAULT NOW(),
    activated_at     TIMESTAMP,
    CONSTRAINT chk_key_type   CHECK (key_type IN ('KEK','PEK','MAK')),
    CONSTRAINT chk_key_status CHECK (status   IN ('ACTIVE','INACTIVE','PENDING'))
);
CREATE INDEX IF NOT EXISTS idx_key_store_group  ON key_store(member_group_id);
CREATE INDEX IF NOT EXISTS idx_key_store_type   ON key_store(key_type);
CREATE INDEX IF NOT EXISTS idx_key_store_status ON key_store(status);

CREATE TABLE IF NOT EXISTS dmas_acq_authorizations (
    id                    BIGSERIAL PRIMARY KEY,
    execution_id          BIGINT       REFERENCES executions(id),
    member_group_id       VARCHAR(20),
    de002_pan             VARCHAR(20),
    de003_proc_code       VARCHAR(6),
    de004_amount          BIGINT,
    de007_datetime        VARCHAR(10),
    de011_stan            VARCHAR(6),
    de012_local_time      VARCHAR(6),
    de013_local_date      VARCHAR(4),
    de018_mcc             VARCHAR(4),
    de022_pos_mode        VARCHAR(3),
    de032_acq_id          VARCHAR(11),
    de037_rrn             VARCHAR(12),
    de041_term_id         VARCHAR(8),
    de042_merch_id        VARCHAR(15),
    de043_merch_name      VARCHAR(40),
    de049_currency        VARCHAR(3),
    de052_pin_encrypted   VARCHAR(16),
    pek_kcv_used          VARCHAR(6),
    de064_mac             VARCHAR(16),
    mak_kcv_used          VARCHAR(6),
    de038_auth_code       VARCHAR(6),
    de039_response        VARCHAR(2),
    approved              BOOLEAN,
    mac_valid_on_response BOOLEAN,
    duration_ms           INTEGER,
    request_hex           TEXT,
    response_hex          TEXT,
    sent_at               TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dmas_acq_auth_exec ON dmas_acq_authorizations(execution_id);
CREATE INDEX IF NOT EXISTS idx_dmas_acq_auth_pan  ON dmas_acq_authorizations(de002_pan);

CREATE TABLE IF NOT EXISTS dmas_acq_reversals (
    id                BIGSERIAL PRIMARY KEY,
    execution_id      BIGINT      REFERENCES executions(id),
    dmas_acq_auth_id  BIGINT      REFERENCES dmas_acq_authorizations(id),
    de002_pan         VARCHAR(20),
    de003_proc_code   VARCHAR(6),
    de004_amount      BIGINT,
    de007_datetime    VARCHAR(10),
    de011_stan        VARCHAR(6),
    de037_rrn         VARCHAR(12),
    de038_auth_code   VARCHAR(6),
    de039_original    VARCHAR(2),
    de041_term_id     VARCHAR(8),
    de049_currency    VARCHAR(3),
    de056_orig_data   VARCHAR(40),
    de064_mac         VARCHAR(16),
    de039_response    VARCHAR(2),
    reversed          BOOLEAN,
    duration_ms       INTEGER,
    request_hex       TEXT,
    response_hex      TEXT,
    sent_at           TIMESTAMP   DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dmas_acq_rev_auth ON dmas_acq_reversals(dmas_acq_auth_id);

CREATE TABLE IF NOT EXISTS dmas_acq_advices (
    id                    BIGSERIAL PRIMARY KEY,
    execution_id          BIGINT      REFERENCES executions(id),
    dmas_acq_auth_id      BIGINT      REFERENCES dmas_acq_authorizations(id),
    de002_pan             VARCHAR(20),
    de003_proc_code       VARCHAR(6),
    de004_amount          BIGINT,
    de007_datetime        VARCHAR(10),
    de011_stan            VARCHAR(6),
    de037_rrn             VARCHAR(12),
    de038_auth_code       VARCHAR(6),
    de039_response        VARCHAR(2),
    de049_currency        VARCHAR(3),
    de060_reason          VARCHAR(3),
    de064_mac             VARCHAR(16),
    de039_advice_response VARCHAR(2),
    accepted              BOOLEAN,
    duration_ms           INTEGER,
    request_hex           TEXT,
    response_hex          TEXT,
    sent_at               TIMESTAMP   DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dmas_acq_adv_auth ON dmas_acq_advices(dmas_acq_auth_id);

CREATE TABLE IF NOT EXISTS dmas_iss_authorizations (
    id                  BIGSERIAL PRIMARY KEY,
    member_group_id     VARCHAR(20),
    de002_pan           VARCHAR(20),
    de002_pan_raw       VARCHAR(19),
    de003_proc_code     VARCHAR(6),
    de004_amount        BIGINT,
    de007_datetime      VARCHAR(10),
    de011_stan          VARCHAR(6),
    de012_local_time    VARCHAR(6),
    de013_local_date    VARCHAR(4),
    de018_mcc           VARCHAR(4),
    de022_pos_mode      VARCHAR(3),
    de032_acq_id        VARCHAR(11),
    de037_rrn           VARCHAR(12),
    de041_term_id       VARCHAR(8),
    de042_merch_id      VARCHAR(15),
    de043_merch_name    VARCHAR(40),
    de049_currency      VARCHAR(3),
    de052_pin_encrypted VARCHAR(16),
    pek_kcv_used        VARCHAR(6),
    de064_mac           VARCHAR(16),
    mak_kcv_used        VARCHAR(6),
    mac_verified        BOOLEAN     DEFAULT FALSE,
    pin_decrypted_ok    BOOLEAN     DEFAULT FALSE,
    de038_auth_code     VARCHAR(6),
    de039_response      VARCHAR(2),
    decision_reason     VARCHAR(100),
    approved            BOOLEAN,
    request_hex         TEXT,
    response_hex        TEXT,
    received_at         TIMESTAMP   DEFAULT NOW(),
    responded_at        TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dmas_iss_auth_pan  ON dmas_iss_authorizations(de002_pan);
CREATE INDEX IF NOT EXISTS idx_dmas_iss_auth_stan ON dmas_iss_authorizations(de011_stan);

CREATE TABLE IF NOT EXISTS dmas_iss_reversals (
    id                BIGSERIAL PRIMARY KEY,
    dmas_iss_auth_id  BIGINT      REFERENCES dmas_iss_authorizations(id),
    de002_pan         VARCHAR(20),
    de003_proc_code   VARCHAR(6),
    de004_amount      BIGINT,
    de007_datetime    VARCHAR(10),
    de011_stan        VARCHAR(6),
    de037_rrn         VARCHAR(12),
    de038_auth_code   VARCHAR(6),
    de039_original    VARCHAR(2),
    de041_term_id     VARCHAR(8),
    de049_currency    VARCHAR(3),
    de056_orig_data   VARCHAR(40),
    de064_mac         VARCHAR(16),
    mac_verified      BOOLEAN     DEFAULT FALSE,
    de039_response    VARCHAR(2),
    reversed          BOOLEAN,
    request_hex       TEXT,
    response_hex      TEXT,
    received_at       TIMESTAMP   DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dmas_iss_rev_auth ON dmas_iss_reversals(dmas_iss_auth_id);

CREATE TABLE IF NOT EXISTS dmas_iss_advices (
    id                    BIGSERIAL PRIMARY KEY,
    dmas_iss_auth_id      BIGINT      REFERENCES dmas_iss_authorizations(id),
    de002_pan             VARCHAR(20),
    de003_proc_code       VARCHAR(6),
    de004_amount          BIGINT,
    de007_datetime        VARCHAR(10),
    de011_stan            VARCHAR(6),
    de037_rrn             VARCHAR(12),
    de038_auth_code       VARCHAR(6),
    de039_response        VARCHAR(2),
    de049_currency        VARCHAR(3),
    de060_reason          VARCHAR(3),
    de064_mac             VARCHAR(16),
    mac_verified          BOOLEAN     DEFAULT FALSE,
    de039_advice_response VARCHAR(2),
    accepted              BOOLEAN,
    request_hex           TEXT,
    response_hex          TEXT,
    received_at            TIMESTAMP   DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dmas_iss_adv_auth ON dmas_iss_advices(dmas_iss_auth_id);

INSERT INTO key_store (member_group_id, key_type, key_length, encrypted_value, kcv, status, description)
SELECT 'TESTGRP01', 'KEK', 24,
       '0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF',
       '000000',
       'ACTIVE',
       'KEK de test — KCV à recalculer via HsmService une fois D2 livré'
WHERE NOT EXISTS (
    SELECT 1 FROM key_store WHERE member_group_id = 'TESTGRP01' AND key_type = 'KEK'
);

GRANT SELECT ON key_store TO dmas_acquirer_user, dmas_issuer_user;
GRANT USAGE, SELECT ON key_store_id_seq TO dmas_acquirer_user, dmas_issuer_user;
GRANT SELECT ON executions TO dmas_acquirer_user, dmas_issuer_user;

GRANT ALL PRIVILEGES ON dmas_acq_authorizations, dmas_acq_reversals, dmas_acq_advices
    TO dmas_acquirer_user;
GRANT USAGE, SELECT ON
    dmas_acq_authorizations_id_seq, dmas_acq_reversals_id_seq, dmas_acq_advices_id_seq
    TO dmas_acquirer_user;

GRANT ALL PRIVILEGES ON dmas_iss_authorizations, dmas_iss_reversals, dmas_iss_advices
    TO dmas_issuer_user;
GRANT USAGE, SELECT ON
    dmas_iss_authorizations_id_seq, dmas_iss_reversals_id_seq, dmas_iss_advices_id_seq
    TO dmas_issuer_user;
