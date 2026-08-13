CREATE SEQUENCE IF NOT EXISTS way4_external_mid_seq
    START WITH 990001000000001 INCREMENT BY 1 MINVALUE 990001000000000 MAXVALUE 990001999999999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS way4_external_tid_seq
    START WITH 99000001 INCREMENT BY 1 MINVALUE 99000000 MAXVALUE 99999999 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS way4_merchant_contract_number_seq
    START WITH 1 INCREMENT BY 1 MINVALUE 1 MAXVALUE 99999999 NO CYCLE;

CREATE TABLE IF NOT EXISTS way4_external_identifier_allocation (
    id UUID PRIMARY KEY,
    allocation_type VARCHAR(32) NOT NULL,
    business_key VARCHAR(160) NOT NULL,
    allocated_value VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_way4_external_allocation_type CHECK
        (allocation_type IN ('MID','TID','MERCHANT_CONTRACT')),
    CONSTRAINT uk_way4_external_allocation_business UNIQUE (allocation_type,business_key),
    CONSTRAINT uk_way4_external_allocation_value UNIQUE (allocation_type,allocated_value)
);

-- Runtime code additionally checks current_database()='CARSDB', the explicit CARSDB
-- environment flag and the absence of a production Spring profile before every allocation.
