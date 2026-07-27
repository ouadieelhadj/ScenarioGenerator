-- ScenarioGenerator - SWAM LIS clearing persistence (member and switch)
\connect scenariogenerator

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'swam_lis_member_user') THEN
        CREATE USER swam_lis_member_user WITH PASSWORD 'postgres123';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'swam_lis_switch_user') THEN
        CREATE USER swam_lis_switch_user WITH PASSWORD 'postgres123';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE scenariogenerator TO swam_lis_member_user, swam_lis_switch_user;

DO $$
DECLARE
    side_name TEXT;
BEGIN
    FOREACH side_name IN ARRAY ARRAY['member', 'switch']
    LOOP
        EXECUTE format($ddl$
            CREATE TABLE IF NOT EXISTS %1$s_lis_business_day (
                id BIGSERIAL PRIMARY KEY,
                bank_member_id VARCHAR(20) NOT NULL,
                business_date DATE NOT NULL,
                status VARCHAR(16) NOT NULL,
                cutoff_at TIMESTAMP,
                opened_at TIMESTAMP NOT NULL,
                closed_at TIMESTAMP,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                version BIGINT NOT NULL DEFAULT 0,
                CONSTRAINT uk_%1$s_business_date UNIQUE (bank_member_id, business_date),
                CONSTRAINT chk_%1$s_business_status
                    CHECK (status IN ('OPEN','CLOSING','CLOSED','FAILED'))
            )
        $ddl$, side_name);

        EXECUTE format($ddl$
            CREATE TABLE IF NOT EXISTS %1$s_lis_batch_execution (
                id BIGSERIAL PRIMARY KEY,
                business_day_id BIGINT NOT NULL REFERENCES %1$s_lis_business_day(id),
                batch_type VARCHAR(32) NOT NULL,
                status VARCHAR(16) NOT NULL,
                correlation_id UUID NOT NULL UNIQUE,
                started_at TIMESTAMP,
                completed_at TIMESTAMP,
                read_count BIGINT NOT NULL DEFAULT 0,
                write_count BIGINT NOT NULL DEFAULT 0,
                skip_count BIGINT NOT NULL DEFAULT 0,
                error_count BIGINT NOT NULL DEFAULT 0,
                requested_by VARCHAR(80),
                error_summary VARCHAR(1000),
                version BIGINT NOT NULL DEFAULT 0,
                CONSTRAINT chk_%1$s_batch_status
                    CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED'))
            )
        $ddl$, side_name);

        EXECUTE format($ddl$
            CREATE TABLE IF NOT EXISTS %1$s_lis_file (
                id BIGSERIAL PRIMARY KEY,
                business_day_id BIGINT NOT NULL REFERENCES %1$s_lis_business_day(id),
                direction VARCHAR(10) NOT NULL,
                file_name VARCHAR(160) NOT NULL,
                storage_path VARCHAR(500) NOT NULL,
                source_member VARCHAR(20) NOT NULL,
                destination_member VARCHAR(20) NOT NULL,
                processing_date DATE NOT NULL,
                file_sequence INTEGER NOT NULL,
                regeneration_status VARCHAR(1) NOT NULL DEFAULT 'N',
                lis_version VARCHAR(10) NOT NULL DEFAULT '4.13',
                sha256 VARCHAR(64) NOT NULL,
                byte_size BIGINT NOT NULL,
                physical_record_count INTEGER NOT NULL,
                status VARCHAR(16) NOT NULL,
                original_file_id BIGINT REFERENCES %1$s_lis_file(id),
                created_at TIMESTAMP NOT NULL,
                version BIGINT NOT NULL DEFAULT 0,
                CONSTRAINT uk_%1$s_lis_file_identity UNIQUE (
                    direction, source_member, destination_member,
                    processing_date, file_sequence, regeneration_status
                ),
                CONSTRAINT chk_%1$s_lis_direction
                    CHECK (direction IN ('INCOMING','OUTGOING')),
                CONSTRAINT chk_%1$s_lis_regeneration
                    CHECK (regeneration_status IN ('N','R'))
            )
        $ddl$, side_name);

        EXECUTE format($ddl$
            CREATE UNIQUE INDEX IF NOT EXISTS uk_%1$s_lis_incoming_sha256
            ON %1$s_lis_file(sha256) WHERE direction = 'INCOMING'
        $ddl$, side_name);

        EXECUTE format($ddl$
            CREATE TABLE IF NOT EXISTS %1$s_clearing_transaction (
                id BIGSERIAL PRIMARY KEY,
                bank_member_id VARCHAR(20) NOT NULL,
                business_day_id BIGINT NOT NULL REFERENCES %1$s_lis_business_day(id),
                local_sid_transaction_id BIGINT,
                local_source_type VARCHAR(24),
                incoming_lis_file_id BIGINT REFERENCES %1$s_lis_file(id),
                incoming_record_sequence INTEGER,
                functional_key VARCHAR(64) NOT NULL,
                transaction_type VARCHAR(16) NOT NULL,
                clearing_cycle INTEGER NOT NULL DEFAULT 1,
                pan_fingerprint VARCHAR(64) NOT NULL,
                masked_pan VARCHAR(24) NOT NULL,
                rrn VARCHAR(12),
                stan VARCHAR(6),
                authorization_code VARCHAR(6),
                transaction_at TIMESTAMP,
                processing_date DATE,
                processing_code VARCHAR(6),
                mcc VARCHAR(4),
                pos_data_code VARCHAR(12),
                terminal_id VARCHAR(8),
                merchant_id VARCHAR(15),
                merchant_name VARCHAR(25),
                merchant_city VARCHAR(13),
                transaction_amount BIGINT NOT NULL,
                transaction_currency VARCHAR(3) NOT NULL,
                billing_amount BIGINT,
                billing_currency VARCHAR(3),
                settlement_amount BIGINT,
                settlement_currency VARCHAR(3),
                source_presence VARCHAR(16) NOT NULL,
                match_status VARCHAR(24) NOT NULL,
                accounting_status VARCHAR(16) NOT NULL,
                dispute_status VARCHAR(16) NOT NULL,
                manual_reason VARCHAR(500),
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                version BIGINT NOT NULL DEFAULT 0,
                CONSTRAINT uk_%1$s_clearing_local_source UNIQUE
                    (local_source_type, local_sid_transaction_id, clearing_cycle),
                CONSTRAINT uk_%1$s_clearing_lis_source UNIQUE
                    (incoming_lis_file_id, incoming_record_sequence),
                CONSTRAINT chk_%1$s_clearing_cycle CHECK (clearing_cycle IN (1,2))
            )
        $ddl$, side_name);

        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%1$s_clearing_functional_key ON %1$s_clearing_transaction(functional_key)',
            side_name);
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%1$s_clearing_rrn ON %1$s_clearing_transaction(rrn)',
            side_name);
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%1$s_clearing_match ON %1$s_clearing_transaction(bank_member_id, match_status)',
            side_name);

        EXECUTE format($ddl$
            CREATE TABLE IF NOT EXISTS %1$s_chargeback (
                id BIGSERIAL PRIMARY KEY,
                bank_member_id VARCHAR(20) NOT NULL,
                clearing_transaction_id BIGINT NOT NULL REFERENCES %1$s_clearing_transaction(id),
                parent_chargeback_id BIGINT REFERENCES %1$s_chargeback(id),
                direction VARCHAR(10) NOT NULL,
                status VARCHAR(24) NOT NULL,
                transaction_code VARCHAR(2) NOT NULL,
                cycle_number INTEGER NOT NULL DEFAULT 1,
                reason_code VARCHAR(4) NOT NULL,
                chargeback_reference VARCHAR(6) NOT NULL,
                amount BIGINT NOT NULL,
                currency VARCHAR(3) NOT NULL,
                source_lis_file_id BIGINT REFERENCES %1$s_lis_file(id),
                source_record_sequence INTEGER,
                outgoing_lis_file_id BIGINT REFERENCES %1$s_lis_file(id),
                counterparty_member VARCHAR(20) NOT NULL,
                due_date DATE,
                emitted_at TIMESTAMP,
                received_at TIMESTAMP,
                created_by VARCHAR(80) NOT NULL,
                manual_reason VARCHAR(500),
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                version BIGINT NOT NULL DEFAULT 0,
                CONSTRAINT uk_%1$s_chargeback_lis_source UNIQUE
                    (source_lis_file_id, source_record_sequence),
                CONSTRAINT uk_%1$s_chargeback_business UNIQUE (
                    clearing_transaction_id, direction, cycle_number,
                    reason_code, chargeback_reference
                ),
                CONSTRAINT chk_%1$s_chargeback_direction
                    CHECK (direction IN ('EMITTED','RECEIVED')),
                CONSTRAINT chk_%1$s_chargeback_cycle CHECK (cycle_number IN (1,2))
            )
        $ddl$, side_name);

        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%1$s_chargeback_queue ON %1$s_chargeback(bank_member_id, direction, status)',
            side_name);
    END LOOP;
END
$$;

DO $$
DECLARE side_name TEXT;
BEGIN
    FOREACH side_name IN ARRAY ARRAY['member', 'switch']
    LOOP
        EXECUTE format($ddl$
            CREATE TABLE IF NOT EXISTS %1$s_accounting_entry (
                id BIGSERIAL PRIMARY KEY,
                business_day_id BIGINT NOT NULL REFERENCES %1$s_lis_business_day(id),
                clearing_transaction_id BIGINT NOT NULL REFERENCES %1$s_clearing_transaction(id),
                lis_file_id BIGINT NOT NULL REFERENCES %1$s_lis_file(id),
                entry_key VARCHAR(96) NOT NULL UNIQUE,
                account_code VARCHAR(40) NOT NULL,
                debit BIGINT NOT NULL CHECK (debit >= 0),
                credit BIGINT NOT NULL CHECK (credit >= 0),
                currency VARCHAR(3) NOT NULL,
                posting_date DATE NOT NULL,
                created_at TIMESTAMP NOT NULL,
                CONSTRAINT chk_%1$s_accounting_one_side
                    CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
            )
        $ddl$, side_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%1$s_accounting_tx ON %1$s_accounting_entry(clearing_transaction_id)', side_name);
    END LOOP;
END
$$;

GRANT SELECT ON swam_acq_transactions TO swam_lis_member_user;
GRANT SELECT ON swam_iss_transactions TO swam_lis_switch_user;

GRANT ALL PRIVILEGES ON
    member_lis_business_day,
    member_lis_batch_execution,
    member_lis_file,
    member_clearing_transaction,
    member_chargeback,
    member_accounting_entry
TO swam_lis_member_user;

GRANT ALL PRIVILEGES ON
    switch_lis_business_day,
    switch_lis_batch_execution,
    switch_lis_file,
    switch_clearing_transaction,
    switch_chargeback,
    switch_accounting_entry
TO swam_lis_switch_user;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public
TO swam_lis_member_user, swam_lis_switch_user;
