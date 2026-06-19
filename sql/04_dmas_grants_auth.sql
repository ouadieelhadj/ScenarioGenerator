-- ═══════════════════════════════════════════════════════════
-- DMAS — GRANTs complémentaires pour l'auth JWT
-- Les modules dmas-* lisent la table users au login.
-- Lecture seule : ils n'écrivent jamais dans users.
-- ═══════════════════════════════════════════════════════════

\connect scenariogenerator

GRANT SELECT ON users      TO dmas_acquirer_user, dmas_issuer_user;
GRANT SELECT ON tests      TO dmas_acquirer_user, dmas_issuer_user;
GRANT SELECT ON user_tests TO dmas_acquirer_user, dmas_issuer_user;

-- last_login est mis à jour à chaque login réussi -> besoin de UPDATE sur users
GRANT UPDATE ON users TO dmas_acquirer_user, dmas_issuer_user;
