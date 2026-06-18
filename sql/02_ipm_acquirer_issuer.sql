-- COMMIT 1 — Tables acq_ipm_* / iss_ipm_* + ipm_processing_log + flags

CREATE TABLE IF NOT EXISTS acq_ipm_files (
    id                    BIGSERIAL PRIMARY KEY,
    file_name             VARCHAR(100) NOT NULL,
    file_path_binary      VARCHAR(500),
    file_path_ascii       VARCHAR(500),
    file_date             DATE         NOT NULL,
    generation_date       TIMESTAMP    DEFAULT NOW(),
    status                VARCHAR(20)  DEFAULT 'GENERATED',
    direction             VARCHAR(3)   DEFAULT 'OUT',
    nb_transactions       INTEGER      DEFAULT 0,
    total_amount          BIGINT       DEFAULT 0,
    total_amount_currency VARCHAR(3),
    file_id               VARCHAR(50),
    processing_mode       VARCHAR(10)  DEFAULT 'TEST',
    execution_id          BIGINT       REFERENCES executions(id),
    created_at            TIMESTAMP    DEFAULT NOW(),
    created_by            VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_files_date ON acq_ipm_files(file_date);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_files_exec ON acq_ipm_files(execution_id);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_files_dir  ON acq_ipm_files(direction);

CREATE TABLE IF NOT EXISTS acq_ipm_records (
    id                   BIGSERIAL PRIMARY KEY,
    ipm_file_id          BIGINT       NOT NULL REFERENCES acq_ipm_files(id) ON DELETE CASCADE,
    acq_auth_id          BIGINT,
    direction            VARCHAR(3)   DEFAULT 'OUT',
    message_number       INTEGER      NOT NULL,
    record_type          VARCHAR(15)  NOT NULL,
    mti                  VARCHAR(4)   NOT NULL,
    function_code        VARCHAR(3),
    de002_pan            VARCHAR(20),
    de003_proc_code      VARCHAR(6),
    de004_amount         BIGINT,
    de005_amount_recon   BIGINT,
    de012_local_dt       VARCHAR(12),
    de022_pos_code       VARCHAR(12),
    de024_func_code      VARCHAR(3),
    de025_reason         VARCHAR(4),
    de026_mcc            VARCHAR(4),
    de030_orig_amount    BIGINT,
    de031_acq_ref_data   VARCHAR(23),
    de032_acq_id         VARCHAR(11),
    de037_rrn            VARCHAR(12),
    de038_auth_code      VARCHAR(6),
    de041_term_id        VARCHAR(8),
    de042_merch_id       VARCHAR(15),
    de043_merch_name     VARCHAR(40),
    de049_currency       VARCHAR(3),
    de050_currency_recon VARCHAR(3),
    de063_network_data   VARCHAR(50),
    de071_msg_num        VARCHAR(8),
    de072_data_record    VARCHAR(255),
    pds_data             TEXT,
    raw_hex              TEXT,
    raw_ascii            TEXT,
    status               VARCHAR(10)  DEFAULT 'OK',
    error_message        VARCHAR(255),
    created_at           TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_records_file ON acq_ipm_records(ipm_file_id);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_records_auth ON acq_ipm_records(acq_auth_id);

CREATE TABLE IF NOT EXISTS iss_ipm_files (
    id                    BIGSERIAL PRIMARY KEY,
    file_name             VARCHAR(100) NOT NULL,
    file_path_binary      VARCHAR(500),
    file_path_ascii       VARCHAR(500),
    file_date             DATE         NOT NULL,
    generation_date       TIMESTAMP    DEFAULT NOW(),
    status                VARCHAR(20)  DEFAULT 'GENERATED',
    direction             VARCHAR(3)   DEFAULT 'OUT',
    nb_transactions       INTEGER      DEFAULT 0,
    total_amount          BIGINT       DEFAULT 0,
    total_amount_currency VARCHAR(3),
    file_id               VARCHAR(50),
    processing_mode       VARCHAR(10)  DEFAULT 'TEST',
    execution_id          BIGINT       REFERENCES executions(id),
    created_at            TIMESTAMP    DEFAULT NOW(),
    created_by            VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_files_date ON iss_ipm_files(file_date);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_files_exec ON iss_ipm_files(execution_id);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_files_dir  ON iss_ipm_files(direction);

CREATE TABLE IF NOT EXISTS iss_ipm_records (
    id                   BIGSERIAL PRIMARY KEY,
    ipm_file_id          BIGINT       NOT NULL REFERENCES iss_ipm_files(id) ON DELETE CASCADE,
    iss_auth_id          BIGINT,
    direction            VARCHAR(3)   DEFAULT 'OUT',
    message_number       INTEGER      NOT NULL,
    record_type          VARCHAR(15)  NOT NULL,
    mti                  VARCHAR(4)   NOT NULL,
    function_code        VARCHAR(3),
    de002_pan            VARCHAR(20),
    de003_proc_code      VARCHAR(6),
    de004_amount         BIGINT,
    de005_amount_recon   BIGINT,
    de012_local_dt       VARCHAR(12),
    de022_pos_code       VARCHAR(12),
    de024_func_code      VARCHAR(3),
    de025_reason         VARCHAR(4),
    de026_mcc            VARCHAR(4),
    de030_orig_amount    BIGINT,
    de031_acq_ref_data   VARCHAR(23),
    de032_acq_id         VARCHAR(11),
    de037_rrn            VARCHAR(12),
    de038_auth_code      VARCHAR(6),
    de041_term_id        VARCHAR(8),
    de042_merch_id       VARCHAR(15),
    de043_merch_name     VARCHAR(40),
    de049_currency       VARCHAR(3),
    de050_currency_recon VARCHAR(3),
    de063_network_data   VARCHAR(50),
    de071_msg_num        VARCHAR(8),
    de072_data_record    VARCHAR(255),
    de093_dest_id        VARCHAR(11),
    de094_origin_id      VARCHAR(11),
    pds_data             TEXT,
    raw_hex              TEXT,
    raw_ascii            TEXT,
    status               VARCHAR(10)  DEFAULT 'OK',
    error_message        VARCHAR(255),
    created_at           TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_records_file ON iss_ipm_records(ipm_file_id);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_records_auth ON iss_ipm_records(iss_auth_id);

CREATE TABLE IF NOT EXISTS ipm_processing_log (
    id            BIGSERIAL PRIMARY KEY,
    file_id       VARCHAR(50),
    file_name     VARCHAR(100),
    file_path     VARCHAR(500),
    role          VARCHAR(10),
    direction     VARCHAR(3),
    action        VARCHAR(15),
    execution_id  BIGINT,
    record_count  INTEGER,
    checksum      VARCHAR(64),
    status        VARCHAR(15),
    processed_at  TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_ipm_log UNIQUE (file_name, role, direction)
);
CREATE INDEX IF NOT EXISTS idx_ipm_log_checksum ON ipm_processing_log(checksum);
CREATE INDEX IF NOT EXISTS idx_ipm_log_exec     ON ipm_processing_log(execution_id);

ALTER TABLE acq_authorizations
    ADD COLUMN IF NOT EXISTS ipm_generated    BOOLEAN     DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ipm_file_id      BIGINT,
    ADD COLUMN IF NOT EXISTS ipm_file_name    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ipm_generated_at TIMESTAMP;

ALTER TABLE iss_authorizations
    ADD COLUMN IF NOT EXISTS ipm_generated    BOOLEAN     DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ipm_file_id      BIGINT,
    ADD COLUMN IF NOT EXISTS ipm_file_name    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS ipm_generated_at TIMESTAMP;
