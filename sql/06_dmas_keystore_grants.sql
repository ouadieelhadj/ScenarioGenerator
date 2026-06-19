-- GRANT key_store pour les 2 users DMAS (l'émetteur écrit les PEK/MAK importées)
\connect scenariogenerator

GRANT SELECT, INSERT, UPDATE ON key_store TO dmas_acquirer_user, dmas_issuer_user;
GRANT USAGE, SELECT ON key_store_id_seq TO dmas_acquirer_user, dmas_issuer_user;
