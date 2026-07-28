-- ============================================================
-- Mastercard Single Message System (SMS) — Tables
-- Schema : public, Owner : postgres
-- Modele : tables SWAM existantes (swam_kek, swam_acq_keys, etc.)
-- ============================================================

-- ------------------------------------------------------------
-- 1. ROLES / USERS APPLICATIFS
-- ------------------------------------------------------------
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'mc_sms_acquirer_user') THEN
        CREATE ROLE mc_sms_acquirer_user LOGIN PASSWORD 'mc_acq_pass';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'mc_sms_issuer_user') THEN
        CREATE ROLE mc_sms_issuer_user LOGIN PASSWORD 'mc_iss_pass';
    END IF;
END $$;

-- ------------------------------------------------------------
-- 2. mc_sms_kek — KEK (ZMK) du membre Mastercard SMS
--    Modele : swam_kek
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.mc_sms_kek (
    id                  BIGSERIAL       PRIMARY KEY,
    member_group_id     VARCHAR(20)     NOT NULL,
    key_length          INTEGER         NOT NULL DEFAULT 24,
    kek_clear           VARCHAR(48),
    kek_under_acq_lmk   VARCHAR(128),
    kek_under_iss_lmk   VARCHAR(128),
    kcv                 VARCHAR(6),
    status              VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    description         VARCHAR(255),
    created_at          TIMESTAMP       DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mc_sms_kek_group
    ON public.mc_sms_kek (member_group_id);
CREATE INDEX IF NOT EXISTS idx_mc_sms_kek_group
    ON public.mc_sms_kek (member_group_id);

-- ------------------------------------------------------------
-- 3. mc_sms_acq_keys — Cles session acquereur (PEK, MAK...)
--    Modele : swam_acq_keys
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.mc_sms_acq_keys (
    id                  BIGSERIAL       PRIMARY KEY,
    member_group_id     VARCHAR(20)     NOT NULL,
    key_type            VARCHAR(3)      NOT NULL,   -- PEK, MAK...
    key_length          INTEGER         NOT NULL DEFAULT 24,
    key_under_lmk       VARCHAR(64),
    key_under_kek       VARCHAR(64),
    kcv                 VARCHAR(6),
    status              VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP       DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mc_sms_acq_keys
    ON public.mc_sms_acq_keys (member_group_id, key_type, status);

-- ------------------------------------------------------------
-- 4. mc_sms_iss_keys — Cles session issuer (PEK, MAK...)
--    Modele : swam_iss_keys
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.mc_sms_iss_keys (
    id                  BIGSERIAL       PRIMARY KEY,
    member_group_id     VARCHAR(20)     NOT NULL,
    key_type            VARCHAR(3)      NOT NULL,
    key_length          INTEGER         NOT NULL DEFAULT 24,
    key_under_lmk       VARCHAR(64),
    key_under_kek       VARCHAR(64),
    kcv                 VARCHAR(6),
    status              VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP       DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mc_sms_iss_keys
    ON public.mc_sms_iss_keys (member_group_id, key_type, status);

-- ------------------------------------------------------------
-- 5. mc_sms_acq_transactions — Transactions acquereur
--    Modele : swam_acq_transactions
--    Specifique SMS : MTI 0xxx, response_code an-2
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.mc_sms_acq_transactions (
    id                  BIGSERIAL       PRIMARY KEY,
    pan                 VARCHAR(19)     NOT NULL,
    stan                VARCHAR(6)      NOT NULL,
    transmission_dt     VARCHAR(10)     NOT NULL,   -- MMDDhhmmss UTC
    mti                 VARCHAR(4)      NOT NULL,   -- 0200, 0210...
    processing_code     VARCHAR(6),
    amount              BIGINT          NOT NULL,
    currency            VARCHAR(3),
    response_code       VARCHAR(2),                 -- an-2 Mastercard SMS
    auth_id_response    VARCHAR(6),                 -- DE38
    network_id          VARCHAR(3),                 -- DE24 (NII)
    retrieval_ref       VARCHAR(12),                -- DE37
    status              VARCHAR(10)     NOT NULL DEFAULT 'SENT',
    created_at          TIMESTAMP       DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mc_sms_acq_tx
    ON public.mc_sms_acq_transactions (stan, transmission_dt);
CREATE INDEX IF NOT EXISTS idx_mc_sms_acq_tx_pan
    ON public.mc_sms_acq_transactions (pan);

-- ------------------------------------------------------------
-- 6. mc_sms_iss_transactions — Transactions issuer (simulateur)
--    Modele : swam_iss_transactions
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.mc_sms_iss_transactions (
    id                  BIGSERIAL       PRIMARY KEY,
    pan                 VARCHAR(19)     NOT NULL,
    stan                VARCHAR(6)      NOT NULL,
    transmission_dt     VARCHAR(10)     NOT NULL,
    mti                 VARCHAR(4)      NOT NULL,
    processing_code     VARCHAR(6),
    amount              BIGINT          NOT NULL,
    currency            VARCHAR(3),
    response_code       VARCHAR(2),
    auth_id_response    VARCHAR(6),
    retrieval_ref       VARCHAR(12),
    status              VARCHAR(10)     NOT NULL DEFAULT 'APPROVED',
    created_at          TIMESTAMP       DEFAULT now(),
    reversed_at         TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mc_sms_iss_tx
    ON public.mc_sms_iss_transactions (stan, transmission_dt);
CREATE INDEX IF NOT EXISTS idx_mc_sms_iss_tx_pan
    ON public.mc_sms_iss_transactions (pan);

-- ------------------------------------------------------------
-- 7. mc_sms_cards — Cartes de test Mastercard SMS
--    Modele : swam_cards
--    Specifique : cvv2, service_code (Mastercard)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.mc_sms_cards (
    id                  BIGSERIAL       PRIMARY KEY,
    pan                 VARCHAR(19)     NOT NULL,
    pin                 VARCHAR(12)     NOT NULL,
    balance             BIGINT          NOT NULL DEFAULT 0,
    currency            VARCHAR(3)      NOT NULL DEFAULT '504',
    expiry              VARCHAR(4),                 -- YYMM
    cvv2                VARCHAR(3),
    service_code        VARCHAR(3)      DEFAULT '101',
    status              VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP       DEFAULT now(),
    updated_at          TIMESTAMP       DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mc_sms_cards_pan
    ON public.mc_sms_cards (pan);

-- ------------------------------------------------------------
-- 8. GRANTS — mc_sms_acquirer_user
-- ------------------------------------------------------------
-- kek
GRANT SELECT, INSERT, UPDATE ON public.mc_sms_kek TO mc_sms_acquirer_user;
GRANT USAGE, SELECT ON SEQUENCE mc_sms_kek_id_seq TO mc_sms_acquirer_user;

-- acq_keys
GRANT SELECT, INSERT, UPDATE ON public.mc_sms_acq_keys TO mc_sms_acquirer_user;
GRANT USAGE, SELECT ON SEQUENCE mc_sms_acq_keys_id_seq TO mc_sms_acquirer_user;

-- acq_transactions
GRANT SELECT, INSERT, UPDATE ON public.mc_sms_acq_transactions TO mc_sms_acquirer_user;
GRANT USAGE, SELECT ON SEQUENCE mc_sms_acq_transactions_id_seq TO mc_sms_acquirer_user;

-- cards (lecture seule pour l'acquereur)
GRANT SELECT ON public.mc_sms_cards TO mc_sms_acquirer_user;

-- ------------------------------------------------------------
-- 9. GRANTS — mc_sms_issuer_user
-- ------------------------------------------------------------
-- kek
GRANT SELECT, INSERT, UPDATE ON public.mc_sms_kek TO mc_sms_issuer_user;
GRANT USAGE, SELECT ON SEQUENCE mc_sms_kek_id_seq TO mc_sms_issuer_user;

-- iss_keys
GRANT SELECT, INSERT, UPDATE ON public.mc_sms_iss_keys TO mc_sms_issuer_user;
GRANT USAGE, SELECT ON SEQUENCE mc_sms_iss_keys_id_seq TO mc_sms_issuer_user;

-- iss_transactions
GRANT SELECT, INSERT, UPDATE ON public.mc_sms_iss_transactions TO mc_sms_issuer_user;
GRANT USAGE, SELECT ON SEQUENCE mc_sms_iss_transactions_id_seq TO mc_sms_issuer_user;

-- cards (lecture + mise a jour solde)
GRANT SELECT, INSERT, UPDATE ON public.mc_sms_cards TO mc_sms_issuer_user;
GRANT USAGE, SELECT ON SEQUENCE mc_sms_cards_id_seq TO mc_sms_issuer_user;

-- ------------------------------------------------------------
-- 10. Ligne networks pour Mastercard SMS
-- ------------------------------------------------------------
INSERT INTO public.networks (
    code, name, iso_version, header_type, packager_class,
    issuer_host, issuer_iso_port,
    acquirer_jpos_port, active
) VALUES (
    'MASTERCARD_SMS',
    'Mastercard Single Message System',
    '1987',
    'MC_SMS',
    'com.staging.sg.common.iso.MastercardPackager',
    'localhost',
    7001,
    8095,
    true
) ON CONFLICT (code) DO NOTHING;
