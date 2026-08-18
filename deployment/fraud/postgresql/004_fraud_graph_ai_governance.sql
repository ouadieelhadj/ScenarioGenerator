-- Omnichannel graph identities and governed AI policies. Existing card data is preserved.
CREATE TABLE IF NOT EXISTS fraud_monitoring_subject (id uuid PRIMARY KEY,member_id varchar(64) NOT NULL,sector_id varchar(64) NOT NULL,subject_type varchar(32) NOT NULL,subject_hash varchar(64) NOT NULL,status varchar(24) NOT NULL,created_at timestamptz NOT NULL,CONSTRAINT uk_fraud_subject_member_sector_type_hash UNIQUE(member_id,sector_id,subject_type,subject_hash));
INSERT INTO fraud_monitoring_subject(id,member_id,sector_id,subject_type,subject_hash,status,created_at)
SELECT id,member_id,'MONETIQUE','CARD_TOKEN',token_hash,status,created_at FROM fraud_card_profile
ON CONFLICT(member_id,sector_id,subject_type,subject_hash) DO NOTHING;

ALTER TABLE fraud_risk_assessment ADD COLUMN IF NOT EXISTS subject_type varchar(32);
ALTER TABLE fraud_risk_assessment ADD COLUMN IF NOT EXISTS subject_hash varchar(64);
UPDATE fraud_risk_assessment SET subject_type='CARD_TOKEN' WHERE subject_type IS NULL;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='fraud_risk_assessment' AND column_name='token_hash') THEN
        EXECUTE 'UPDATE fraud_risk_assessment SET subject_hash=token_hash WHERE subject_hash IS NULL';
        EXECUTE 'ALTER TABLE fraud_risk_assessment ALTER COLUMN token_hash DROP NOT NULL';
    END IF;
END $$;
ALTER TABLE fraud_risk_assessment ALTER COLUMN subject_type SET NOT NULL;
ALTER TABLE fraud_risk_assessment ALTER COLUMN subject_hash SET NOT NULL;
CREATE INDEX IF NOT EXISTS ix_fraud_assessment_member_subject ON fraud_risk_assessment(member_id,subject_type,subject_hash,created_at DESC);

ALTER TABLE fraud_entity_link ADD COLUMN IF NOT EXISTS sector_id varchar(64);
ALTER TABLE fraud_entity_link ADD COLUMN IF NOT EXISTS subject_type varchar(32);
ALTER TABLE fraud_entity_link ADD COLUMN IF NOT EXISTS channel varchar(32);
UPDATE fraud_entity_link SET sector_id='MONETIQUE' WHERE sector_id IS NULL;
UPDATE fraud_entity_link SET subject_type='CARD_TOKEN' WHERE subject_type IS NULL;
UPDATE fraud_entity_link SET channel='LEGACY' WHERE channel IS NULL;
ALTER TABLE fraud_entity_link ALTER COLUMN sector_id SET NOT NULL;
ALTER TABLE fraud_entity_link ALTER COLUMN subject_type SET NOT NULL;
ALTER TABLE fraud_entity_link ALTER COLUMN channel SET NOT NULL;
ALTER TABLE fraud_entity_link DROP CONSTRAINT IF EXISTS uk_fraud_link_member_subject_entity;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uk_fraud_link_member_sector_subject_entity') THEN
        ALTER TABLE fraud_entity_link ADD CONSTRAINT uk_fraud_link_member_sector_subject_entity UNIQUE(member_id,sector_id,subject_type,subject_hash,entity_type,entity_hash);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_fraud_link_member_sector_entity ON fraud_entity_link(member_id,sector_id,entity_type,entity_hash,last_seen_at DESC);

CREATE TABLE IF NOT EXISTS fraud_graph_policy (id uuid PRIMARY KEY,member_id varchar(64) NOT NULL,sector_id varchar(64) NOT NULL,enabled boolean NOT NULL,cross_sector_enabled boolean NOT NULL,allowed_entity_types varchar(256) NOT NULL,minimum_group_size integer NOT NULL,base_score integer NOT NULL,score_per_additional_subject integer NOT NULL,maximum_score integer NOT NULL,observation_window_minutes integer NOT NULL,minimum_observations integer NOT NULL,maximum_hops integer NOT NULL,updated_at timestamptz NOT NULL,version bigint NOT NULL DEFAULT 0,CONSTRAINT uk_fraud_graph_policy_member_sector UNIQUE(member_id,sector_id));
CREATE TABLE IF NOT EXISTS fraud_ai_policy (id uuid PRIMARY KEY,member_id varchar(64) NOT NULL,sector_id varchar(64) NOT NULL,enabled boolean NOT NULL,governance_mode varchar(16) NOT NULL,champion_model varchar(96) NOT NULL,challenger_model varchar(96),challenger_traffic_percent integer NOT NULL,minimum_precision double precision NOT NULL,minimum_recall double precision NOT NULL,maximum_false_positive_rate double precision NOT NULL,drift_threshold double precision NOT NULL,drift_status varchar(16) NOT NULL,explainability_required boolean NOT NULL,analyst_approval_required boolean NOT NULL,alert_threshold integer NOT NULL,challenge_threshold integer NOT NULL,hold_threshold integer NOT NULL,block_threshold integer NOT NULL,updated_at timestamptz NOT NULL,version bigint NOT NULL DEFAULT 0,CONSTRAINT uk_fraud_ai_policy_member_sector UNIQUE(member_id,sector_id));
