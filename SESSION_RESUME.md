# SESSION RESUME — ScenarioGenerator

> Fichier de reprise de session : etat d'avancement, ce qui est en cours, ce qui reste.
> A mettre a jour a la fin de chaque session de travail.

**Derniere mise a jour :** 2026-06-27
**Branche :** feature/multi-module
**Version courante :** v1.1.0 (taguee, publiee)

---

## 1. Etat de la plateforme

Plateforme de simulation et test de charge monetique Mastercard DMAS.

**Modules :**
- `sg-generator-orchestrator` (8080) — orchestrateur : tests, executions, campagnes, users, rapports
- `sg-dmas-acquirer` (8084 REST / 8600 jPOS) — acquereur : autorisations + moteur de charge
- `sg-dmas-issuer` (8501) — emetteur : cartes, sign-on, reponses
- `sg-common` — entites / repositories / DTOs partages

**Acquis (jusqu'a v1.1.0) :**
- Moteur de test de charge sur connexion permanente jPOS (concurrence, STAN unique, tableau DE39)
- Load test orchestre de bout en bout (Execution + Result + rapports Excel/PDF)
- Modele Campagne : 4 tables (campaigns, campaign_load_steps, campaign_executions, campaign_execution_results)
  - multi-paliers (montee/plateau/descente)
  - criteres SLA + verdict PASSED/FAILED
  - endpoint POST /api/campaigns/{id}/run
- **v1.1.0 : tirage de cartes reelles + PIN**
  - config campagne : "DE002_PAN_MODE":"RANDOM" -> tire au hasard dans dmas_cards (ACTIVE, solde>0)
  - config campagne : "WITH_PIN":true -> chiffre le PIN block (DE52) sous PEK par carte + montant reel
  - le PAN reellement utilise est trace dans les resultats
  - prerequis WITH_PIN : PEK ACTIVE (key exchange prealable)
- Guide utilisateur Word (docs/), versionne, synchronise avec la plateforme
- Versioning SemVer : VERSION + CHANGELOG.md + tags git (v1.0.0, v1.1.0)

---

## 2. RESTE A FAIRE (iterations futures)

**Conditions d'arret (circuit breaker)**
- colonne stop_on_error_rate deja prete dans campaigns
- arret auto si trop d'erreurs sur une fenetre

**Champs variables / generation realiste (au-dela du PAN)**
- logique sauvee dans docs/generation-variee-TODO.md (montants min/max, DE43, processing codes,
  POS entry/condition par canal...) — le PAN est deja gere via le tirage de cartes v1.1.0
- a reintegrer dans le parametrage de campagne (config par champ : fixe / variable)

**RBAC a corriger** (bugs identifies)
- TESTEUR a TPS_CREATE mais bloque par le filtre URL /api/admin/**
- permissions TPS_RUN/CAMPAIGN_* non branchees sur ExecutionController/CampaignController
- seul /api/admin/** est reellement protege
- prevoir script bout-en-bout RBAC (utilisateur1 + utilisateur2)

**CRUD campagnes via API**
- actuellement creation en SQL ; manque POST /api/campaigns (creer/editer une campagne via API)

**Dette technique / divers**
- LoadTestOrchestrationService (monde Test) redondant avec CampaignRunService -> a deprecier ?
- LoadTestOrchestrationService ne joue que le 1er step (multi-paliers seulement dans CampaignRunService)
- Persistance acq_authorizations / iss_authorizations
- Basculer McAdviceManager (0120) / McReversalManager (0400) sur jPOS
- Recharge des cartes : avec WITH_PIN + montant reel, les soldes baissent ; prevoir un endpoint/mecanisme de recharge en masse
- Renommage cosmetique eventuel des libelles Test -> Campagne

---

## 3. INFOS PRATIQUES (rappel)

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

**ddl-auto = validate** (issuer + orchestrator) : chaque entite doit correspondre a une table existante.

**Prerequis flux jPOS :** sign-on issuer (POST 8501/api/admin/dmas/jpos/signon) AVANT toute transaction.
Pour le PIN (WITH_PIN) : key exchange prealable (PEK ACTIVE). Verifier :
  sgsql -c "SELECT member_group_id, key_type, status FROM dmas_acq_keys WHERE key_type='PEK' AND status='ACTIVE';"

**Cartes de test (dmas_cards) :** 7 cartes ACTIVE (BINs 532196, 541333, 513330...). PAN principal 5321962145453348, PIN 1234.
La table appartient a dmas_issuer_user ; scenario_user a recu GRANT SELECT (sql/16).

**Endpoints cles :**
- POST 8501/api/admin/dmas/jpos/signon | echo ; POST 8501/api/admin/dmas/cards
- POST 8084/api/admin/dmas/loadtest + GET .../loadtest/{id}/status?details=true
- POST 8080/api/campaigns/{id}/run
- POST 8080/api/executions/start/{id}?mode=SIMPLE | loadtest/{id} ; GET .../{id}/status

**Exemple campagne RANDOM + PIN (SQL) :**
```
INSERT INTO campaigns (name, description, category, config, active, created_by, sla_p95_max_ms, sla_error_rate_max, sla_approval_min)
VALUES ('CAMP-RANDOM-PIN', 'tirage + PIN', 'DMAS',
        '{"DE002_PAN_MODE":"RANDOM","WITH_PIN":true,"DE004_AMOUNT":1000}', TRUE,
        (SELECT id FROM users WHERE login='admin'), 600, 10.00, 80.00) RETURNING id;
-- puis campaign_load_steps (campaign_id, step_order, start_seconds, end_seconds, tps_value)
```

**Versioning :** a chaque evolution, incrementer ensemble VERSION + CHANGELOG.md + version du guide (docs/),
committer le code AVEC, puis git tag vX.Y.Z. SemVer, doc synchronise avec la plateforme.

---

## 4. HISTORIQUE DES VERSIONS (tags)

- v1.0.0 (7945c96) — moteur de charge + modele Campagne (multi-paliers + SLA/verdict) + guide + versioning
- v1.1.0 (a48f029) — tirage de cartes reelles + PIN dans les campagnes

## 5. DERNIERS COMMITS (feature/multi-module)

- 411a3e2  modele Campagne v1 (multi-paliers + SLA/verdict + rapports)
- 7945c96  release v1.0.0 (versioning + guide)  [tag v1.0.0]
- a0cb0bc  SESSION_RESUME.md (suivi)
- a48f029  v1.1.0 tirage de cartes reelles + PIN  [tag v1.1.0]
