-- =====================================================================
-- Migration v1.2.0 : socle multi-reseau
-- Additif et idempotent. Ne modifie aucune donnee DMAS existante.
-- =====================================================================
BEGIN;

-- 1. Colonne network sur message_types (defaut DMAS pour l'existant)
ALTER TABLE message_types ADD COLUMN IF NOT EXISTS network VARCHAR(20) NOT NULL DEFAULT 'DMAS';

-- 2. Colonnes network + initiator sur campaigns
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS network   VARCHAR(20) NOT NULL DEFAULT 'DMAS';
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS initiator VARCHAR(20) NOT NULL DEFAULT 'ACQUIRER';

-- 3. Lignes SWAM dans message_types (idempotent : insere seulement si absent)
--    category reste reseau-agnostique (memes libelles que DMAS).
--    processing_codes = DE3 tires de la spec HPS SID v3.20 (positions 1-2).

INSERT INTO message_types (code, name, category, network, description, processing_codes, active, created_at)
SELECT '1100', 'Demande d''autorisation', 'AUTHORIZATION', 'SWAM',
       'Autorisation SWAM (PowerCARD HSID)',
       '[{"code":"000000","label":"Achat de biens & services"},{"code":"010000","label":"Cash advance"},{"code":"170000","label":"Cash"},{"code":"310000","label":"Demande de solde"},{"code":"960000","label":"Achat sur GAB"}]',
       true, now()
WHERE NOT EXISTS (SELECT 1 FROM message_types WHERE network='SWAM' AND code='1100');

INSERT INTO message_types (code, name, category, network, description, processing_codes, active, created_at)
SELECT '1200', 'Demande de transaction financiere', 'FINANCIAL', 'SWAM',
       'Transaction financiere SWAM (PowerCARD HSID)',
       '[{"code":"000000","label":"Achat de biens & services"},{"code":"010000","label":"Cash advance"},{"code":"310000","label":"Demande de solde"}]',
       true, now()
WHERE NOT EXISTS (SELECT 1 FROM message_types WHERE network='SWAM' AND code='1200');

INSERT INTO message_types (code, name, category, network, description, processing_codes, active, created_at)
SELECT '1420', 'Avis d''annulation acquereur', 'REVERSAL', 'SWAM',
       'Reversal / annulation acquereur SWAM',
       '[{"code":"000000","label":"Annulation achat"},{"code":"010000","label":"Annulation cash advance"}]',
       true, now()
WHERE NOT EXISTS (SELECT 1 FROM message_types WHERE network='SWAM' AND code='1420');

INSERT INTO message_types (code, name, category, network, description, processing_codes, active, created_at)
SELECT '1804', 'Demande de gestion de reseau', 'NETWORK', 'SWAM',
       'Gestion reseau SWAM (DE24 : 801 sign-on, 803 echo, 802 sign-off)',
       '[{"code":"801","label":"Ouverture de session"},{"code":"803","label":"Echo test"},{"code":"802","label":"Fermeture de session"}]',
       true, now()
WHERE NOT EXISTS (SELECT 1 FROM message_types WHERE network='SWAM' AND code='1804');

COMMIT;
