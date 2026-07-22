-- ============================================================
--  V5__orchestrator_cards_dmas.sql
--
--  Table de cartes PROPRE A L'ORCHESTRATEUR pour le reseau DMAS.
--
--  L'orchestrateur ne doit pas lire les tables metier du membre
--  (mc_dmas_cards). Il joue le role du TERMINAL : il possede ses
--  cartes de test et, a terme, les donnees EMV pour construire le
--  DE55 qu'il transmettra au membre.
--
--  Nom prefixe par reseau : sg_orchestrator_cards_<network>, pour
--  distinguer DMAS de SWAM et MC SMS a venir.
-- ============================================================

CREATE TABLE IF NOT EXISTS sg_orchestrator_cards_dmas (
    id              BIGSERIAL PRIMARY KEY,
    pan             VARCHAR(19)  NOT NULL UNIQUE,
    pin             VARCHAR(12)  NOT NULL,
    balance         BIGINT       NOT NULL DEFAULT 0,
    currency        VARCHAR(3)   NOT NULL DEFAULT '840',
    expiry          VARCHAR(4),
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    bank_code       VARCHAR(6),

    -- Donnees EMV : l'orchestrateur joue le terminal
    emv_aid         VARCHAR(32),
    emv_aip         VARCHAR(4),
    emv_psn         VARCHAR(2),
    emv_atc         INTEGER      DEFAULT 0,
    emv_app_version VARCHAR(4),
    emv_iad         VARCHAR(64),
    emv_cvm_results VARCHAR(6),

    created_at      TIMESTAMP    DEFAULT now(),
    updated_at      TIMESTAMP    DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_orch_cards_dmas_bank
    ON sg_orchestrator_cards_dmas (bank_code, pan);

COMMENT ON TABLE sg_orchestrator_cards_dmas IS
    'Cartes de test de l''orchestrateur pour DMAS. Autonome : ne depend pas de mc_dmas_cards (table metier du membre).';

-- ------------------------------------------------------------
--  Amorce : copie des cartes actives du membre, une seule fois.
--  Passage unique pour ne pas partir d'une table vide ; ensuite
--  l'orchestrateur gere ses propres cartes independamment.
-- ------------------------------------------------------------
INSERT INTO sg_orchestrator_cards_dmas
    (pan, pin, balance, currency, expiry, status, bank_code,
     emv_aid, emv_aip, emv_psn, emv_atc, emv_app_version, emv_iad, emv_cvm_results)
SELECT pan, pin, balance, currency, expiry, status, bank_code,
       emv_aid, emv_aip, emv_psn, emv_atc, emv_app_version, emv_iad, emv_cvm_results
FROM mc_dmas_cards
ON CONFLICT (pan) DO NOTHING;

-- ------------------------------------------------------------
--  Droits : l'orchestrateur utilise le user postgres par defaut,
--  mais on prevoit le cas d'un user dedie.
-- ------------------------------------------------------------
-- GRANT SELECT, INSERT, UPDATE ON sg_orchestrator_cards_dmas TO sg_orchestrator;

SELECT count(*) AS cartes_orchestrateur FROM sg_orchestrator_cards_dmas;
