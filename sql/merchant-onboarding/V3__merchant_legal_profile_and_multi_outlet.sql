-- Increment 1: MER-001..MER-007, ADR-001, PDV-001..PDV-004, REF-001/002, MIG-001/002.
-- Additive migration: legacy columns and the public v1 contract remain available.

ALTER TABLE merchant_onboarding_case
    ADD COLUMN IF NOT EXISTS merchant_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS organization_legal_nature VARCHAR(24),
    ADD COLUMN IF NOT EXISTS tax_identifier VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ice VARCHAR(64),
    ADD COLUMN IF NOT EXISTS legal_form VARCHAR(96),
    ADD COLUMN IF NOT EXISTS business_activity VARCHAR(255),
    ADD COLUMN IF NOT EXISTS association_purpose VARCHAR(500),
    ADD COLUMN IF NOT EXISTS primary_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS primary_email VARCHAR(254),
    ADD COLUMN IF NOT EXISTS headquarters_address_line1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS headquarters_address_line2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS headquarters_district VARCHAR(120),
    ADD COLUMN IF NOT EXISTS headquarters_city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS headquarters_region VARCHAR(120),
    ADD COLUMN IF NOT EXISTS headquarters_postal_code VARCHAR(24),
    ADD COLUMN IF NOT EXISTS representative_title VARCHAR(32),
    ADD COLUMN IF NOT EXISTS representative_first_name VARCHAR(96),
    ADD COLUMN IF NOT EXISTS representative_last_name VARCHAR(96),
    ADD COLUMN IF NOT EXISTS representative_birth_date DATE,
    ADD COLUMN IF NOT EXISTS representative_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS representative_email VARCHAR(254),
    ADD COLUMN IF NOT EXISTS representative_id_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS representative_id_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS representative_residence_country VARCHAR(2),
    ADD COLUMN IF NOT EXISTS representative_nationality VARCHAR(2),
    ADD COLUMN IF NOT EXISTS rib VARCHAR(24);

CREATE TABLE IF NOT EXISTS onboarding_outlet (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES merchant_onboarding_case(id),
    outlet_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    principal BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    district VARCHAR(120),
    city VARCHAR(120) NOT NULL,
    region VARCHAR(120),
    postal_code VARCHAR(24),
    country VARCHAR(2) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    responsible_title VARCHAR(32),
    responsible_first_name VARCHAR(96) NOT NULL,
    responsible_last_name VARCHAR(96) NOT NULL,
    responsible_birth_date DATE,
    responsible_phone VARCHAR(32) NOT NULL,
    responsible_email VARCHAR(254) NOT NULL,
    responsible_id_type VARCHAR(32),
    responsible_id_number VARCHAR(64),
    responsible_residence_country VARCHAR(2),
    responsible_nationality VARCHAR(2),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_onboarding_outlet_code UNIQUE(case_id, outlet_code)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_onboarding_outlet_active_principal
    ON onboarding_outlet(case_id) WHERE active AND principal;

CREATE TABLE IF NOT EXISTS onboarding_beneficial_owner (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES merchant_onboarding_case(id),
    first_name VARCHAR(96) NOT NULL,
    last_name VARCHAR(96) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_onboarding_beneficial_owner_case
    ON onboarding_beneficial_owner(case_id, active);

CREATE TABLE IF NOT EXISTS onboarding_reference_value (
    category VARCHAR(32) NOT NULL,
    code VARCHAR(64) NOT NULL,
    label VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL,
    attributes_json TEXT,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    PRIMARY KEY(category, code)
);

INSERT INTO onboarding_reference_value(category, code, label, active, valid_from) VALUES
    ('MERCHANT_TYPE','PP','Personne physique',true,CURRENT_TIMESTAMP),
    ('MERCHANT_TYPE','PM','Personne morale',true,CURRENT_TIMESTAMP),
    ('MERCHANT_TYPE','AE','Auto-entrepreneur',true,CURRENT_TIMESTAMP),
    ('MERCHANT_TYPE','ASSOCIATION_FOUNDATION','Association ou fondation',true,CURRENT_TIMESTAMP),
    ('LEGAL_NATURE','ASSOCIATION','Association',true,CURRENT_TIMESTAMP),
    ('LEGAL_NATURE','FOUNDATION','Fondation',true,CURRENT_TIMESTAMP),
    ('COUNTRY','MA','Maroc',true,CURRENT_TIMESTAMP),
    ('MCC','5411','Epiceries et supermarches',true,CURRENT_TIMESTAMP)
ON CONFLICT(category, code) DO NOTHING;

CREATE TABLE IF NOT EXISTS onboarding_field_rule (
    merchant_type VARCHAR(32) NOT NULL,
    field_path VARCHAR(160) NOT NULL,
    required BOOLEAN NOT NULL,
    max_length INTEGER,
    active BOOLEAN NOT NULL,
    PRIMARY KEY(merchant_type, field_path)
);

INSERT INTO onboarding_field_rule(merchant_type, field_path, required, max_length, active) VALUES
    ('PP','legalName',true,160,true),
    ('PP','primaryPhone',true,32,true),
    ('PP','primaryEmail',true,254,true),
    ('PP','rib',true,24,true),
    ('PM','legalName',true,160,true),
    ('PM','ice',true,64,true),
    ('PM','businessActivity',true,255,true),
    ('PM','primaryPhone',true,32,true),
    ('PM','primaryEmail',true,254,true),
    ('PM','rib',true,24,true),
    ('AE','legalName',true,160,true),
    ('AE','businessActivity',true,255,true),
    ('AE','primaryPhone',true,32,true),
    ('AE','primaryEmail',true,254,true),
    ('AE','rib',true,24,true),
    ('ASSOCIATION_FOUNDATION','legalName',true,160,true),
    ('ASSOCIATION_FOUNDATION','organizationLegalNature',true,24,true),
    ('ASSOCIATION_FOUNDATION','associationPurpose',true,500,true),
    ('ASSOCIATION_FOUNDATION','primaryPhone',true,32,true),
    ('ASSOCIATION_FOUNDATION','primaryEmail',true,254,true),
    ('ASSOCIATION_FOUNDATION','rib',true,24,true)
ON CONFLICT(merchant_type, field_path) DO NOTHING;

CREATE TABLE IF NOT EXISTS migration_run (
    id UUID PRIMARY KEY,
    migration_code VARCHAR(96) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    source_count INTEGER NOT NULL DEFAULT 0,
    created_count INTEGER NOT NULL DEFAULT 0,
    ignored_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL
);

CREATE TABLE IF NOT EXISTS legacy_outlet_migration (
    case_id UUID PRIMARY KEY REFERENCES merchant_onboarding_case(id),
    outlet_id UUID NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    run_id UUID NOT NULL REFERENCES migration_run(id),
    status VARCHAR(24) NOT NULL,
    migrated_at TIMESTAMPTZ,
    error_code VARCHAR(96)
);

DO $$
DECLARE
    current_run UUID := (substr(md5(random()::text || clock_timestamp()::text),1,8) || '-' ||
        substr(md5(random()::text),1,4) || '-4' || substr(md5(random()::text),1,3) || '-a' ||
        substr(md5(random()::text),1,3) || '-' || substr(md5(random()::text),1,12))::uuid;
    source_rows INTEGER;
    created_rows INTEGER;
    ignored_rows INTEGER;
    error_rows INTEGER;
BEGIN
    SELECT count(*) INTO source_rows FROM merchant_onboarding_case WHERE outlet_code IS NOT NULL;
    INSERT INTO migration_run(id, migration_code, started_at, source_count, status)
        VALUES(current_run, 'MIG-001-LEGACY-OUTLET', CURRENT_TIMESTAMP, source_rows, 'RUNNING');

    INSERT INTO legacy_outlet_migration(case_id, outlet_id, source_hash, run_id, status, error_code)
    SELECT c.id,
        (substr(md5(random()::text || c.id::text),1,8) || '-' || substr(md5(random()::text),1,4) ||
         '-4' || substr(md5(random()::text),1,3) || '-a' || substr(md5(random()::text),1,3) || '-' ||
         substr(md5(random()::text),1,12))::uuid,
        md5(concat_ws('|',c.outlet_code,c.outlet_name,c.outlet_address,c.country)),
        current_run,
        CASE WHEN c.outlet_name IS NULL OR c.outlet_address IS NULL OR c.country IS NULL
             THEN 'ERROR' ELSE 'READY' END,
        CASE WHEN c.outlet_name IS NULL OR c.outlet_address IS NULL OR c.country IS NULL
             THEN 'LEGACY_OUTLET_INCOMPLETE' ELSE NULL END
    FROM merchant_onboarding_case c
    WHERE c.outlet_code IS NOT NULL
    ON CONFLICT(case_id) DO NOTHING;

    INSERT INTO onboarding_outlet(id, case_id, outlet_code, name, principal, active,
        address_line1, city, country, contact_phone, contact_email,
        responsible_first_name, responsible_last_name, responsible_phone, responsible_email,
        created_at, updated_at)
    SELECT m.outlet_id, c.id, c.outlet_code, c.outlet_name, true, true,
        c.outlet_address, 'LEGACY', c.country, 'LEGACY', 'legacy@invalid.local',
        'LEGACY', 'LEGACY', 'LEGACY', 'legacy@invalid.local', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM legacy_outlet_migration m
    JOIN merchant_onboarding_case c ON c.id = m.case_id
    WHERE m.status = 'READY'
    ON CONFLICT(id) DO NOTHING;
    GET DIAGNOSTICS created_rows = ROW_COUNT;

    UPDATE legacy_outlet_migration m SET status='MIGRATED', migrated_at=CURRENT_TIMESTAMP
    WHERE m.status='READY' AND EXISTS(SELECT 1 FROM onboarding_outlet o WHERE o.id=m.outlet_id);
    SELECT count(*) INTO error_rows FROM legacy_outlet_migration WHERE run_id=current_run AND status='ERROR';
    ignored_rows := source_rows - created_rows - error_rows;
    UPDATE migration_run SET completed_at=CURRENT_TIMESTAMP, created_count=created_rows,
        ignored_count=GREATEST(ignored_rows,0), error_count=error_rows,
        status=CASE WHEN error_rows=0 THEN 'COMPLETED' ELSE 'COMPLETED_WITH_ERRORS' END
    WHERE id=current_run;
END $$;
