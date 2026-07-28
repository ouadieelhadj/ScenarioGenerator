\connect scenariogenerator

-- Droits finaux rejouables après une reconstruction SQL ou une restauration.
-- Les migrations métier conservent les droits fins. Ce fichier garantit les
-- accès transverses indispensables au démarrage des services.

GRANT CONNECT ON DATABASE scenariogenerator TO
    scenario_user,
    dmas_acquirer_user,
    dmas_issuer_user,
    swam_issuer_user,
    swam_acquirer_user,
    swam_lis_member_user,
    swam_lis_switch_user;

GRANT USAGE ON SCHEMA public TO
    scenario_user,
    dmas_acquirer_user,
    dmas_issuer_user,
    swam_issuer_user,
    swam_acquirer_user,
    swam_lis_member_user,
    swam_lis_switch_user;

GRANT SELECT, INSERT, UPDATE, DELETE ON swam_interface
TO swam_issuer_user, swam_acquirer_user;

GRANT SELECT ON networks TO swam_issuer_user, swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_kek
TO swam_issuer_user, swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_iss_keys, swam_iss_transactions, swam_cards
TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_acq_keys, swam_acq_transactions
TO swam_acquirer_user;
GRANT SELECT ON swam_cards TO swam_acquirer_user;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO
    scenario_user,
    dmas_acquirer_user,
    dmas_issuer_user,
    swam_issuer_user,
    swam_acquirer_user,
    swam_lis_member_user,
    swam_lis_switch_user;
