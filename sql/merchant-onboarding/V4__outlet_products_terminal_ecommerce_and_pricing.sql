-- Increment 2: products/services by outlet, TPE requests, ecommerce stores and pricing snapshots.
-- Additive and replay-safe. No fee or WAY4 technical code is invented here.

CREATE TABLE IF NOT EXISTS onboarding_outlet_product (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES merchant_onboarding_case(id),
    outlet_id UUID NOT NULL REFERENCES onboarding_outlet(id),
    product_id UUID NOT NULL,
    pricing_pack_code VARCHAR(64),
    pricing_pack_version INTEGER,
    pricing_snapshot_json TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_onboarding_outlet_product UNIQUE (outlet_id, product_id),
    CONSTRAINT ck_onboarding_pricing_pack_pair CHECK (
        (pricing_pack_code IS NULL AND pricing_pack_version IS NULL)
        OR (pricing_pack_code IS NOT NULL AND pricing_pack_version > 0))
);

CREATE TABLE IF NOT EXISTS onboarding_terminal_request (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES merchant_onboarding_case(id),
    outlet_id UUID NOT NULL REFERENCES onboarding_outlet(id),
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    model_code VARCHAR(64) NOT NULL,
    connectivity_code VARCHAR(64) NOT NULL,
    option_codes VARCHAR(1000) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    external_reference VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_terminal_request_quantity CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT ck_terminal_request_status CHECK (status IN (
        'REQUESTED','PROVISIONING','PROVISIONED','FAILED_RETRYABLE','FAILED_FINAL','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS ix_terminal_request_case ON onboarding_terminal_request(case_id);
CREATE INDEX IF NOT EXISTS ix_terminal_request_outlet ON onboarding_terminal_request(outlet_id);

CREATE TABLE IF NOT EXISTS onboarding_ecommerce_store_request (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES merchant_onboarding_case(id),
    outlet_id UUID NOT NULL REFERENCES onboarding_outlet(id),
    product_id UUID NOT NULL,
    store_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    allowed_domain VARCHAR(255) NOT NULL,
    return_url VARCHAR(512) NOT NULL,
    notification_url VARCHAR(512) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    capture_mode VARCHAR(32) NOT NULL,
    option_codes VARCHAR(1000) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    external_reference VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_onboarding_store_code UNIQUE (case_id, store_code),
    CONSTRAINT ck_onboarding_store_https CHECK (
        return_url LIKE 'https://%' AND notification_url LIKE 'https://%'),
    CONSTRAINT ck_onboarding_store_status CHECK (status IN (
        'REQUESTED','PROVISIONING','PROVISIONED','FAILED_RETRYABLE','FAILED_FINAL','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS ix_onboarding_store_outlet
    ON onboarding_ecommerce_store_request(outlet_id);

INSERT INTO onboarding_reference_value(category, code, label, active, attributes_json, valid_from)
SELECT seed.category, seed.code, seed.label, seed.active, seed.attributes_json, seed.valid_from
FROM (VALUES
    ('TPE_MODEL', 'STANDARD', 'TPE standard', TRUE, '{}', CURRENT_TIMESTAMP),
    ('TPE_CONNECTIVITY', 'ADSL', 'ADSL', TRUE, '{}', CURRENT_TIMESTAMP),
    ('TPE_CONNECTIVITY', '4G', '4G', TRUE, '{}', CURRENT_TIMESTAMP),
    ('TPE_CONNECTIVITY', '5G', '5G', TRUE, '{}', CURRENT_TIMESTAMP),
    ('TPE_CONNECTIVITY', 'WIFI', 'Wi-Fi', TRUE, '{}', CURRENT_TIMESTAMP),
    ('TPE_OPTION', 'CONTACTLESS', 'Sans contact', TRUE, '{}', CURRENT_TIMESTAMP),
    ('ECOMMERCE_OPTION', 'THREEDS', '3-D Secure', TRUE, '{}', CURRENT_TIMESTAMP),
    ('CAPTURE_MODE', 'IMMEDIATE', 'Capture immediate', TRUE, '{}', CURRENT_TIMESTAMP),
    ('CAPTURE_MODE', 'DEFERRED', 'Capture differee', TRUE, '{}', CURRENT_TIMESTAMP)
) AS seed(category, code, label, active, attributes_json, valid_from)
WHERE NOT EXISTS (
    SELECT 1
    FROM onboarding_reference_value existing
    WHERE existing.category = seed.category
      AND existing.code = seed.code
);
