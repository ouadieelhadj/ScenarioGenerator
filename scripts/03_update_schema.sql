-- ═══════════════════════════════════════════════════════════
-- ScenarioGenerator — Update Schema
-- Nouvelles tables : acq_* et iss_*
-- ═══════════════════════════════════════════════════════════

\connect scenariogenerator

-- ═══════════════════════════════════════════════════════════
-- ACQUIRER TABLES
-- ═══════════════════════════════════════════════════════════

CREATE TABLE acq_authorizations (
    id                BIGSERIAL PRIMARY KEY,
    execution_id      BIGINT      REFERENCES executions(id),
    -- Request 0100
    de002_pan         VARCHAR(20),
    de003_proc_code   VARCHAR(6),
    de004_amount      BIGINT,
    de007_datetime    VARCHAR(10),
    de011_stan        VARCHAR(6),
    de012_local_time  VARCHAR(6),
    de013_local_date  VARCHAR(4),
    de018_mcc         VARCHAR(4),
    de022_pos_mode    VARCHAR(3),
    de032_acq_id      VARCHAR(11),
    de037_rrn         VARCHAR(12),
    de041_term_id     VARCHAR(8),
    de042_merch_id    VARCHAR(15),
    de043_merch_name  VARCHAR(40),
    de049_currency    VARCHAR(3),
    de052_pin_present BOOLEAN     DEFAULT FALSE,
    -- Response 0110
    de038_auth_code   VARCHAR(6),
    de039_response    VARCHAR(2),
    approved          BOOLEAN,
    -- Metrics
    duration_ms       INTEGER,
    request_hex       TEXT,
    response_hex      TEXT,
    sent_at           TIMESTAMP   DEFAULT NOW()
);

CREATE TABLE acq_reversals (
    id                BIGSERIAL PRIMARY KEY,
    execution_id      BIGINT      REFERENCES executions(id),
    acq_auth_id       BIGINT      REFERENCES acq_authorizations(id),
    -- Request 0400
    de002_pan         VARCHAR(20),
    de003_proc_code   VARCHAR(6),
    de004_amount      BIGINT,
    de007_datetime    VARCHAR(10),
    de011_stan        VARCHAR(6),
    de037_rrn         VARCHAR(12),
    de038_auth_code   VARCHAR(6),
    de039_original    VARCHAR(2),
    de041_term_id     VARCHAR(8),
    de042_merch_id    VARCHAR(15),
    de049_currency    VARCHAR(3),
    de056_orig_data   VARCHAR(40),
    -- Response 0410
    de039_response    VARCHAR(2),
    reversed          BOOLEAN,
    -- Metrics
    duration_ms       INTEGER,
    request_hex       TEXT,
    response_hex      TEXT,
    sent_at           TIMESTAMP   DEFAULT NOW()
);

CREATE TABLE acq_advices (
    id                    BIGSERIAL PRIMARY KEY,
    execution_id          BIGINT      REFERENCES executions(id),
    acq_auth_id           BIGINT      REFERENCES acq_authorizations(id),
    -- Request 0120
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
    -- Response 0130
    de039_advice_response VARCHAR(2),
    accepted              BOOLEAN,
    -- Metrics
    duration_ms           INTEGER,
    request_hex           TEXT,
    response_hex          TEXT,
    sent_at               TIMESTAMP   DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════
-- ISSUER TABLES
-- ═══════════════════════════════════════════════════════════

CREATE TABLE iss_authorizations (
    id                BIGSERIAL PRIMARY KEY,
    -- Request 0100 reçu
    de002_pan         VARCHAR(20),
    de003_proc_code   VARCHAR(6),
    de004_amount      BIGINT,
    de007_datetime    VARCHAR(10),
    de011_stan        VARCHAR(6),
    de012_local_time  VARCHAR(6),
    de013_local_date  VARCHAR(4),
    de018_mcc         VARCHAR(4),
    de022_pos_mode    VARCHAR(3),
    de032_acq_id      VARCHAR(11),
    de037_rrn         VARCHAR(12),
    de041_term_id     VARCHAR(8),
    de042_merch_id    VARCHAR(15),
    de043_merch_name  VARCHAR(40),
    de049_currency    VARCHAR(3),
    de052_pin_present BOOLEAN     DEFAULT FALSE,
    mac_verified      BOOLEAN     DEFAULT FALSE,
    -- Response 0110 envoyé
    de038_auth_code   VARCHAR(6),
    de039_response    VARCHAR(2),
    decision_reason   VARCHAR(100),
    approved          BOOLEAN,
    -- Metrics
    request_hex       TEXT,
    response_hex      TEXT,
    received_at       TIMESTAMP   DEFAULT NOW(),
    responded_at      TIMESTAMP
);

CREATE TABLE iss_reversals (
    id                BIGSERIAL PRIMARY KEY,
    iss_auth_id       BIGINT      REFERENCES iss_authorizations(id),
    -- Request 0400 reçu
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
    -- Response 0410 envoyé
    de039_response    VARCHAR(2),
    reversed          BOOLEAN,
    -- Metrics
    request_hex       TEXT,
    response_hex      TEXT,
    received_at       TIMESTAMP   DEFAULT NOW()
);

CREATE TABLE iss_advices (
    id                    BIGSERIAL PRIMARY KEY,
    iss_auth_id           BIGINT      REFERENCES iss_authorizations(id),
    -- Request 0120 reçu
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
    -- Response 0130 envoyé
    de039_advice_response VARCHAR(2),
    accepted              BOOLEAN,
    -- Metrics
    request_hex           TEXT,
    response_hex          TEXT,
    received_at           TIMESTAMP   DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════
-- IPM TABLES
-- ═══════════════════════════════════════════════════════════

CREATE TABLE ipm_files (
    id                    BIGSERIAL PRIMARY KEY,
    file_name             VARCHAR(100) NOT NULL,
    file_path_binary      VARCHAR(500),
    file_path_ascii       VARCHAR(500),
    file_date             DATE         NOT NULL,
    generation_date       TIMESTAMP    DEFAULT NOW(),
    status                VARCHAR(20)  DEFAULT 'GENERATED',
    nb_transactions       INTEGER      DEFAULT 0,
    total_amount          BIGINT       DEFAULT 0,
    total_amount_currency VARCHAR(3),
    file_id               VARCHAR(50),
    processing_mode       VARCHAR(10)  DEFAULT 'TEST',
    execution_id          BIGINT       REFERENCES executions(id),
    created_at            TIMESTAMP    DEFAULT NOW(),
    created_by            VARCHAR(50)
);

CREATE TABLE ipm_records (
    id               BIGSERIAL PRIMARY KEY,
    ipm_file_id      BIGINT      NOT NULL REFERENCES ipm_files(id) ON DELETE CASCADE,
    acq_auth_id      BIGINT      REFERENCES acq_authorizations(id),
    message_number   INTEGER     NOT NULL,
    record_type      VARCHAR(15) NOT NULL,
    mti              VARCHAR(4)  NOT NULL,
    function_code    VARCHAR(3),
    de002_pan        VARCHAR(20),
    de003_proc_code  VARCHAR(6),
    de004_amount     BIGINT,
    de012_local_dt   VARCHAR(12),
    de022_pos_code   VARCHAR(12),
    de024_func_code  VARCHAR(3),
    de025_reason     VARCHAR(4),
    de026_mcc        VARCHAR(4),
    de032_acq_id     VARCHAR(11),
    de037_rrn        VARCHAR(12),
    de038_auth_code  VARCHAR(6),
    de041_term_id    VARCHAR(8),
    de042_merch_id   VARCHAR(15),
    de043_merch_name VARCHAR(40),
    de049_currency   VARCHAR(3),
    de071_msg_num    VARCHAR(8),
    raw_hex          TEXT,
    raw_ascii        TEXT,
    status           VARCHAR(10) DEFAULT 'OK',
    error_message    VARCHAR(255),
    created_at       TIMESTAMP   DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════
-- INDEX
-- ═══════════════════════════════════════════════════════════

CREATE INDEX idx_acq_auth_exec    ON acq_authorizations(execution_id);
CREATE INDEX idx_acq_auth_de039   ON acq_authorizations(de039_response);
CREATE INDEX idx_acq_auth_pan     ON acq_authorizations(de002_pan);
CREATE INDEX idx_acq_auth_approved ON acq_authorizations(approved);
CREATE INDEX idx_acq_rev_auth     ON acq_reversals(acq_auth_id);
CREATE INDEX idx_acq_adv_auth     ON acq_advices(acq_auth_id);
CREATE INDEX idx_iss_auth_pan     ON iss_authorizations(de002_pan);
CREATE INDEX idx_iss_auth_stan    ON iss_authorizations(de011_stan);
CREATE INDEX idx_iss_auth_rrn     ON iss_authorizations(de037_rrn);
CREATE INDEX idx_iss_auth_approved ON iss_authorizations(approved);
CREATE INDEX idx_iss_rev_auth     ON iss_reversals(iss_auth_id);
CREATE INDEX idx_iss_adv_auth     ON iss_advices(iss_auth_id);
CREATE INDEX idx_ipm_files_date   ON ipm_files(file_date);
CREATE INDEX idx_ipm_files_exec   ON ipm_files(execution_id);
CREATE INDEX idx_ipm_records_file ON ipm_records(ipm_file_id);
CREATE INDEX idx_ipm_records_auth ON ipm_records(acq_auth_id);

-- ═══════════════════════════════════════════════════════════
-- GRANTS
-- ═══════════════════════════════════════════════════════════

GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO scenario_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO scenario_user;

