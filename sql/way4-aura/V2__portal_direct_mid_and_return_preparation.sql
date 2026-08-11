CREATE SEQUENCE IF NOT EXISTS way4_mid_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS way4_mid_allocation (
    id UUID PRIMARY KEY,
    onboarding_case_id UUID NOT NULL,
    application_reg_number VARCHAR(96) NOT NULL,
    mid VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_way4_mid_case UNIQUE(onboarding_case_id),
    CONSTRAINT uk_way4_mid_reg_number UNIQUE(application_reg_number),
    CONSTRAINT uk_way4_mid_value UNIQUE(mid)
);

-- The sequence is intentionally not enabled for allocation by this migration.
-- Its start, prefix and width must be approved from the real AURA MID rules.
