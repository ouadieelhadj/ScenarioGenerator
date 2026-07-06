-- Migration v1.2.0 (complement) : direction naturelle des types de message
BEGIN;

-- Colonne direction. Defaut ACQ_TO_ISS (le cas le plus courant : sens montant).
ALTER TABLE message_types
  ADD COLUMN IF NOT EXISTS direction VARCHAR(12) NOT NULL DEFAULT 'ACQ_TO_ISS';

-- Backfill : les messages reseau (gestion de session) sont bidirectionnels.
UPDATE message_types SET direction = 'BOTH'
  WHERE category = 'NETWORK';

-- Les autres (AUTHORIZATION, FINANCIAL, REVERSAL) restent ACQ_TO_ISS
-- (deja pose par le DEFAULT). Les types descendants (1120/1422) seront
-- inseres plus tard en ISS_TO_ACQ quand on les ajoutera.

COMMIT;
