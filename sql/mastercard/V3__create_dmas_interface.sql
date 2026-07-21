-- ============================================================
--  V3__create_dmas_interface.sql
--
--  DMAS devient MULTI-BANQUE et abandonne la table `networks`.
--
--  Chaque module recoit UN SEUL parametre au demarrage :
--      --sg.interface=DMAS_BANK_A
--  et lit tout le reste ici : identifiants et ports.
--
--  Une liaison permanente par couple membre / Mastercard :
--      DMAS_BANK_A  ----socket----> DMAS_MASTERCARD_1
--      DMAS_BANK_B  ----socket----> DMAS_MASTERCARD_2
--
--  SWAM et MC SMS continuent d'utiliser `networks`, non modifiee.
-- ============================================================

-- ------------------------------------------------------------
--  1. Table des interfaces DMAS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mc_dmas_interface (
    id_interface     VARCHAR(32) PRIMARY KEY,   -- passe au demarrage
    bank_code        VARCHAR(6)  NOT NULL,      -- 6 chiffres, format ICA
    label            VARCHAR(64),

    -- Role ACQUEREUR : le membre acquiert une transaction sur son TPE
    acq_ica_de32     VARCHAR(11),   -- DE32 Acquiring Institution ID Code
    acq_arid         VARCHAR(11),   -- Acquirer Reference ID

    -- Role EMETTEUR : la carte du membre est utilisee ailleurs
    iss_ica_de100    VARCHAR(11),   -- DE100 Receiving Institution ID Code
    iss_arid         VARCHAR(11),

    -- Commun aux deux roles
    fwd_id_de33      VARCHAR(11),   -- DE33, six chiffres chez DMAS
    group_signon_de2 VARCHAR(11),   -- DE2 des messages 0800
    member_group_id  VARCHAR(32),   -- cle interne d'indexation des cles

    business_role    VARCHAR(16) NOT NULL DEFAULT 'BOTH'
        CHECK (business_role IN ('ACQUIRER', 'ISSUER', 'BOTH')),

    -- Transport
    host             VARCHAR(64) NOT NULL DEFAULT 'localhost',
    rest_port        INTEGER     NOT NULL,
    iso_port         INTEGER,       -- renseigne uniquement cote Mastercard

    -- Ou ce module se connecte. Indifferemment un autre module de
    -- ce projet ou un vrai MIP : il suffit de changer ces deux valeurs.
    --     localhost      8500    le module DMAS_MASTERCARD_1
    --     10.23.33.114  11127    un MIP reel, via relais
    -- Vide cote Mastercard, qui ecoute au lieu de se connecter.
    target_host      VARCHAR(64),
    target_port      INTEGER,

    -- Etat de l'interface, tenu a jour par le module lui-meme.
    --   OFF            module arrete
    --   SIGNON         sign-on accepte, liaison etablie
    --   PEK_EXCHANGED  cle echangee
    --   READY          apte a traiter des transactions
    --   SIGNOFF        sign-off emis
    status           VARCHAR(16) NOT NULL DEFAULT 'OFF'
        CHECK (status IN ('OFF','SIGNON','PEK_EXCHANGED','READY','SIGNOFF')),
    status_updated   TIMESTAMP,

    active           BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP,

    CONSTRAINT uk_dmas_interface_bank UNIQUE (bank_code),
    CONSTRAINT ck_dmas_bank_code CHECK (bank_code ~ '^[0-9]{6}$')
);

COMMENT ON TABLE mc_dmas_interface IS
    'Interfaces DMAS. Une ligne par banque et par Mastercard. Remplace networks pour DMAS.';
COMMENT ON COLUMN mc_dmas_interface.id_interface IS
    'Passe au demarrage : --sg.interface=<valeur>';
COMMENT ON COLUMN mc_dmas_interface.acq_ica_de32 IS
    'DE32 - identifie l''institution acquereuse (guide DMAS p.1929)';
COMMENT ON COLUMN mc_dmas_interface.fwd_id_de33 IS
    'DE33 - six chiffres, identifie qui route le message vers DMAS';
COMMENT ON COLUMN mc_dmas_interface.target_host IS
    'Ou ce module se connecte : un autre module ou un vrai MIP. Vide cote Mastercard.';
COMMENT ON COLUMN mc_dmas_interface.status IS
    'OFF, SIGNON, PEK_EXCHANGED, READY, SIGNOFF. Tenu a jour par le module ; le Mastercard peut basculer un membre a OFF si sa socket tombe.';
COMMENT ON COLUMN mc_dmas_interface.member_group_id IS
    'Cle de recherche EN BASE des KEK et PEK. A ne pas confondre avec group_signon_de2, qui est un identifiant RESEAU.';

-- ------------------------------------------------------------
--  2. Deux couples membre / Mastercard, pour tester en parallele
-- ------------------------------------------------------------
INSERT INTO mc_dmas_interface (
    id_interface, bank_code, label,
    acq_ica_de32, iss_ica_de100, fwd_id_de33,
    group_signon_de2, member_group_id, business_role,
    host, rest_port, iso_port, target_host, target_port
) VALUES
    ('DMAS_BANK_A', '022905', 'Banque A',
     '022905', '022905', '002202',
     '40260', 'TESTGRP01', 'BOTH',
     'localhost', 8084, NULL, 'localhost', 8500),

    ('DMAS_MASTERCARD_1', '002202', 'Mastercard DMAS 1',
     NULL, NULL, '002202',
     NULL, 'TESTGRP01', 'BOTH',
     'localhost', 8501, 8500, NULL, NULL),

    ('DMAS_BANK_B', '022906', 'Banque B',
     '022906', '022906', '002203',
     '40261', 'TESTGRP02', 'BOTH',
     'localhost', 8085, NULL, 'localhost', 8503),

    ('DMAS_MASTERCARD_2', '002203', 'Mastercard DMAS 2',
     NULL, NULL, '002203',
     NULL, 'TESTGRP02', 'BOTH',
     'localhost', 8502, 8503, NULL, NULL)
ON CONFLICT (id_interface) DO NOTHING;

-- ------------------------------------------------------------
--  3. Cloisonnement des donnees par banque
--
--  Sans cela, deux banques partageant le meme member_group_id
--  ecraseraient mutuellement leurs cles.
-- ------------------------------------------------------------
ALTER TABLE mc_dmas_kek              ADD COLUMN IF NOT EXISTS bank_code VARCHAR(6);
ALTER TABLE mc_dmas_member_keys      ADD COLUMN IF NOT EXISTS bank_code VARCHAR(6);
ALTER TABLE mc_dmas_mastercard_keys  ADD COLUMN IF NOT EXISTS bank_code VARCHAR(6);
ALTER TABLE mc_dmas_cards            ADD COLUMN IF NOT EXISTS bank_code VARCHAR(6);
ALTER TABLE mc_dmas_transactions     ADD COLUMN IF NOT EXISTS bank_code VARCHAR(6);

-- Les donnees existantes appartiennent a la banque A
UPDATE mc_dmas_kek             SET bank_code = '022905' WHERE bank_code IS NULL;
UPDATE mc_dmas_member_keys     SET bank_code = '022905' WHERE bank_code IS NULL;
UPDATE mc_dmas_mastercard_keys SET bank_code = '022905' WHERE bank_code IS NULL;
UPDATE mc_dmas_cards           SET bank_code = '022905' WHERE bank_code IS NULL;
UPDATE mc_dmas_transactions    SET bank_code = '022905' WHERE bank_code IS NULL;

-- Index de recherche : toujours filtrer par banque
CREATE INDEX IF NOT EXISTS ix_dmas_kek_bank      ON mc_dmas_kek (bank_code, member_group_id);
CREATE INDEX IF NOT EXISTS ix_dmas_mem_keys_bank ON mc_dmas_member_keys (bank_code, member_group_id, key_type, status);
CREATE INDEX IF NOT EXISTS ix_dmas_mc_keys_bank  ON mc_dmas_mastercard_keys (bank_code, member_group_id, key_type, status);
CREATE INDEX IF NOT EXISTS ix_dmas_cards_bank    ON mc_dmas_cards (bank_code, pan);
CREATE INDEX IF NOT EXISTS ix_dmas_tx_bank       ON mc_dmas_transactions (bank_code, stan, transmission_dt);

-- ------------------------------------------------------------
--  4. Droits
-- ------------------------------------------------------------
-- SELECT pour lire sa configuration, UPDATE pour tenir son statut a jour
GRANT SELECT, UPDATE ON mc_dmas_interface TO mc_dmas_member;
GRANT SELECT, UPDATE ON mc_dmas_interface TO mc_dmas_mastercard;

-- ------------------------------------------------------------
--  5. Verification
-- ------------------------------------------------------------
SELECT id_interface, bank_code, label, rest_port, iso_port,
       target_host, target_port, status
FROM mc_dmas_interface
ORDER BY id_interface;
