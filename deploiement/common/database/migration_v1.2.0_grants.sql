-- Grants sur la table networks (neuve) pour scenario_user (orchestrateur).
-- networks est un referentiel en LECTURE cote orchestrateur ; SELECT suffit,
-- mais on ajoute INSERT/UPDATE/DELETE pour un futur ecran de gestion.
BEGIN;
GRANT SELECT, INSERT, UPDATE, DELETE ON networks TO scenario_user;
GRANT USAGE, SELECT ON SEQUENCE networks_id_seq TO scenario_user;
COMMIT;
