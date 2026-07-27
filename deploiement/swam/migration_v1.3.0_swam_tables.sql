-- =====================================================================
-- SWAM incr.1 : tables metier + users + grants (miroir DMAS, crypto reelle)
-- =====================================================================
BEGIN;

-- ---------- USERS (idempotent) ----------
DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_issuer_user') THEN
      CREATE ROLE swam_issuer_user LOGIN PASSWORD 'postgres123';
   END IF;
   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_acquirer_user') THEN
      CREATE ROLE swam_acquirer_user LOGIN PASSWORD 'postgres123';
   END IF;
END $$;

-- ---------- TABLES ISSUER (proprietaire swam_issuer_user) ----------

CREATE TABLE IF NOT EXISTS swam_cards (
    id          BIGSERIAL PRIMARY KEY,
    pan         VARCHAR(19) NOT NULL UNIQUE,
    pin         VARCHAR(12) NOT NULL,
    balance     BIGINT      NOT NULL DEFAULT 0,
    currency    VARCHAR(3)  NOT NULL DEFAULT '504',   -- MAD par defaut (SWAM)
    expiry      VARCHAR(4),
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP DEFAULT now(),
    updated_at  TIMESTAMP DEFAULT now()
);

-- Transactions AUTORISEES par le switch (cote issuer)
CREATE TABLE IF NOT EXISTS swam_iss_transactions (
    id              BIGSERIAL PRIMARY KEY,
    pan             VARCHAR(19) NOT NULL,
    stan            VARCHAR(6)  NOT NULL,
    transmission_dt VARCHAR(10) NOT NULL,
    mti             VARCHAR(4)  NOT NULL,
    processing_code VARCHAR(6),
    amount          BIGINT      NOT NULL,
    currency        VARCHAR(3),
    response_code   VARCHAR(3),                        -- DE39 SWAM (000/8xx) sur 3 car.
    status          VARCHAR(10) NOT NULL DEFAULT 'APPROVED',
    created_at      TIMESTAMP DEFAULT now(),
    reversed_at     TIMESTAMP,
    CONSTRAINT uq_swam_iss_tx UNIQUE (stan, transmission_dt)
);
CREATE INDEX IF NOT EXISTS idx_swam_iss_tx_pan ON swam_iss_transactions(pan);

CREATE TABLE IF NOT EXISTS swam_iss_keys (
    id              BIGSERIAL PRIMARY KEY,
    member_group_id VARCHAR(20) NOT NULL,
    key_type        VARCHAR(3)  NOT NULL,
    key_length      INTEGER     NOT NULL DEFAULT 24,
    key_under_lmk   VARCHAR(64),
    key_under_kek   VARCHAR(64),
    kcv             VARCHAR(6),
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_swam_iss_keys UNIQUE (member_group_id, key_type, status)
);

-- ---------- TABLES ACQUIRER (proprietaire swam_acquirer_user) ----------

-- Transactions EMISES par l'acquereur (ta demande : l'acquereur persiste aussi)
CREATE TABLE IF NOT EXISTS swam_acq_transactions (
    id              BIGSERIAL PRIMARY KEY,
    pan             VARCHAR(19) NOT NULL,
    stan            VARCHAR(6)  NOT NULL,
    transmission_dt VARCHAR(10) NOT NULL,
    mti             VARCHAR(4)  NOT NULL,
    processing_code VARCHAR(6),
    amount          BIGINT      NOT NULL,
    currency        VARCHAR(3),
    response_code   VARCHAR(3),                        -- DE39 recu du switch
    status          VARCHAR(10) NOT NULL DEFAULT 'SENT',
    created_at      TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_swam_acq_tx UNIQUE (stan, transmission_dt)
);
CREATE INDEX IF NOT EXISTS idx_swam_acq_tx_pan ON swam_acq_transactions(pan);

CREATE TABLE IF NOT EXISTS swam_acq_keys (
    id              BIGSERIAL PRIMARY KEY,
    member_group_id VARCHAR(20) NOT NULL,
    key_type        VARCHAR(3)  NOT NULL,
    key_length      INTEGER     NOT NULL DEFAULT 24,
    key_under_lmk   VARCHAR(64),
    key_under_kek   VARCHAR(64),
    kcv             VARCHAR(6),
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_swam_acq_keys UNIQUE (member_group_id, key_type, status)
);

-- ---------- KEK (partagee, proprietaire postgres) ----------
CREATE TABLE IF NOT EXISTS swam_kek (
    id                BIGSERIAL PRIMARY KEY,
    member_group_id   VARCHAR(20) NOT NULL,
    key_length        INTEGER     NOT NULL DEFAULT 24,
    kek_clear         VARCHAR(48),
    kek_under_acq_lmk VARCHAR(128),
    kek_under_iss_lmk VARCHAR(128),
    kcv               VARCHAR(6),
    status            VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    description       VARCHAR(255),
    created_at        TIMESTAMP DEFAULT now(),
    CONSTRAINT uq_swam_kek_group UNIQUE (member_group_id),
    CONSTRAINT chk_swam_kek_status CHECK (status IN ('ACTIVE','INACTIVE','PENDING'))
);
CREATE INDEX IF NOT EXISTS idx_swam_kek_group ON swam_kek(member_group_id);

-- ---------- GRANTS (miroir de DMAS) ----------
-- Issuer : possede cards, iss_transactions, iss_keys ; lit acq_keys(SELECT), kek(RW)
GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON swam_cards            TO swam_issuer_user;
GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON swam_iss_transactions TO swam_issuer_user;
GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON swam_iss_keys         TO swam_issuer_user;
GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON swam_kek              TO swam_issuer_user;
GRANT SELECT                                                 ON swam_acq_keys         TO swam_issuer_user;

-- Acquirer : possede acq_transactions, acq_keys ; lit cards/iss_keys(SELECT), kek(RW)
GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON swam_acq_transactions TO swam_acquirer_user;
GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON swam_acq_keys         TO swam_acquirer_user;
GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON swam_kek              TO swam_acquirer_user;
GRANT SELECT                                                 ON swam_cards            TO swam_acquirer_user;
GRANT SELECT                                                 ON swam_iss_transactions TO swam_acquirer_user;

-- Sequences (necessaires pour les INSERT)
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_issuer_user;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_acquirer_user;

-- Tables communes (referentiel + auth) : les 2 users lisent networks/users, campaigns etc.
GRANT SELECT ON networks       TO swam_issuer_user, swam_acquirer_user;
GRANT SELECT ON message_types  TO swam_issuer_user, swam_acquirer_user;
GRANT SELECT,UPDATE ON users   TO swam_issuer_user, swam_acquirer_user;
GRANT SELECT,INSERT,UPDATE ON key_store TO swam_issuer_user, swam_acquirer_user;

COMMIT;
