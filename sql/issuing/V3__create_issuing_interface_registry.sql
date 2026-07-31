-- Registre versionne des interfaces Issuing.
-- Les secrets ne sont jamais stockes ici : secret_reference pointe vers un coffre.

CREATE TABLE IF NOT EXISTS issuing_interface_endpoint (
    id                          UUID PRIMARY KEY,
    issuer_id                   VARCHAR(64) NOT NULL,
    interface_type              VARCHAR(32) NOT NULL,
    interface_version           INTEGER NOT NULL,
    direction                   VARCHAR(16) NOT NULL,
    protocol                    VARCHAR(16) NOT NULL,
    host                        VARCHAR(255) NOT NULL,
    port                        INTEGER NOT NULL,
    base_path                   VARCHAR(255),
    connect_timeout_ms          INTEGER NOT NULL,
    read_timeout_ms             INTEGER NOT NULL,
    tls_profile                 VARCHAR(128),
    secret_reference            VARCHAR(255),
    parameters_json             TEXT NOT NULL DEFAULT '{}',
    status                      VARCHAR(16) NOT NULL,
    created_by                  VARCHAR(64) NOT NULL,
    creation_idempotency_key    VARCHAR(128) NOT NULL,
    creation_fingerprint        VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    row_version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_issuing_interface_version
        CHECK (interface_version > 0),
    CONSTRAINT ck_issuing_interface_port
        CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_issuing_interface_connect_timeout
        CHECK (connect_timeout_ms > 0),
    CONSTRAINT ck_issuing_interface_read_timeout
        CHECK (read_timeout_ms > 0),
    CONSTRAINT ck_issuing_interface_tls_profile
        CHECK (protocol <> 'TLS_TCP' OR tls_profile IS NOT NULL),
    CONSTRAINT uk_issuing_interface_version
        UNIQUE (issuer_id, interface_type, interface_version),
    CONSTRAINT uk_issuing_interface_idempotency
        UNIQUE (issuer_id, created_by, creation_idempotency_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_issuing_interface_active
    ON issuing_interface_endpoint (issuer_id, interface_type)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_issuing_interface_lookup
    ON issuing_interface_endpoint
        (issuer_id, interface_type, status, interface_version DESC);
