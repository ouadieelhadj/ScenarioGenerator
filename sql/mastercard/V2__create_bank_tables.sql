-- ============================================================
--  V2__create_bank_tables.sql
--
--  Modele : une table mere `bank` (identite de l'etablissement)
--           + une table fille par reseau, avec les identifiants
--           attribues par CE reseau, aux formats de CE reseau.
--
--  Cette version cree :
--      bank            identite commune
--      bank_mc_sms     identifiants Mastercard Single Message System
--
--  SWAM et DMAS ne sont PAS touches. Leurs tables filles
--  (bank_swam, bank_dmas) seront ajoutees quand on migrera ces
--  reseaux, sans modifier `bank` ni `bank_mc_sms`.
-- ============================================================

-- ------------------------------------------------------------
--  Table mere : identite de la banque
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bank (
    id           BIGSERIAL PRIMARY KEY,

    code         VARCHAR(32)  NOT NULL,   -- cle logique, ex. 'BANQUE_TEST'
    name         VARCHAR(128) NOT NULL,
    bic          VARCHAR(11),             -- SWIFT/BIC (8 ou 11 caracteres)
    country_code VARCHAR(3),              -- ISO 3166 numerique, ex. '504' Maroc

    description  VARCHAR(255),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP,

    CONSTRAINT uk_bank_code UNIQUE (code)
);

COMMENT ON TABLE bank IS
    'Identite de l''etablissement. Les identifiants propres a chaque reseau sont dans les tables bank_<reseau>.';

-- ------------------------------------------------------------
--  Table fille : Mastercard Single Message System
--
--  Ces valeurs sont ASSIGNEES PAR MASTERCARD.
--  Les formats viennent du Single Message System Guide (2 juin 2026).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bank_mc_sms (
    id                    BIGSERIAL PRIMARY KEY,
    bank_id               BIGINT       NOT NULL,

    -- DE33 Forwarding Institution ID Code (guide p.385)
    -- Format impose : 9000xxxxxx — exactement 10 chiffres.
    -- xxxxxx = processor ID assigne par le Single Message System.
    -- Present dans 0800, 0810, 0200.
    processor_id          VARCHAR(10)  NOT NULL,

    -- DE32 Acquiring Institution ID Code (guide p.383)
    -- Exactement 9 chiffres. Utilise a partir des 0200.
    acquiring_inst_id     VARCHAR(9),

    -- DE100 Receiving Institution ID Code
    receiving_inst_id     VARCHAR(11),

    -- ICA (Interbank Card Association) : 6 chiffres.
    -- Identifiant du membre chez Mastercard. Hors ISO :
    -- reporting, settlement, facturation.
    ica                   VARCHAR(6),
    settlement_ica        VARCHAR(6),

    -- DE96 Message Security Code (guide p.792)
    -- 8 octets binaires, stockes en hexadecimal (16 caracteres).
    -- "Password" du sign-on 0800 DE70=061.
    message_security_code VARCHAR(16),

    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP,

    CONSTRAINT fk_bank_mc_sms_bank FOREIGN KEY (bank_id) REFERENCES bank(id),
    CONSTRAINT uk_bank_mc_sms_bank UNIQUE (bank_id),

    -- Formats imposes par le guide
    CONSTRAINT ck_mc_processor_id     CHECK (processor_id ~ '^9000[0-9]{6}$'),
    CONSTRAINT ck_mc_acquiring_inst   CHECK (acquiring_inst_id IS NULL OR acquiring_inst_id ~ '^[0-9]{9}$'),
    CONSTRAINT ck_mc_ica              CHECK (ica IS NULL OR ica ~ '^[0-9]{6}$')
);

COMMENT ON TABLE  bank_mc_sms IS
    'Identifiants Mastercard SMS de la banque. Valeurs assignees par Mastercard.';
COMMENT ON COLUMN bank_mc_sms.processor_id IS
    'DE33 - format 9000xxxxxx, 10 chiffres exactement';
COMMENT ON COLUMN bank_mc_sms.acquiring_inst_id IS
    'DE32 - 9 chiffres exactement, utilise a partir des 0200';
COMMENT ON COLUMN bank_mc_sms.ica IS
    'Interbank Card Association - 6 chiffres';
COMMENT ON COLUMN bank_mc_sms.message_security_code IS
    'DE96 - 8 octets en hexadecimal (16 caracteres)';

-- ------------------------------------------------------------
--  Bouchons de developpement — VALEURS A REMPLACER
-- ------------------------------------------------------------
INSERT INTO bank (code, name, country_code, description)
VALUES ('BANQUE_TEST', 'Banque de test ScenarioGenerator', '504',
        'BOUCHON DEV')
ON CONFLICT (code) DO NOTHING;

INSERT INTO bank_mc_sms (bank_id, processor_id, acquiring_inst_id, ica)
SELECT id, '9000000001', '000000001', '000001'
FROM bank WHERE code = 'BANQUE_TEST'
ON CONFLICT (bank_id) DO NOTHING;

-- ------------------------------------------------------------
--  Droits
-- ------------------------------------------------------------
GRANT SELECT ON bank        TO mc_sms_acquirer_user;
GRANT SELECT ON bank_mc_sms TO mc_sms_acquirer_user;
GRANT SELECT ON bank        TO mc_sms_issuer_user;
GRANT SELECT ON bank_mc_sms TO mc_sms_issuer_user;

-- ------------------------------------------------------------
--  Verification
-- ------------------------------------------------------------
SELECT b.code, b.name, m.processor_id, m.acquiring_inst_id, m.ica
FROM bank b
JOIN bank_mc_sms m ON m.bank_id = b.id;
