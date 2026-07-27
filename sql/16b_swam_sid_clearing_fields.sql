-- SID journal enrichment required by LIS 4.13 EOD extraction.
\connect scenariogenerator

DO $$
DECLARE table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['swam_acq_transactions','swam_iss_transactions']
    LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS local_transaction_dt VARCHAR(12)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS settlement_date VARCHAR(6)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS conversion_date VARCHAR(4)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS expiry_date VARCHAR(4)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS merchant_category_code VARCHAR(4)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS acquirer_country_code VARCHAR(3)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS forwarding_country_code VARCHAR(3)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS pos_data_code VARCHAR(12)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS function_code VARCHAR(3)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS message_reason_code VARCHAR(4)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS card_sequence_number VARCHAR(3)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS acquirer_institution_id VARCHAR(11)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS forwarding_institution_id VARCHAR(11)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS rrn VARCHAR(12)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS authorization_code VARCHAR(6)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(8)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS merchant_id VARCHAR(15)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS merchant_name_location VARCHAR(40)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS settlement_amount BIGINT', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS billing_amount BIGINT', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS settlement_currency VARCHAR(3)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS billing_currency VARCHAR(3)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS security_control_info VARCHAR(99)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS original_data_elements VARCHAR(35)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS sender_identification VARCHAR(999)', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS clearing_eligible BOOLEAN NOT NULL DEFAULT FALSE', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS clearing_amount BIGINT', table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(24)', table_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_clearing_eod ON %I(clearing_eligible, created_at)', table_name, table_name);
    END LOOP;
END
$$;
