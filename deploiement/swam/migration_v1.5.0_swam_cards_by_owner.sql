-- =====================================================================
-- SWAM SID : separation physique des cartes par proprietaire metier.
--
-- issuer_swam_cards   : cartes du switch/emetteur, swam_issuer_user
-- acquirer_swam_cards : cartes du membre/acquereur, swam_acquirer_user
--
-- Lors d'une mise a niveau, les cartes de l'ancienne table partagee sont
-- copiees dans les deux tables pour ne perdre aucune donnee. Les deux jeux
-- deviennent ensuite completement independants.
-- =====================================================================
BEGIN;

CREATE TABLE IF NOT EXISTS issuer_swam_cards (
    id          BIGSERIAL PRIMARY KEY,
    pan         VARCHAR(19) NOT NULL UNIQUE,
    pin         VARCHAR(12) NOT NULL,
    balance     BIGINT      NOT NULL DEFAULT 0,
    currency    VARCHAR(3)  NOT NULL DEFAULT '504',
    expiry      VARCHAR(4),
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP DEFAULT now(),
    updated_at  TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS acquirer_swam_cards (
    id          BIGSERIAL PRIMARY KEY,
    pan         VARCHAR(19) NOT NULL UNIQUE,
    pin         VARCHAR(12) NOT NULL,
    balance     BIGINT      NOT NULL DEFAULT 0,
    currency    VARCHAR(3)  NOT NULL DEFAULT '504',
    expiry      VARCHAR(4),
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP DEFAULT now(),
    updated_at  TIMESTAMP DEFAULT now()
);

DO $$
BEGIN
    IF to_regclass('public.swam_cards') IS NOT NULL THEN
        INSERT INTO issuer_swam_cards
            (pan, pin, balance, currency, expiry, status, created_at, updated_at)
        SELECT pan, pin, balance, currency, expiry, status, created_at, updated_at
        FROM swam_cards
        ON CONFLICT (pan) DO NOTHING;

        INSERT INTO acquirer_swam_cards
            (pan, pin, balance, currency, expiry, status, created_at, updated_at)
        SELECT pan, pin, balance, currency, expiry, status, created_at, updated_at
        FROM swam_cards
        ON CONFLICT (pan) DO NOTHING;

        DROP TABLE swam_cards;
    END IF;
END $$;

ALTER TABLE issuer_swam_cards OWNER TO swam_issuer_user;
ALTER SEQUENCE issuer_swam_cards_id_seq OWNER TO swam_issuer_user;
ALTER TABLE acquirer_swam_cards OWNER TO swam_acquirer_user;
ALTER SEQUENCE acquirer_swam_cards_id_seq OWNER TO swam_acquirer_user;

REVOKE ALL ON issuer_swam_cards FROM swam_acquirer_user;
REVOKE ALL ON issuer_swam_cards_id_seq FROM swam_acquirer_user;
REVOKE ALL ON acquirer_swam_cards FROM swam_issuer_user;
REVOKE ALL ON acquirer_swam_cards_id_seq FROM swam_issuer_user;

GRANT SELECT, INSERT, UPDATE, DELETE ON issuer_swam_cards
TO swam_issuer_user;
GRANT USAGE, SELECT ON issuer_swam_cards_id_seq
TO swam_issuer_user;

GRANT SELECT, INSERT, UPDATE, DELETE ON acquirer_swam_cards
TO swam_acquirer_user;
GRANT USAGE, SELECT ON acquirer_swam_cards_id_seq
TO swam_acquirer_user;

COMMIT;
