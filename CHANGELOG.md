# Changelog

Toutes les evolutions notables de la plateforme ScenarioGenerator sont consignees ici.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/)
et le versioning suit [SemVer](https://semver.org/lang/fr/) (MAJEUR.MINEUR.CORRECTIF).

Le guide utilisateur (docs/Guide-Utilisateur-ScenarioGenerator.docx) porte la meme
version que la plateforme et est mis a jour conjointement.

## [1.1.0] - 2026-06-27

Tirage de cartes reelles + PIN dans les campagnes.

### Ajoute
- Mode **tirage aleatoire de cartes** : une campagne avec `"DE002_PAN_MODE":"RANDOM"`
  dans son config tire au hasard, par transaction, parmi les cartes ACTIVE de `dmas_cards`
  (au lieu d'un PAN fixe). Trafic varie et realiste.
- Option **`"WITH_PIN":true`** : chiffre le PIN block (DE52) sous PEK pour chaque carte tiree
  (reutilise le chiffrement du flux d'autorisation normal) + montant reel.
- McDmasAuthorization.buildAuth0100WithPin (variante avec PIN du builder load test).
- LoadTestRequest accepte une liste de cartes (PAN+PIN) + flag withPin ; LoadTestService
  tire une carte au hasard par transaction et remonte le PAN utilise (TxDetail.pan).
- sql/16 : GRANT SELECT sur dmas_cards a scenario_user (lecture par l'orchestrateur).

### Note
- Le mode WITH_PIN requiert une PEK ACTIVE (key exchange prealable).
- Valide : campagne 3 paliers, 7 cartes reelles tirees aleatoirement, PIN chiffre par carte,
  71/71 approuvees, repartition equilibree, verdict SLA fonctionnel.

## [1.0.0] - 2026-06-26

Premier jalon stable : moteur de test de charge complet + modele Campagne.

### Ajoute
- Moteur de test de charge sur la connexion permanente jPOS (concurrence, tableau de bord DE39 temps reel, suivi par transaction).
- Load test orchestre de bout en bout (Execution + Result + rapports Excel/PDF).
- Modele **Campagne** : definition reutilisable, paliers de charge multiples (montee/plateau/descente), criteres SLA et **verdict PASSED/FAILED**.
- Endpoint POST /api/campaigns/{id}/run (lancement multi-paliers asynchrone).
- Guide utilisateur (Word) couvrant toutes les actions + exemple de bout en bout + distinction generateur/orchestrateur.

### Modifie
- STAN rendu globalement unique via compteur atomique partage (suppression des collisions).

### Supprime
- Ancien sous-systeme Campagne/Replay redondant (unification du modele).

## [0.4.0] - 2026-06-26
### Supprime
- Sous-systeme Campagne/Replay : unification sur le Test (preparation du nouveau modele Campagne).

## [0.3.0] - 2026-06-26
### Ajoute
- Load test orchestre + rapports Excel/PDF.
### Corrige
- STAN unique (compteur atomique partage).

## [0.2.0] - 2026-06-26
### Ajoute
- Flux orchestre sur jPOS + scripts de test.
- Moteur de test de charge (premiere version).

## [0.1.0] - 2026-06-26
### Ajoute
- Flux jPOS de base : echange de cle PEK, autorisation directe sur la connexion permanente.

[1.0.0]: https://github.com/ouadieelhadj/ScenarioGenerator/releases/tag/v1.0.0
[1.1.0]: https://github.com/ouadieelhadj/ScenarioGenerator/releases/tag/v1.1.0
