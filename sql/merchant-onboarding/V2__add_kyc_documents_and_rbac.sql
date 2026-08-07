-- KYC, metadonnees documentaires et permissions du portail commercant.

ALTER TABLE merchant_onboarding_case
    ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS kyc_submitted_by VARCHAR(96),
    ADD COLUMN IF NOT EXISTS kyc_reviewed_by VARCHAR(96),
    ADD COLUMN IF NOT EXISTS complement_reason VARCHAR(500);

CREATE TABLE IF NOT EXISTS merchant_onboarding_document (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES merchant_onboarding_case(id),
    document_type VARCHAR(48) NOT NULL,
    document_version INTEGER NOT NULL CHECK (document_version > 0),
    storage_reference VARCHAR(512) NOT NULL,
    content_type VARCHAR(96) NOT NULL CHECK (content_type IN
        ('application/pdf','image/jpeg','image/png')),
    content_length BIGINT NOT NULL CHECK (content_length BETWEEN 1 AND 20000000),
    sha256 VARCHAR(64) NOT NULL,
    review_status VARCHAR(24) NOT NULL,
    uploaded_by VARCHAR(96) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    reviewed_by VARCHAR(96),
    reviewed_at TIMESTAMPTZ,
    rejection_reason VARCHAR(500),
    CONSTRAINT uk_onboarding_document_version
        UNIQUE(case_id, document_type, document_version)
);

CREATE INDEX IF NOT EXISTS idx_onboarding_document_case
    ON merchant_onboarding_document(case_id, document_type, document_version DESC);

DO $$
BEGIN
    IF to_regclass('permissions') IS NOT NULL THEN
        PERFORM setval(pg_get_serial_sequence('permissions','id'),
            COALESCE((SELECT MAX(id) FROM permissions), 0) + 1, false);
        INSERT INTO permissions(code,label,category) VALUES
            ('ONBOARDING_PROSPECT_CREATE','Creer un prospect commercant','MERCHANT_ONBOARDING'),
            ('ONBOARDING_IDENTITY_LINK','Lier une identite commercant','MERCHANT_ONBOARDING'),
            ('ONBOARDING_KYC_REVIEW','Controler le KYC commercant','MERCHANT_ONBOARDING'),
            ('ONBOARDING_APPROVE','Approuver un dossier commercant','MERCHANT_ONBOARDING'),
            ('ONBOARDING_PROVISION','Provisionner vers Acquiring','MERCHANT_ONBOARDING')
        ON CONFLICT (code) DO UPDATE SET label=EXCLUDED.label, category=EXCLUDED.category;
    END IF;
END $$;
