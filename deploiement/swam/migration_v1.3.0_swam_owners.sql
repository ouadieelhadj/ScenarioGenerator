-- Propriete des tables swam_* aux users dedies (miroir DMAS)
BEGIN;

-- ISSUER possede ses tables + leurs sequences
ALTER TABLE swam_cards            OWNER TO swam_issuer_user;
ALTER TABLE swam_iss_transactions OWNER TO swam_issuer_user;
ALTER TABLE swam_iss_keys         OWNER TO swam_issuer_user;
ALTER SEQUENCE swam_cards_id_seq            OWNER TO swam_issuer_user;
ALTER SEQUENCE swam_iss_transactions_id_seq OWNER TO swam_issuer_user;
ALTER SEQUENCE swam_iss_keys_id_seq         OWNER TO swam_issuer_user;

-- ACQUIRER possede ses tables + leurs sequences
ALTER TABLE swam_acq_transactions OWNER TO swam_acquirer_user;
ALTER TABLE swam_acq_keys         OWNER TO swam_acquirer_user;
ALTER SEQUENCE swam_acq_transactions_id_seq OWNER TO swam_acquirer_user;
ALTER SEQUENCE swam_acq_keys_id_seq         OWNER TO swam_acquirer_user;

-- KEK reste a postgres (partagee, comme dmas_kek)

-- Re-appliquer les grants croises (le changement d'owner ne les retire pas,
-- mais on s'assure que chaque user garde l'acces a ce dont il a besoin).
GRANT SELECT                                                 ON swam_cards            TO swam_acquirer_user;
GRANT SELECT                                                 ON swam_iss_transactions TO swam_acquirer_user;
GRANT SELECT                                                 ON swam_acq_keys         TO swam_issuer_user;
GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON swam_kek             TO swam_issuer_user, swam_acquirer_user;
GRANT USAGE,SELECT ON swam_kek_id_seq TO swam_issuer_user, swam_acquirer_user;

COMMIT;
