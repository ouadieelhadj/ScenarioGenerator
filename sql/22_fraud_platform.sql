-- FuturPayment Fraud Monitoring - schéma PostgreSQL multi-membre.
-- Aucune donnée PAN/PIN/CVV n'est stockée. Les références métier sensibles sont hachées.
CREATE TABLE IF NOT EXISTS fraud_card_profile (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL, token_hash VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL, country VARCHAR(3) NOT NULL,
    customer_reference_hash VARCHAR(64), status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_fraud_card_member_token UNIQUE (member_id, token_hash)
);
CREATE TABLE IF NOT EXISTS fraud_risk_assessment (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL,
    transaction_reference VARCHAR(128) NOT NULL, token_hash VARCHAR(64) NOT NULL,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 1000), band VARCHAR(16) NOT NULL,
    recommended_action VARCHAR(32) NOT NULL, enforced_action VARCHAR(32) NOT NULL,
    model_version VARCHAR(64) NOT NULL, reasons_json VARCHAR(4000) NOT NULL,
    collective_group_size INTEGER NOT NULL DEFAULT 1,
    collective_risk_score INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_fraud_assessment_member_tx UNIQUE (member_id, transaction_reference),
    CONSTRAINT ck_fraud_decision_action CHECK (enforced_action IN ('ALLOW','ALERT','CHALLENGE','HOLD','BLOCK'))
);
CREATE TABLE IF NOT EXISTS fraud_alert (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL,
    assessment_id UUID NOT NULL UNIQUE REFERENCES fraud_risk_assessment(id),
    transaction_reference VARCHAR(128) NOT NULL, score INTEGER NOT NULL,
    band VARCHAR(16) NOT NULL, status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_fraud_alert_member_created ON fraud_alert(member_id, created_at DESC);
CREATE TABLE IF NOT EXISTS fraud_feedback (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL,
    alert_id UUID NOT NULL REFERENCES fraud_alert(id), outcome VARCHAR(24) NOT NULL,
    comment VARCHAR(500), created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_fraud_feedback_member_alert ON fraud_feedback(member_id, alert_id);
CREATE TABLE IF NOT EXISTS fraud_case (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL,
    alert_id UUID NOT NULL REFERENCES fraud_alert(id), title VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_fraud_case_member_alert UNIQUE(member_id, alert_id)
);
CREATE TABLE IF NOT EXISTS fraud_control_candidate (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL, name VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL, precision_value DOUBLE PRECISION NOT NULL,
    recall_value DOUBLE PRECISION NOT NULL, false_positive_rate DOUBLE PRECISION NOT NULL,
    governance_decision VARCHAR(32) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS fraud_threat_signal (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL,
    indicator_type VARCHAR(64) NOT NULL, indicator_hash VARCHAR(64) NOT NULL,
    severity INTEGER NOT NULL CHECK (severity BETWEEN 1 AND 100),
    source VARCHAR(64) NOT NULL, expires_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_fraud_signal_member_hash UNIQUE(member_id, indicator_type, indicator_hash)
);
CREATE TABLE IF NOT EXISTS fraud_entity_link (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL, subject_hash VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL, entity_hash VARCHAR(64) NOT NULL,
    observation_count BIGINT NOT NULL, first_seen_at TIMESTAMPTZ NOT NULL, last_seen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_fraud_link_member_subject_entity UNIQUE(member_id, subject_hash, entity_type, entity_hash)
);
CREATE INDEX IF NOT EXISTS ix_fraud_link_entity ON fraud_entity_link(member_id, entity_type, entity_hash);
CREATE TABLE IF NOT EXISTS fraud_feature_snapshot (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL, transaction_reference VARCHAR(128) NOT NULL,
    feature_version VARCHAR(64) NOT NULL, features_json VARCHAR(4000) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_fraud_feature_member_tx UNIQUE(member_id, transaction_reference)
);
CREATE TABLE IF NOT EXISTS fraud_decision_policy (
    id UUID PRIMARY KEY, member_id VARCHAR(64) NOT NULL, mode VARCHAR(24) NOT NULL,
    challenge_enabled BOOLEAN NOT NULL, hold_enabled BOOLEAN NOT NULL, block_enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL, CONSTRAINT uk_fraud_policy_member UNIQUE(member_id),
    CONSTRAINT ck_fraud_policy_mode CHECK(mode IN ('ALERT_ONLY','ACTIVE_DECISION'))
);

-- Mise à niveau idempotente de la première révision du schéma (CHAR -> VARCHAR attendu par JPA).
ALTER TABLE fraud_card_profile ALTER COLUMN token_hash TYPE VARCHAR(64);
ALTER TABLE fraud_card_profile ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE fraud_card_profile ALTER COLUMN country TYPE VARCHAR(3);
ALTER TABLE fraud_card_profile ALTER COLUMN customer_reference_hash TYPE VARCHAR(64);
ALTER TABLE fraud_risk_assessment ALTER COLUMN token_hash TYPE VARCHAR(64);
ALTER TABLE fraud_risk_assessment ADD COLUMN IF NOT EXISTS collective_group_size INTEGER NOT NULL DEFAULT 1;
ALTER TABLE fraud_risk_assessment ADD COLUMN IF NOT EXISTS collective_risk_score INTEGER NOT NULL DEFAULT 0;
ALTER TABLE fraud_risk_assessment DROP CONSTRAINT IF EXISTS ck_fraud_alert_only;
DO $$ BEGIN
    ALTER TABLE fraud_risk_assessment ADD CONSTRAINT ck_fraud_decision_action CHECK (enforced_action IN ('ALLOW','ALERT','CHALLENGE','HOLD','BLOCK'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Isolation défensive PostgreSQL. Le service doit exécuter SET LOCAL app.member_id
-- lorsqu'une politique RLS est activée en production par l'exploitant.
ALTER TABLE fraud_card_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_risk_assessment ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_alert ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_case ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_control_candidate ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_threat_signal ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_entity_link ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_feature_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_decision_policy ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
    CREATE POLICY fraud_card_member_policy ON fraud_card_profile USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    CREATE POLICY fraud_assessment_member_policy ON fraud_risk_assessment USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    CREATE POLICY fraud_alert_member_policy ON fraud_alert USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    CREATE POLICY fraud_feedback_member_policy ON fraud_feedback USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    CREATE POLICY fraud_case_member_policy ON fraud_case USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    CREATE POLICY fraud_control_member_policy ON fraud_control_candidate USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    CREATE POLICY fraud_signal_member_policy ON fraud_threat_signal USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    CREATE POLICY fraud_link_member_policy ON fraud_entity_link USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    CREATE POLICY fraud_feature_member_policy ON fraud_feature_snapshot USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    CREATE POLICY fraud_policy_member_policy ON fraud_decision_policy USING (member_id = current_setting('app.member_id', true));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
