CREATE TABLE IF NOT EXISTS dmcs_acquirer_clearing_transactions (
    id                          BIGSERIAL PRIMARY KEY,
    business_date               DATE NOT NULL,
    source_type                 VARCHAR(20) NOT NULL,
    direction                   VARCHAR(3) NOT NULL,
    local_authorization_id      BIGINT,
    source_file_id              BIGINT,
    source_message_number       INTEGER,
    parent_transaction_id       BIGINT,
    correlation_key             VARCHAR(80) NOT NULL,
    lifecycle_stage             VARCHAR(24) NOT NULL,
    status                      VARCHAR(24) NOT NULL,
    match_status                VARCHAR(24) NOT NULL,
    mti                         VARCHAR(4) NOT NULL,
    function_code               VARCHAR(3) NOT NULL,
    de002_pan                   VARCHAR(19) NOT NULL,
    masked_pan                  VARCHAR(19) NOT NULL,
    de003_processing_code       VARCHAR(6),
    de004_amount                BIGINT,
    de005_reconciliation_amount BIGINT,
    de009_reconciliation_rate   VARCHAR(8),
    de012_transaction_datetime  VARCHAR(12),
    de014_expiry                VARCHAR(4),
    de022_pos_data_code         VARCHAR(12),
    de025_message_reason_code   VARCHAR(4),
    de026_mcc                   VARCHAR(4),
    de030_original_amounts      VARCHAR(24),
    de031_acquirer_reference    VARCHAR(23),
    de032_acquiring_id          VARCHAR(11),
    de033_forwarding_id         VARCHAR(11),
    de037_rrn                   VARCHAR(12),
    de038_authorization_code    VARCHAR(6),
    de041_terminal_id           VARCHAR(8),
    de042_acceptor_id           VARCHAR(15),
    de043_acceptor_name_location VARCHAR(99),
    de049_currency              VARCHAR(3),
    de050_reconciliation_currency VARCHAR(3),
    de071_message_number        VARCHAR(8),
    de093_destination_id        VARCHAR(11),
    de094_origin_id             VARCHAR(11),
    de095_issuer_reference      VARCHAR(42),
    pds_data                    TEXT,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_dmcs_acq_clearing_local
        UNIQUE (source_type, local_authorization_id, lifecycle_stage),
    CONSTRAINT chk_dmcs_acq_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_dmcs_acq_source CHECK (source_type IN ('LOCAL_AUTH', 'INCOMING_IPM'))
);

CREATE TABLE IF NOT EXISTS dmcs_issuer_clearing_transactions (
    id                          BIGSERIAL PRIMARY KEY,
    business_date               DATE NOT NULL,
    source_type                 VARCHAR(20) NOT NULL,
    direction                   VARCHAR(3) NOT NULL,
    local_authorization_id      BIGINT,
    source_file_id              BIGINT,
    source_message_number       INTEGER,
    parent_transaction_id       BIGINT,
    correlation_key             VARCHAR(80) NOT NULL,
    lifecycle_stage             VARCHAR(24) NOT NULL,
    status                      VARCHAR(24) NOT NULL,
    match_status                VARCHAR(24) NOT NULL,
    mti                         VARCHAR(4) NOT NULL,
    function_code               VARCHAR(3) NOT NULL,
    de002_pan                   VARCHAR(19) NOT NULL,
    masked_pan                  VARCHAR(19) NOT NULL,
    de003_processing_code       VARCHAR(6),
    de004_amount                BIGINT,
    de005_reconciliation_amount BIGINT,
    de009_reconciliation_rate   VARCHAR(8),
    de012_transaction_datetime  VARCHAR(12),
    de014_expiry                VARCHAR(4),
    de022_pos_data_code         VARCHAR(12),
    de025_message_reason_code   VARCHAR(4),
    de026_mcc                   VARCHAR(4),
    de030_original_amounts      VARCHAR(24),
    de031_acquirer_reference    VARCHAR(23),
    de032_acquiring_id          VARCHAR(11),
    de033_forwarding_id         VARCHAR(11),
    de037_rrn                   VARCHAR(12),
    de038_authorization_code    VARCHAR(6),
    de041_terminal_id           VARCHAR(8),
    de042_acceptor_id           VARCHAR(15),
    de043_acceptor_name_location VARCHAR(99),
    de049_currency              VARCHAR(3),
    de050_reconciliation_currency VARCHAR(3),
    de071_message_number        VARCHAR(8),
    de093_destination_id        VARCHAR(11),
    de094_origin_id             VARCHAR(11),
    de095_issuer_reference      VARCHAR(42),
    pds_data                    TEXT,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_dmcs_iss_clearing_local
        UNIQUE (source_type, local_authorization_id, lifecycle_stage),
    CONSTRAINT chk_dmcs_iss_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_dmcs_iss_source CHECK (source_type IN ('LOCAL_AUTH', 'INCOMING_IPM'))
);

CREATE INDEX IF NOT EXISTS idx_dmcs_acq_clearing_eod
    ON dmcs_acquirer_clearing_transactions(business_date, direction, status);
CREATE INDEX IF NOT EXISTS idx_dmcs_acq_clearing_match
    ON dmcs_acquirer_clearing_transactions(correlation_key, lifecycle_stage);
CREATE INDEX IF NOT EXISTS idx_dmcs_iss_clearing_eod
    ON dmcs_issuer_clearing_transactions(business_date, direction, status);
CREATE INDEX IF NOT EXISTS idx_dmcs_iss_clearing_match
    ON dmcs_issuer_clearing_transactions(correlation_key, lifecycle_stage);

ALTER TABLE dmcs_acquirer_clearing_transactions
    ADD COLUMN IF NOT EXISTS parent_transaction_id BIGINT,
    ADD COLUMN IF NOT EXISTS de005_reconciliation_amount BIGINT,
    ADD COLUMN IF NOT EXISTS de009_reconciliation_rate VARCHAR(8),
    ADD COLUMN IF NOT EXISTS de025_message_reason_code VARCHAR(4),
    ADD COLUMN IF NOT EXISTS de030_original_amounts VARCHAR(24),
    ADD COLUMN IF NOT EXISTS de050_reconciliation_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS de095_issuer_reference VARCHAR(42);

ALTER TABLE dmcs_issuer_clearing_transactions
    ADD COLUMN IF NOT EXISTS parent_transaction_id BIGINT,
    ADD COLUMN IF NOT EXISTS de005_reconciliation_amount BIGINT,
    ADD COLUMN IF NOT EXISTS de009_reconciliation_rate VARCHAR(8),
    ADD COLUMN IF NOT EXISTS de025_message_reason_code VARCHAR(4),
    ADD COLUMN IF NOT EXISTS de030_original_amounts VARCHAR(24),
    ADD COLUMN IF NOT EXISTS de050_reconciliation_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS de095_issuer_reference VARCHAR(42);

ALTER TABLE dmcs_acquirer_clearing_transactions OWNER TO dmas_acquirer_user;
ALTER TABLE dmcs_issuer_clearing_transactions OWNER TO dmas_issuer_user;

REVOKE ALL ON dmcs_acquirer_clearing_transactions FROM PUBLIC, dmas_issuer_user;
REVOKE ALL ON dmcs_issuer_clearing_transactions FROM PUBLIC, dmas_acquirer_user;

GRANT SELECT, INSERT, UPDATE, DELETE ON acq_ipm_files, acq_ipm_records
    TO dmas_acquirer_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON iss_ipm_files, iss_ipm_records
    TO dmas_issuer_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ipm_processing_log
    TO dmas_acquirer_user, dmas_issuer_user;
GRANT USAGE, SELECT ON SEQUENCE
    acq_ipm_files_id_seq, acq_ipm_records_id_seq, ipm_processing_log_id_seq
    TO dmas_acquirer_user;
GRANT USAGE, SELECT ON SEQUENCE
    iss_ipm_files_id_seq, iss_ipm_records_id_seq, ipm_processing_log_id_seq
    TO dmas_issuer_user;
