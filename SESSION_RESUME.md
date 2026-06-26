# SESSION RESUME — ScenarioGenerator

> Fichier de reprise de session : etat d'avancement, ce qui est en cours, ce qui reste.
> A mettre a jour a la fin de chaque session de travail.

**Derniere mise a jour :** 2026-06-26
**Branche :** feature/multi-module
**Version courante :** v1.0.0 (taguee) — v1.1.0 en cours

---

## 1. Etat de la plateforme (v1.0.0 — publie + tague)

Plateforme de simulation et test de charge monetique Mastercard DMAS.

**Modules :**
- `sg-generator-orchestrator` (8080) — orchestrateur : tests, executions, campagnes, users, rapports
- `sg-dmas-acquirer` (8084 REST / 8600 jPOS) — acquereur : autorisations + moteur de charge
- `sg-dmas-issuer` (8501) — emetteur : cartes, sign-on, reponses
- `sg-common` — entites / repositories / DTOs partages

**Acquis v1.0.0 :**
- Moteur de test de charge sur connexion permanente jPOS (concurrence, STAN unique, tableau de bord DE39)
- Load test orchestre de bout en bout (Execution + Result + rapports Excel/PDF)
- Modele Campagne : 4 tables (campaigns, campaign_load_steps, campaign_executions, campaign_execution_results)
  - multi-paliers (montee/plateau/descente joues sequentiellement)
  - criteres SLA + verdict PASSED/FAILED
  - endpoint POST /api/campaigns/{id}/run
- Guide utilisateur Word (docs/), versionne, avec distinction generateur/orchestrateur
- Versioning SemVer en place : VERSION + CHANGELOG.md + tag git, synchronises avec le guide

---

## 2. EN COURS — v1.1.0 : tirage de cartes reelles + PIN

**Objectif :** au lieu d'envoyer toujours la meme transaction (PAN fixe), une campagne peut
tirer au hasard parmi les cartes reelles de `dmas_cards`, et chiffrer le PIN par carte.

**Conception validee :**
- Mode dans le `config` JSON de la campagne (fait partie de la definition) :
  - `"DE002_PAN_MODE": "RANDOM"` -> tire au hasard dans dmas_cards (ACTIVE, solde > 0)
  - `"WITH_PIN": true` -> chiffre le PIN block (DE52) sous PEK + montant reel
  - sinon -> comportement v1.0.0 (PAN fixe, sans PIN)
- Tirage par transaction cote acquereur (LoadTestService)
- La campagne fournit le pool de cartes (PAN+PIN) a l'acquereur
- Reutilise le chiffrement PIN existant du flux simple : hsm.encryptPinBlock(pin, pan, pek...)

**Avancement etape par etape :**
- [FAIT] Etape 1 : McDmasAuthorization.buildAuth0100WithPin(pan, pin, amount, entryMode, stan)
  — reutilise hsm.encryptPinBlock + PEK ACTIVE. Compile ACQ:0.
- [FAIT] Etape 2 : LoadTestRequest enrichi (withPin + List<CardEntry> cards) ;
  LoadTestService.submitOne tire une carte au hasard par transaction (ThreadLocalRandom)
  et appelle buildAuth0100WithPin ou buildAuth0100 selon withPin. Compile ACQ:0.
- [EN COURS] Etape 3 : CampaignRunService — injection DmasCardRepository [FAIT],
  lecture DE002_PAN_MODE + WITH_PIN du config, chargement du pool de cartes,
  ajout de cards + withPin dans le body POST /loadtest. PATCH APPLIQUE,
  COMPILE A CONFIRMER (ORCH:?).
- [A FAIRE] Etape 4 : test bout-en-bout d'une campagne RANDOM + WITH_PIN
  (prerequis : sign-on + key exchange pour avoir une PEK ACTIVE).
- [A FAIRE] Etape 5 : versioning v1.1.0 (VERSION=1.1.0, CHANGELOG, guide, tag v1.1.0).

**Point d'attention :** WITH_PIN exige une PEK ACTIVE (key exchange prealable).
Prevoir une verification au lancement (refus clair si pas de PEK).

**Cartes de test disponibles dans dmas_cards :** 7 cartes ACTIVE avec solde
(BINs 532196, 541333, 513330...). PAN principal : 5321962145453348, PIN 1234.

---

## 3. RESTE A FAIRE (iterations futures)

**v1.2.0 (pressenti) — Conditions d'arret (circuit breaker)**
- colonne stop_on_error_rate deja prete dans campaigns
- arret auto si trop d'erreurs sur une fenetre

**Champs variables / generation realiste**
- logique sauvee dans docs/generation-variee-TODO.md (PAN+Luhn, montants min/max, DE43,
  processing codes, POS entry/condition par canal...)
- a reintegrer dans le parametrage de campagne

**RBAC a corriger** (bugs identifies)
- TESTEUR a TPS_CREATE mais bloque par le filtre URL /api/admin/**
- permissions TPS_RUN/CAMPAIGN_* non branchees sur ExecutionController/CampaignController
- seul /api/admin/** est reellement protege
- prevoir script bout-en-bout RBAC (utilisateur1 + utilisateur2)

**CRUD campagnes via API**
- actuellement creation en SQL ; manque POST /api/campaigns (creer/editer une campagne)

**Dette technique / divers**
- LoadTestOrchestrationService (monde Test) redondant avec CampaignRunService -> a deprecier ?
- LoadTestOrchestrationService ne joue que le 1er step (le multi-paliers est dans CampaignRunService)
- Persistance acq_authorizations / iss_authorizations
- Basculer McAdviceManager (0120) / McReversalManager (0400) sur jPOS
- Renommage cosmetique eventuel des libelles Test -> Campagne

---

## 4. INFOS PRATIQUES (rappel)

**Canal SQL (Git Bash) :**
```
alias sgsql='PGPASSWORD=postgres123 "/d/MoneyCore/PostgreSQL/18/bin/psql.exe" -U postgres -h localhost -d scenariogenerator'
```
(a redefinir a chaque terminal ; user postgres ou scenario_user, pwd postgres123)

**Login (tous modules) :** admin / Admin123! (endpoint /auth/login, champ "login")

**Compile (filtre warnings Maven) :**
```
cd MODULE && mvn install -q -DskipTests 2>&1 | grep -viE "^WARNING" ; echo "=== EXIT: ${PIPESTATUS[0]} ==="
```
Ordre : sg-common AVANT issuer/acquirer/orchestrator.

**ddl-auto = validate** (issuer + orchestrator) : chaque entite doit correspondre
exactement a une table existante. Ne jamais mettre update.

**Prerequis flux jPOS :** sign-on issuer (POST 8501/api/admin/dmas/jpos/signon) AVANT
toute transaction. Pour le PIN : key exchange prealable (PEK ACTIVE).

**Endpoints cles :**
- POST 8501/api/admin/dmas/jpos/signon | echo ; POST 8501/api/admin/dmas/cards
- POST 8084/api/admin/dmas/loadtest + GET .../loadtest/{id}/status?details=true
- POST 8080/api/campaigns/{id}/run
- POST 8080/api/executions/start/{id}?mode=SIMPLE | loadtest/{id} ; GET .../{id}/status

**Versioning :** a chaque evolution, incrementer ensemble VERSION + CHANGELOG.md +
version du guide (docs/), puis git tag vX.Y.Z. SemVer, doc synchronise avec la plateforme.

---

## 5. HISTORIQUE DES COMMITS DE LA SESSION (feature/multi-module)

- 256370b  moteur de test de charge (connexion permanente jPOS)
- f824f28  STAN globalement unique (compteur atomique partage)
- a4a9d78  load test orchestre + rapports Excel/PDF
- 006b409  suppression du sous-systeme Campagne/Replay (unification)
- 411a3e2  modele Campagne v1 (multi-paliers + SLA/verdict + rapports)
- 7945c96  release v1.0.0 (versioning + guide utilisateur)  [tag v1.0.0]
- (v1.1.0 en cours, pas encore commite)
