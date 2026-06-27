-- v1.1.0 : l'orchestrateur (scenario_user) doit lire dmas_cards pour le tirage
-- aleatoire de cartes dans les campagnes (mode DE002_PAN_MODE=RANDOM).
-- La table appartient a dmas_issuer_user ; on accorde la lecture seule.
GRANT SELECT ON dmas_cards TO scenario_user;
