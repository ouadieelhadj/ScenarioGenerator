-- SID V3.20 - enrichissement des journaux transactionnels pour le futur LIS.
-- Les messages de gestion reseau 1800/1804 ne sont pas concernes.
BEGIN;

ALTER TABLE swam_iss_transactions
    ADD COLUMN IF NOT EXISTS local_transaction_dt VARCHAR(12),
    ADD COLUMN IF NOT EXISTS settlement_date VARCHAR(6),
    ADD COLUMN IF NOT EXISTS conversion_date VARCHAR(4),
    ADD COLUMN IF NOT EXISTS expiry_date VARCHAR(4),
    ADD COLUMN IF NOT EXISTS merchant_category_code VARCHAR(4),
    ADD COLUMN IF NOT EXISTS acquirer_country_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS forwarding_country_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS pos_data_code VARCHAR(12),
    ADD COLUMN IF NOT EXISTS function_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS message_reason_code VARCHAR(4),
    ADD COLUMN IF NOT EXISTS card_sequence_number VARCHAR(3),
    ADD COLUMN IF NOT EXISTS acquirer_institution_id VARCHAR(11),
    ADD COLUMN IF NOT EXISTS forwarding_institution_id VARCHAR(11),
    ADD COLUMN IF NOT EXISTS rrn VARCHAR(12),
    ADD COLUMN IF NOT EXISTS authorization_code VARCHAR(6),
    ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(8),
    ADD COLUMN IF NOT EXISTS merchant_id VARCHAR(15),
    ADD COLUMN IF NOT EXISTS merchant_name_location VARCHAR(40),
    ADD COLUMN IF NOT EXISTS settlement_amount BIGINT,
    ADD COLUMN IF NOT EXISTS billing_amount BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS billing_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS security_control_info VARCHAR(99),
    ADD COLUMN IF NOT EXISTS original_data_elements VARCHAR(35),
    ADD COLUMN IF NOT EXISTS sender_identification VARCHAR(999),
    ADD COLUMN IF NOT EXISTS clearing_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS clearing_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(24);

ALTER TABLE swam_acq_transactions
    ADD COLUMN IF NOT EXISTS local_transaction_dt VARCHAR(12),
    ADD COLUMN IF NOT EXISTS settlement_date VARCHAR(6),
    ADD COLUMN IF NOT EXISTS conversion_date VARCHAR(4),
    ADD COLUMN IF NOT EXISTS expiry_date VARCHAR(4),
    ADD COLUMN IF NOT EXISTS merchant_category_code VARCHAR(4),
    ADD COLUMN IF NOT EXISTS acquirer_country_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS forwarding_country_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS pos_data_code VARCHAR(12),
    ADD COLUMN IF NOT EXISTS function_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS message_reason_code VARCHAR(4),
    ADD COLUMN IF NOT EXISTS card_sequence_number VARCHAR(3),
    ADD COLUMN IF NOT EXISTS acquirer_institution_id VARCHAR(11),
    ADD COLUMN IF NOT EXISTS forwarding_institution_id VARCHAR(11),
    ADD COLUMN IF NOT EXISTS rrn VARCHAR(12),
    ADD COLUMN IF NOT EXISTS authorization_code VARCHAR(6),
    ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(8),
    ADD COLUMN IF NOT EXISTS merchant_id VARCHAR(15),
    ADD COLUMN IF NOT EXISTS merchant_name_location VARCHAR(40),
    ADD COLUMN IF NOT EXISTS settlement_amount BIGINT,
    ADD COLUMN IF NOT EXISTS billing_amount BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS billing_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS security_control_info VARCHAR(99),
    ADD COLUMN IF NOT EXISTS original_data_elements VARCHAR(35),
    ADD COLUMN IF NOT EXISTS sender_identification VARCHAR(999),
    ADD COLUMN IF NOT EXISTS clearing_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS clearing_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(24);

CREATE INDEX IF NOT EXISTS idx_swam_iss_tx_rrn ON swam_iss_transactions(rrn);
CREATE INDEX IF NOT EXISTS idx_swam_iss_tx_clearing
    ON swam_iss_transactions(clearing_eligible, settlement_date);
CREATE INDEX IF NOT EXISTS idx_swam_acq_tx_rrn ON swam_acq_transactions(rrn);
CREATE INDEX IF NOT EXISTS idx_swam_acq_tx_clearing
    ON swam_acq_transactions(clearing_eligible, settlement_date);

COMMIT;
