-- Dynamic, tenant-isolated Kafka event routing. No credential or secret is stored here.
CREATE TABLE IF NOT EXISTS fraud_event_route (
    id uuid PRIMARY KEY,
    member_id varchar(64) NOT NULL,
    sector_id varchar(64) NOT NULL,
    event_type varchar(64) NOT NULL,
    topic_template varchar(249) NOT NULL,
    schema_version varchar(32) NOT NULL,
    retention_class varchar(32) NOT NULL,
    enabled boolean NOT NULL,
    priority integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_fraud_event_route_business_key UNIQUE(member_id,sector_id,event_type),
    CONSTRAINT ck_fraud_event_route_priority CHECK(priority BETWEEN 0 AND 1000)
);
CREATE INDEX IF NOT EXISTS ix_fraud_event_route_member_enabled
    ON fraud_event_route(member_id,enabled,sector_id,event_type);
