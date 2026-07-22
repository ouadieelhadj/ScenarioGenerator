-- ============================================================
--  V4__emv_mdk_and_cards.sql — LOT A du chantier EMV / 0100
--
--  1. key_label : identifiant lisible sur les trois tables de cles
--  2. contrainte d'unicite corrigee pour inclure bank_code
--     (sinon deux banques ne peuvent avoir chacune leur MDK)
--  3. key_type porte a 8 caracteres (MDK, ZMK, TPK... a l'aise)
--  4. colonnes EMV sur mc_dmas_cards, pour alimenter le DE55
--
--  La MDK elle-meme n'est PAS inseree ici : elle doit etre
--  chiffree sous LMK par le HSM, via l'endpoint mdk/bootstrap.
-- ============================================================

-- ------------------------------------------------------------
--  1. Identifiant lisible par cle
-- ------------------------------------------------------------
ALTER TABLE mc_dmas_mastercard_keys ADD COLUMN IF NOT EXISTS key_label VARCHAR(32);
ALTER TABLE mc_dmas_member_keys     ADD COLUMN IF NOT EXISTS key_label VARCHAR(32);
ALTER TABLE mc_dmas_kek             ADD COLUMN IF NOT EXISTS key_label VARCHAR(32);

COMMENT ON COLUMN mc_dmas_mastercard_keys.key_label IS
    'Identifiant lisible, ex. MDK-022905, PEK-022906';

-- Renseigner les cles existantes : <TYPE>-<BANQUE>
UPDATE mc_dmas_mastercard_keys
   SET key_label = key_type || '-' || COALESCE(bank_code, 'XXXXXX')
 WHERE key_label IS NULL;
UPDATE mc_dmas_member_keys
   SET key_label = key_type || '-' || COALESCE(bank_code, 'XXXXXX')
 WHERE key_label IS NULL;
UPDATE mc_dmas_kek
   SET key_label = 'KEK-' || COALESCE(bank_code, 'XXXXXX')
 WHERE key_label IS NULL;

-- ------------------------------------------------------------
--  2. key_type : 3 -> 8 caracteres
-- ------------------------------------------------------------
ALTER TABLE mc_dmas_mastercard_keys ALTER COLUMN key_type TYPE VARCHAR(8);
ALTER TABLE mc_dmas_member_keys     ALTER COLUMN key_type TYPE VARCHAR(8);

-- ------------------------------------------------------------
--  3. Unicite par BANQUE
--
--  Sans bank_code, deux banques partageant un member_group_id
--  ne pourraient pas avoir chacune leur cle du meme type ACTIVE.
-- ------------------------------------------------------------
ALTER TABLE mc_dmas_mastercard_keys DROP CONSTRAINT IF EXISTS uq_dmas_iss_keys;
ALTER TABLE mc_dmas_mastercard_keys
    ADD CONSTRAINT uq_dmas_iss_keys
    UNIQUE (bank_code, member_group_id, key_type, status);

ALTER TABLE mc_dmas_member_keys DROP CONSTRAINT IF EXISTS uq_dmas_acq_keys;
ALTER TABLE mc_dmas_member_keys
    ADD CONSTRAINT uq_dmas_acq_keys
    UNIQUE (bank_code, member_group_id, key_type, status);

-- ------------------------------------------------------------
--  4. Donnees EMV de la carte, pour construire le DE55
--
--  Ce qui provient de la carte (le reste est calcule ou
--  parametre au moment de la transaction).
-- ------------------------------------------------------------
ALTER TABLE mc_dmas_cards ADD COLUMN IF NOT EXISTS emv_aid          VARCHAR(32);   -- tag 84
ALTER TABLE mc_dmas_cards ADD COLUMN IF NOT EXISTS emv_aip          VARCHAR(4);    -- tag 82
ALTER TABLE mc_dmas_cards ADD COLUMN IF NOT EXISTS emv_psn          VARCHAR(2);    -- PAN Sequence Number
ALTER TABLE mc_dmas_cards ADD COLUMN IF NOT EXISTS emv_atc          INTEGER DEFAULT 0;  -- tag 9F36, incremente par carte
ALTER TABLE mc_dmas_cards ADD COLUMN IF NOT EXISTS emv_app_version  VARCHAR(4);    -- tag 9F09
ALTER TABLE mc_dmas_cards ADD COLUMN IF NOT EXISTS emv_iad          VARCHAR(64);   -- tag 9F10
ALTER TABLE mc_dmas_cards ADD COLUMN IF NOT EXISTS emv_cvm_results  VARCHAR(6);    -- tag 9F34

COMMENT ON COLUMN mc_dmas_cards.emv_aid         IS 'Tag 84  — Application Identifier';
COMMENT ON COLUMN mc_dmas_cards.emv_aip         IS 'Tag 82  — Application Interchange Profile';
COMMENT ON COLUMN mc_dmas_cards.emv_psn         IS 'PAN Sequence Number, entre dans la derivation de la cle ICC';
COMMENT ON COLUMN mc_dmas_cards.emv_atc         IS 'Tag 9F36 — Application Transaction Counter, incremente par carte a chaque transaction';
COMMENT ON COLUMN mc_dmas_cards.emv_app_version IS 'Tag 9F09 — Application Version Number';
COMMENT ON COLUMN mc_dmas_cards.emv_iad         IS 'Tag 9F10 — Issuer Application Data';

-- Valeurs par defaut inspirees de la trace EMVCo (carte Maestro/M-Chip)
UPDATE mc_dmas_cards SET
    emv_aid         = COALESCE(emv_aid,         'A0000000043060'),
    emv_aip         = COALESCE(emv_aip,         '1B80'),
    emv_psn         = COALESCE(emv_psn,         '00'),
    emv_app_version = COALESCE(emv_app_version, '0002'),
    emv_iad         = COALESCE(emv_iad,         '0110A0000000000000000000000000'),
    emv_cvm_results = COALESCE(emv_cvm_results, '010002')
WHERE emv_aid IS NULL OR emv_aip IS NULL;

-- ------------------------------------------------------------
--  5. Verification
-- ------------------------------------------------------------
SELECT 'key_type longueur' AS controle,
       (SELECT count(*) FROM information_schema.columns
        WHERE table_name='mc_dmas_mastercard_keys'
          AND column_name='key_type'
          AND character_maximum_length=8) AS ok;

SELECT bank_code, key_type, key_label, kcv, status
FROM mc_dmas_mastercard_keys ORDER BY bank_code, key_type;
