-- ═══════════════════════════════════════════════════════════
-- DMAS — table des cartes (côté BANQUE émettrice / issuer)
-- La banque connaît les cartes, leur PIN attendu et leur solde.
-- Sert au moteur de décision du 0100 (vérif PIN + solde).
-- Solde en CENTIMES (BIGINT) pour éviter les flottants.
-- ═══════════════════════════════════════════════════════════
\connect scenariogenerator

CREATE TABLE IF NOT EXISTS dmas_cards (
    id              BIGSERIAL PRIMARY KEY,
    pan             VARCHAR(19)  NOT NULL UNIQUE,
    pin             VARCHAR(12)  NOT NULL,            -- PIN attendu (clair, pour simulateur)
    balance         BIGINT       NOT NULL DEFAULT 0,  -- solde en centimes
    currency        VARCHAR(3)   NOT NULL DEFAULT '840',
    expiry          VARCHAR(4),                       -- YYMM
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    DEFAULT NOW(),
    updated_at      TIMESTAMP    DEFAULT NOW()
);

-- Ownership à l'issuer (la banque)
ALTER TABLE dmas_cards OWNER TO dmas_issuer_user;

-- GRANT SELECT croisé pour que l'acquéreur valide l'entité au boot (ddl-auto=validate)
GRANT SELECT ON dmas_cards TO dmas_acquirer_user;
GRANT USAGE, SELECT ON dmas_cards_id_seq TO dmas_acquirer_user;
