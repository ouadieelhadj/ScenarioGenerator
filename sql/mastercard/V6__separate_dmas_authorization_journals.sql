-- Journaux d'autorisation qui constituent les sources EOD du clearing DMC.
-- Chaque application d'autorisation possede sa table. Les modules DMCS
-- disposent uniquement d'un droit de lecture sur le journal de leur cote.

CREATE TABLE IF NOT EXISTS mc_dmas_member_transactions (
    id                              BIGSERIAL PRIMARY KEY,
    interface_id                    VARCHAR(32),
    bank_code                       VARCHAR(6) NOT NULL,
    mti_request                     VARCHAR(4) NOT NULL,
    mti_response                    VARCHAR(4),
    pan                             VARCHAR(19) NOT NULL,
    masked_pan                      VARCHAR(19) NOT NULL,
    de003_processing_code           VARCHAR(6),
    de004_amount                    BIGINT,
    de007_transmission_datetime     VARCHAR(10) NOT NULL,
    de011_stan                      VARCHAR(6) NOT NULL,
    de012_local_time                VARCHAR(6),
    de013_local_date                VARCHAR(4),
    de014_expiry                    VARCHAR(4),
    de018_mcc                       VARCHAR(4),
    de022_pos_entry_mode            VARCHAR(3),
    de023_card_sequence             VARCHAR(3),
    de032_acquiring_id              VARCHAR(11),
    de033_forwarding_id             VARCHAR(11),
    de037_rrn                       VARCHAR(12),
    de038_authorization_code        VARCHAR(6),
    de039_response_code             VARCHAR(2),
    de041_terminal_id               VARCHAR(8),
    de042_acceptor_id               VARCHAR(15),
    de043_acceptor_name_location    VARCHAR(99),
    de048_additional_data           VARCHAR(999),
    de049_currency                  VARCHAR(3),
    de055_icc_data                  TEXT,
    de061_pos_data                  VARCHAR(128),
    approved                        BOOLEAN NOT NULL DEFAULT FALSE,
    clearing_eligible               BOOLEAN NOT NULL DEFAULT FALSE,
    clearing_extracted_at           TIMESTAMP,
    reversed                        BOOLEAN NOT NULL DEFAULT FALSE,
    reversed_at                     TIMESTAMP,
    request_at                      TIMESTAMP NOT NULL,
    response_at                     TIMESTAMP,
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_mc_dmas_member_tx_network
        UNIQUE (bank_code, de011_stan, de007_transmission_datetime)
);

CREATE TABLE IF NOT EXISTS mc_dmas_issuer_transactions (
    id                              BIGSERIAL PRIMARY KEY,
    interface_id                    VARCHAR(32),
    bank_code                       VARCHAR(6) NOT NULL,
    mti_request                     VARCHAR(4) NOT NULL,
    mti_response                    VARCHAR(4),
    pan                             VARCHAR(19) NOT NULL,
    masked_pan                      VARCHAR(19) NOT NULL,
    de003_processing_code           VARCHAR(6),
    de004_amount                    BIGINT,
    de007_transmission_datetime     VARCHAR(10) NOT NULL,
    de011_stan                      VARCHAR(6) NOT NULL,
    de012_local_time                VARCHAR(6),
    de013_local_date                VARCHAR(4),
    de014_expiry                    VARCHAR(4),
    de018_mcc                       VARCHAR(4),
    de022_pos_entry_mode            VARCHAR(3),
    de023_card_sequence             VARCHAR(3),
    de032_acquiring_id              VARCHAR(11),
    de033_forwarding_id             VARCHAR(11),
    de037_rrn                       VARCHAR(12),
    de038_authorization_code        VARCHAR(6),
    de039_response_code             VARCHAR(2),
    de041_terminal_id               VARCHAR(8),
    de042_acceptor_id               VARCHAR(15),
    de043_acceptor_name_location    VARCHAR(99),
    de048_additional_data           VARCHAR(999),
    de049_currency                  VARCHAR(3),
    de055_icc_data                  TEXT,
    de061_pos_data                  VARCHAR(128),
    approved                        BOOLEAN NOT NULL DEFAULT FALSE,
    clearing_eligible               BOOLEAN NOT NULL DEFAULT FALSE,
    clearing_extracted_at           TIMESTAMP,
    reversed                        BOOLEAN NOT NULL DEFAULT FALSE,
    reversed_at                     TIMESTAMP,
    request_at                      TIMESTAMP NOT NULL,
    response_at                     TIMESTAMP,
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_mc_dmas_issuer_tx_network
        UNIQUE (bank_code, de011_stan, de007_transmission_datetime)
);

CREATE INDEX IF NOT EXISTS idx_mc_dmas_member_tx_eod
    ON mc_dmas_member_transactions(clearing_eligible, clearing_extracted_at);
CREATE INDEX IF NOT EXISTS idx_mc_dmas_member_tx_rrn
    ON mc_dmas_member_transactions(de037_rrn);
CREATE INDEX IF NOT EXISTS idx_mc_dmas_issuer_tx_eod
    ON mc_dmas_issuer_transactions(clearing_eligible, clearing_extracted_at);
CREATE INDEX IF NOT EXISTS idx_mc_dmas_issuer_tx_rrn
    ON mc_dmas_issuer_transactions(de037_rrn);

ALTER TABLE mc_dmas_member_transactions OWNER TO mc_dmas_member;
ALTER TABLE mc_dmas_issuer_transactions OWNER TO mc_dmas_mastercard;

REVOKE ALL ON mc_dmas_member_transactions FROM PUBLIC, mc_dmas_mastercard, dmas_issuer_user;
REVOKE ALL ON mc_dmas_issuer_transactions FROM PUBLIC, mc_dmas_member, dmas_acquirer_user;

GRANT SELECT ON mc_dmas_member_transactions TO dmas_acquirer_user;
GRANT SELECT ON mc_dmas_issuer_transactions TO dmas_issuer_user;
