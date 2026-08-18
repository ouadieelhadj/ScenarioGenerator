# Gouvernance du graphe et de l’IA

Les politiques sont isolées par `member_id` issu du JWT et par `sector_id`. Elles sont administrées avec le scope OAuth2 `fraud.admin`. Aucun secret, identifiant brut ou donnée sensible n’est accepté dans ces paramètres.

## Politique graphe

Endpoint : `GET|PUT /api/fraud/v1/admin/governance/graph/{sectorId}`.

La politique détermine l’activation, les types d’entités autorisés, l’analyse intersectorielle, la taille minimale d’un groupe, la contribution au score, la fenêtre d’observation et le nombre minimal d’observations. Le graphe local PostgreSQL effectue actuellement une corrélation directe à un niveau. `maximumHops` prépare le raccordement JanusGraph ; il ne constitue pas une preuve de parcours multi-niveaux dans PostgreSQL.

Les sujets sont pseudonymisés et typés selon le parcours : `CARD_TOKEN`, `ACCOUNT`, `WALLET` ou `CUSTOMER`. L’enrôlement générique utilise `POST /api/fraud/v1/subjects/monitoring-enrollments`; l’ancien enrôlement carte reste compatible et crée également le sujet générique correspondant. Les relations possibles sont `DEVICE`, `CUSTOMER`, `ACCOUNT`, `BENEFICIARY`, `MERCHANT` et `IP`.

## Politique IA

Endpoint : `GET|PUT /api/fraud/v1/admin/governance/ai/{sectorId}`.

- `SHADOW` : le Champion et éventuellement le Challenger sont évalués sans modifier la décision déterministe.
- `ACTIVE` : seul le Champion peut augmenter le score opérationnel ; le Challenger reste toujours en observation.
- `challengerTrafficPercent` : sélection déterministe d’une part des transactions pour le Challenger.
- `driftStatus=DRIFTED` ou `driftScore` supérieur au seuil : retour automatique au moteur déterministe.
- `explainabilityRequired=true` : une prédiction sans explication est refusée et déclenche le fallback.
- `analystApprovalRequired=true` est obligatoire et ne peut pas être désactivé.
- les seuils `ALERT`, `CHALLENGE`, `HOLD` et `BLOCK` doivent être ordonnés.

Les critères de précision, rappel et faux positifs sont les portes de promotion attendues du pipeline de validation. La version actuelle les conserve en base mais ne promeut jamais automatiquement un Challenger : la promotion reste une décision humaine explicite après backtest.

## Repli sécurisé

Le moteur déterministe reste disponible si le modèle est désactivé, en dérive, indisponible, retourne un score invalide ou ne fournit pas l’explication obligatoire. Les raisons, le mode de gouvernance, les scores Shadow et le fallback sont conservés dans le snapshot de caractéristiques, sans référence métier en clair.
