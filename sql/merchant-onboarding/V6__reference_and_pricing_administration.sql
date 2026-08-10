ALTER TABLE onboarding_reference_value
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE onboarding_reference_value
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(96);
ALTER TABLE onboarding_reference_value
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

UPDATE onboarding_reference_value
   SET updated_at = COALESCE(updated_at, valid_from),
       updated_by = COALESCE(updated_by, 'MIGRATION')
 WHERE updated_at IS NULL OR updated_by IS NULL;

ALTER TABLE onboarding_reference_value ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE onboarding_reference_value ALTER COLUMN updated_by SET NOT NULL;

CREATE TABLE IF NOT EXISTS onboarding_reference_audit (
    id UUID PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    code VARCHAR(64) NOT NULL,
    action VARCHAR(24) NOT NULL,
    before_json TEXT,
    after_json TEXT,
    changed_by VARCHAR(96) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_reference_audit_value
    ON onboarding_reference_audit(category, code, changed_at);

CREATE TABLE IF NOT EXISTS onboarding_pricing_pack (
    pack_code VARCHAR(64) PRIMARY KEY,
    label VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by VARCHAR(96) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_pricing_pack_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED'))
);

CREATE TABLE IF NOT EXISTS onboarding_pricing_pack_version (
    id UUID PRIMARY KEY,
    pack_code VARCHAR(64) NOT NULL REFERENCES onboarding_pricing_pack(pack_code),
    version_number INTEGER NOT NULL,
    terms_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    created_by VARCHAR(96) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    activated_by VARCHAR(96),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_pricing_pack_version UNIQUE(pack_code, version_number),
    CONSTRAINT ck_pricing_version_number CHECK (version_number > 0),
    CONSTRAINT ck_pricing_version_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pricing_pack_single_active
    ON onboarding_pricing_pack_version(pack_code) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS onboarding_tariff_deviation (
    id UUID PRIMARY KEY,
    outlet_product_id UUID NOT NULL REFERENCES onboarding_outlet_product(id),
    pack_code VARCHAR(64) NOT NULL,
    pack_version INTEGER NOT NULL,
    before_json TEXT NOT NULL,
    after_json TEXT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(96) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    decided_by VARCHAR(96),
    decided_at TIMESTAMPTZ,
    decision_reason VARCHAR(1000),
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tariff_deviation_pack FOREIGN KEY(pack_code, pack_version)
        REFERENCES onboarding_pricing_pack_version(pack_code, version_number),
    CONSTRAINT ck_tariff_deviation_status CHECK
        (status IN ('PENDING_APPROVAL','APPROVED','REJECTED')),
    CONSTRAINT ck_tariff_deviation_checker CHECK
        (decided_by IS NULL OR decided_by <> requested_by)
);
CREATE INDEX IF NOT EXISTS ix_tariff_deviation_product
    ON onboarding_tariff_deviation(outlet_product_id, requested_at);
