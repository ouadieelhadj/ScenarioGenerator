-- Increment 1 additive evolution for FuturPayment Acquiring.
-- Existing v1 creation/provisioning remains valid.

ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS merchant_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS organization_legal_nature VARCHAR(24);

CREATE TABLE IF NOT EXISTS merchant_legal_profile (
    merchant_id UUID PRIMARY KEY REFERENCES merchant(id),
    tax_identifier VARCHAR(64),
    ice VARCHAR(64),
    legal_form VARCHAR(96),
    business_activity VARCHAR(255),
    association_purpose VARCHAR(500),
    primary_phone VARCHAR(32),
    primary_email VARCHAR(254),
    rib VARCHAR(24),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    district VARCHAR(120),
    city VARCHAR(120),
    region VARCHAR(120),
    postal_code VARCHAR(24),
    country VARCHAR(2),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS merchant_representative (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchant(id),
    title VARCHAR(32),
    first_name VARCHAR(96) NOT NULL,
    last_name VARCHAR(96) NOT NULL,
    birth_date DATE,
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    id_type VARCHAR(32) NOT NULL,
    id_number VARCHAR(64) NOT NULL,
    residence_country VARCHAR(2) NOT NULL,
    nationality VARCHAR(2) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS merchant_beneficial_owner (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchant(id),
    first_name VARCHAR(96) NOT NULL,
    last_name VARCHAR(96) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE merchant_outlet
    ADD COLUMN IF NOT EXISTS principal BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS district VARCHAR(120),
    ADD COLUMN IF NOT EXISTS city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS region VARCHAR(120),
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(24),
    ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(254),
    ADD COLUMN IF NOT EXISTS responsible_title VARCHAR(32),
    ADD COLUMN IF NOT EXISTS responsible_first_name VARCHAR(96),
    ADD COLUMN IF NOT EXISTS responsible_last_name VARCHAR(96),
    ADD COLUMN IF NOT EXISTS responsible_birth_date DATE,
    ADD COLUMN IF NOT EXISTS responsible_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS responsible_email VARCHAR(254),
    ADD COLUMN IF NOT EXISTS responsible_id_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS responsible_id_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS responsible_residence_country VARCHAR(2),
    ADD COLUMN IF NOT EXISTS responsible_nationality VARCHAR(2),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY merchant_id ORDER BY created_at, id) AS rn
    FROM merchant_outlet WHERE active
)
UPDATE merchant_outlet o SET principal=(r.rn=1), city=COALESCE(o.city,'LEGACY'),
    updated_at=COALESCE(o.updated_at,o.created_at)
FROM ranked r WHERE r.id=o.id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_outlet_active_principal
    ON merchant_outlet(merchant_id) WHERE active AND principal;

CREATE INDEX IF NOT EXISTS idx_merchant_representative_active
    ON merchant_representative(merchant_id, active);
CREATE INDEX IF NOT EXISTS idx_merchant_beneficial_owner_active
    ON merchant_beneficial_owner(merchant_id, active);
