-- Upgrade v1 data to explicit sector isolation. Existing payment records remain MONETIQUE.
ALTER TABLE fraud_risk_assessment ADD COLUMN IF NOT EXISTS sector_id varchar(64);
UPDATE fraud_risk_assessment SET sector_id='MONETIQUE' WHERE sector_id IS NULL;
ALTER TABLE fraud_risk_assessment ALTER COLUMN sector_id SET NOT NULL;

ALTER TABLE fraud_feature_snapshot ADD COLUMN IF NOT EXISTS sector_id varchar(64);
UPDATE fraud_feature_snapshot SET sector_id='MONETIQUE' WHERE sector_id IS NULL;
ALTER TABLE fraud_feature_snapshot ALTER COLUMN sector_id SET NOT NULL;

ALTER TABLE fraud_event_outbox ADD COLUMN IF NOT EXISTS sector_id varchar(64);
UPDATE fraud_event_outbox SET sector_id='MONETIQUE' WHERE sector_id IS NULL;
ALTER TABLE fraud_event_outbox ALTER COLUMN sector_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_fraud_assessment_member_sector_created
    ON fraud_risk_assessment(member_id,sector_id,created_at DESC);
CREATE INDEX IF NOT EXISTS ix_fraud_feature_member_sector
    ON fraud_feature_snapshot(member_id,sector_id);
CREATE INDEX IF NOT EXISTS ix_fraud_outbox_member_sector
    ON fraud_event_outbox(member_id,sector_id,status,next_attempt_at);
