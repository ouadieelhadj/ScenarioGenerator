ALTER TABLE merchant_onboarding_case
    ADD COLUMN IF NOT EXISTS provisioning_destination VARCHAR(16);

-- Historical dossiers keep their actual pre-existing routing behaviour.
UPDATE merchant_onboarding_case
   SET provisioning_destination = 'FUTURPAYMENT'
 WHERE provisioning_destination IS NULL;

ALTER TABLE merchant_onboarding_case
    DROP CONSTRAINT IF EXISTS ck_merchant_onboarding_destination;
ALTER TABLE merchant_onboarding_case
    ADD CONSTRAINT ck_merchant_onboarding_destination CHECK
        (provisioning_destination IN ('FUTURPAYMENT','WAY4','BOTH'));

CREATE INDEX IF NOT EXISTS ix_merchant_onboarding_destination
    ON merchant_onboarding_case(provisioning_destination, status);

ALTER TABLE onboarding_way4_export_state
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS last_error_message VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS last_failure_retryable BOOLEAN,
    ADD COLUMN IF NOT EXISTS failed_at TIMESTAMPTZ;
