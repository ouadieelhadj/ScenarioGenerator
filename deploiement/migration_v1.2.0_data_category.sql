-- Corrige la category heritee 'DMAS' -> 'AUTHORIZATION' (campagnes = autorisations 0100)
BEGIN;
UPDATE campaigns SET category = 'AUTHORIZATION' WHERE category = 'DMAS';
COMMIT;
