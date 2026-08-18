CREATE TABLE IF NOT EXISTS softpos_activation (
  activation_id varchar(36) PRIMARY KEY, activation_hash varchar(64) NOT NULL UNIQUE,
  member_id varchar(64) NOT NULL, merchant_id varchar(64) NOT NULL, outlet_id varchar(64) NOT NULL,
  terminal_id varchar(8) NOT NULL, expires_at timestamptz NOT NULL, consumed_at timestamptz
);
CREATE TABLE IF NOT EXISTS softpos_device (
  device_id varchar(36) PRIMARY KEY, member_id varchar(64) NOT NULL, merchant_id varchar(64) NOT NULL,
  outlet_id varchar(64) NOT NULL, terminal_id varchar(8) NOT NULL, fingerprint_hash varchar(64) NOT NULL,
  public_key_hash varchar(64) NOT NULL, status varchar(24) NOT NULL, application_version varchar(32) NOT NULL,
  integrity_valid_until timestamptz, created_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
  CONSTRAINT uk_softpos_device_member_fingerprint UNIQUE(member_id, fingerprint_hash),
  CONSTRAINT uk_softpos_device_member_terminal UNIQUE(member_id, terminal_id)
);
CREATE TABLE IF NOT EXISTS softpos_poserver_route (
  route_id varchar(36) PRIMARY KEY, member_id varchar(64) NOT NULL, environment varchar(32) NOT NULL,
  primary_mode varchar(24) NOT NULL, endpoint varchar(256) NOT NULL, connect_timeout_ms integer NOT NULL,
  response_timeout_ms integer NOT NULL, active boolean NOT NULL, version bigint NOT NULL DEFAULT 0,
  CONSTRAINT uk_softpos_route_member_env UNIQUE(member_id, environment)
);
CREATE TABLE IF NOT EXISTS softpos_transaction (
  transaction_id varchar(36) PRIMARY KEY, member_id varchar(64) NOT NULL, device_id varchar(36) NOT NULL,
  client_transaction_id varchar(64) NOT NULL, idempotency_key varchar(128) NOT NULL,
  acceptance_channel varchar(16) NOT NULL, amount_minor bigint NOT NULL, currency varchar(3) NOT NULL,
  credential_reference_hash varchar(64) NOT NULL, status varchar(24) NOT NULL, response_code varchar(3),
  authorization_code varchar(12), pos_transaction_id varchar(64), created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
  CONSTRAINT uk_softpos_tx_member_client UNIQUE(member_id, client_transaction_id),
  CONSTRAINT uk_softpos_tx_member_idempotency UNIQUE(member_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS ix_softpos_tx_member_updated ON softpos_transaction(member_id, updated_at DESC);
