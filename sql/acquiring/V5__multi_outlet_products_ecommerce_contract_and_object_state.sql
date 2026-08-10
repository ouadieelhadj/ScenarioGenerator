-- Increments 2 and 3: product bindings, products by outlet, dedicated ecommerce
-- contracts and idempotent per-object provisioning state.

ALTER TABLE payment_contract DROP CONSTRAINT IF EXISTS ck_payment_contract_type;
ALTER TABLE payment_contract ADD CONSTRAINT ck_payment_contract_type CHECK (
    contract_type IN ('ISSUING_CARD','ACQUIRING_MERCHANT','ACQUIRING_DEVICE','ACQUIRING_ECOMMERCE'));
ALTER TABLE payment_contract DROP CONSTRAINT IF EXISTS ck_payment_contract_parent;
ALTER TABLE payment_contract ADD CONSTRAINT ck_payment_contract_parent CHECK (
    (contract_type IN ('ACQUIRING_DEVICE','ACQUIRING_ECOMMERCE') AND parent_contract_id IS NOT NULL)
    OR (contract_type NOT IN ('ACQUIRING_DEVICE','ACQUIRING_ECOMMERCE') AND parent_contract_id IS NULL));

CREATE TABLE IF NOT EXISTS acquiring_product_binding (
    id UUID PRIMARY KEY,
    acquirer_id VARCHAR(64) NOT NULL,
    usage VARCHAR(32) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    product_id UUID NOT NULL REFERENCES acceptance_product_version(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    created_by VARCHAR(96) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_binding_usage CHECK (usage IN (
        'MERCHANT_CONTRACT','TPE_DEVICE','ECOMMERCE_CONTRACT')),
    CONSTRAINT ck_product_binding_channel CHECK (channel IN ('TPE','ECOMMERCE','BOTH')),
    CONSTRAINT ck_product_binding_currency CHECK (currency ~ '^[0-9]{3}$'),
    CONSTRAINT ck_product_binding_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE INDEX IF NOT EXISTS ix_product_binding_resolution
    ON acquiring_product_binding(acquirer_id, usage, channel, currency, active);
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_binding_active
    ON acquiring_product_binding(acquirer_id, usage, channel, currency)
    WHERE active AND valid_to IS NULL;

CREATE TABLE IF NOT EXISTS merchant_outlet_product (
    id UUID PRIMARY KEY,
    outlet_id UUID NOT NULL REFERENCES merchant_outlet(id),
    product_id UUID NOT NULL REFERENCES acceptance_product_version(id),
    source_reference VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    CONSTRAINT uk_merchant_outlet_product UNIQUE(outlet_id, product_id)
);

ALTER TABLE acquiring_device_contract_detail
    ADD COLUMN IF NOT EXISTS source_terminal_request_id UUID,
    ADD COLUMN IF NOT EXISTS request_ordinal INTEGER,
    ADD COLUMN IF NOT EXISTS requested_model_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS requested_connectivity_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS requested_option_codes VARCHAR(1000);

ALTER TABLE ecommerce_store ADD COLUMN IF NOT EXISTS outlet_id UUID;
UPDATE ecommerce_store store
SET outlet_id = principal.id
FROM merchant_outlet principal
WHERE store.outlet_id IS NULL
  AND principal.merchant_id = store.merchant_id
  AND principal.active = TRUE
  AND principal.principal = TRUE;

CREATE TABLE IF NOT EXISTS acquiring_migration_anomaly (
    id UUID PRIMARY KEY,
    migration_code VARCHAR(64) NOT NULL,
    object_type VARCHAR(48) NOT NULL,
    object_id UUID NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reconciled_at TIMESTAMPTZ,
    CONSTRAINT uk_acquiring_migration_anomaly UNIQUE(migration_code, object_type, object_id)
);
INSERT INTO acquiring_migration_anomaly(id, migration_code, object_type, object_id, error_code)
SELECT md5('V5:ECOMMERCE_STORE:' || id::text)::uuid,
       'V5_ECOMMERCE_OUTLET', 'ECOMMERCE_STORE', id, 'MISSING_PRINCIPAL_OUTLET'
FROM ecommerce_store
WHERE outlet_id IS NULL
ON CONFLICT (migration_code, object_type, object_id) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ecommerce_store WHERE outlet_id IS NULL) THEN
        ALTER TABLE ecommerce_store ALTER COLUMN outlet_id SET NOT NULL;
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ecommerce_store_outlet') THEN
        ALTER TABLE ecommerce_store ADD CONSTRAINT fk_ecommerce_store_outlet
            FOREIGN KEY (outlet_id) REFERENCES merchant_outlet(id);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_ecommerce_store_outlet ON ecommerce_store(outlet_id);

CREATE TABLE IF NOT EXISTS acquiring_ecommerce_contract_detail (
    contract_id UUID PRIMARY KEY REFERENCES payment_contract(id),
    acquirer_id VARCHAR(64) NOT NULL,
    outlet_id UUID NOT NULL REFERENCES merchant_outlet(id),
    store_id UUID NOT NULL REFERENCES ecommerce_store(id),
    logical_terminal_id VARCHAR(8) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    source_store_request_id UUID NOT NULL,
    CONSTRAINT uk_acquiring_ecommerce_tid UNIQUE(acquirer_id, logical_terminal_id),
    CONSTRAINT ck_acquiring_ecommerce_channel CHECK (channel IN ('ECOMMERCE','BOTH'))
);

CREATE TABLE IF NOT EXISTS provisioning_object_state (
    id UUID PRIMARY KEY,
    object_type VARCHAR(48) NOT NULL,
    object_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    external_reference VARCHAR(128),
    allocated_identifier VARCHAR(32),
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_provisioning_object_key UNIQUE(idempotency_key),
    CONSTRAINT ck_provisioning_object_status CHECK (status IN (
        'PENDING','IN_PROGRESS','PROVISIONED','FAILED_RETRYABLE','FAILED_FINAL')),
    CONSTRAINT ck_provisioning_object_attempts CHECK (attempt_count BETWEEN 0 AND 8)
);
CREATE INDEX IF NOT EXISTS ix_provisioning_object_retry
    ON provisioning_object_state(status, next_attempt_at);
