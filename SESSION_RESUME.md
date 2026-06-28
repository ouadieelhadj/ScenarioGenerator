# SESSION RESUME — ScenarioGenerator

> Fichier de reprise de session : etat d'avancement, ce qui est en cours, ce qui reste.

**Derniere mise a jour :** 2026-06-28
**Branche :** feature/multi-module
**Version courante :** v1.1.0 (taguee, publiee)

## 1. Etat de la plateforme

Plateforme de simulation et test de charge monetique Mastercard DMAS.

Modules :
- sg-generator-orchestrator (8080) : orchestrateur (tests, executions, campagnes, users, rapports)
- sg-dmas-acquirer (8084 REST / 8600 jPOS) : acquereur (autorisations + moteur de charge)
- sg-dmas-issuer (8501) : emetteur (cartes, sign-on, reponses)
- sg-common : entites / repositories / DTOs partages

Acquis jusqu'a v1.1.0 :
- Moteur de test de charge jPOS (concurrence, STAN unique, DE39)
- Load test orchestre E2E (Execution + Result + rapports Excel/PDF)
- Modele Campagne (multi-paliers, SLA, verdict PASSED/FAILED, POST /api/campaigns/{id}/run)
- v1.1.0 : tirage cartes reelles (DE002_PAN_MODE=RANDOM) + PIN (WITH_PIN, DE52 sous PEK)

Fait en session 2026-06-28 :
- Recharge cartes : UPDATE dmas_cards balance vers cible (100000000 = 1 000 000,00 USD), cartes ACTIVE sous la cible. Reutilisable.
- RBAC corrige + teste E2E + COMMITE (ff9b0a7, local, PAS encore push) :
  - CAMPAIGN_GENERATE sur CampaignController.run
  - TPS_RUN sur ExecutionController.start / loadtest / stop
  - Test 2 profils : OBSERVATEUR=403 ; EXPLOITATION=200 (run), 500 (start, prereq jPOS hors RBAC)
- Role TESTEUR supprime (inutilise) ; roles restants ADMIN, OBSERVATEUR, EXPLOITATION
- Mots de passe test = Test123! pour obs1 (OBSERVATEUR id4) et mohamed (EXPLOITATION id3)

## 2. RESTE A FAIRE

A finir pour clore le 28/06 :
- git push (commit RBAC ff9b0a7 local, en avance d'1 sur origin)
- committer ce SESSION_RESUME.md

Modifs ABANDONNEES laissees de cote (NE PAS croire que c'est a finir) :
- MessageTypeController, TestController, UserController : RBAC plausible mais NON teste
- application.yml et .idea/* = config locale, NE PAS committer
- a reprendre ou jeter lors de l'audit RBAC complet

Pistes suivantes :
- Audit RBAC complet (proteger tous les verbes d'ecriture restants)
- Circuit breaker (colonne stop_on_error_rate prete dans campaigns)
- Champs variables au-dela du PAN (docs/generation-variee-TODO.md)
- CRUD campagnes via API (POST /api/campaigns, actuellement SQL)
- Dette : LoadTestOrchestrationService redondant ; persistance authorizations ; sql/recharge_cards.sql versionne

## 3. INFOS PRATIQUES

Canal SQL (Git Bash) :
  alias sgsql='PGPASSWORD=postgres123 "/d/MoneyCore/PostgreSQL/18/bin/psql.exe" -U postgres -h localhost -d scenariogenerator'

Login : admin / Admin123! (endpoint /auth/login, champ login, reponse champ token)
Comptes test : obs1 / Test123! (OBSERVATEUR) ; mohamed / Test123! (EXPLOITATION)

Modele RBAC (verifie en base) :
- Roles (table roles, colonne code) : ADMIN, OBSERVATEUR, EXPLOITATION
- Permissions (table permissions, colonne code) : USER_MANAGE, ROLE_MANAGE, CATALOG_MANAGE,
  CAMPAIGN_VIEW, CAMPAIGN_CREATE, CAMPAIGN_GENERATE, CAMPAIGN_EXPORT, CARD_PROVISION,
  CAMPAIGN_REPLAY, TPS_CREATE, TPS_RUN, EXECUTION_VIEW
- JWT (JwtFilter de com.staging.sg.common) : authorities = ROLE_<role> + permissions nominatives
- SecurityConfig : EnableMethodSecurity actif ; /auth/** et /api/status permitAll ;
  /api/admin/** = hasRole(ADMIN) ; /api/** = authenticated ; fin via PreAuthorize
- OBSERVATEUR = lecture seule ; EXPLOITATION = + CREATE/GENERATE/REPLAY/TPS

Compile : cd MODULE && mvn install -q -DskipTests 2>&1 | grep -viE "^WARNING" ; echo EXIT ${PIPESTATUS[0]}
  Ordre : sg-common AVANT issuer/acquirer/orchestrator.
  NB : apres recompilation, REDEMARRER le module (le process garde l'ancien bytecode).

ddl-auto = validate (issuer + orchestrator) : chaque entite doit avoir sa table.

Prerequis jPOS : sign-on issuer (POST 8501/api/admin/dmas/jpos/signon) AVANT toute transaction.
Pour WITH_PIN : PEK ACTIVE (key exchange prealable).

Cartes test (dmas_cards) : 7 ACTIVE. PAN principal 5321962145453348, PIN 1234.
balance = bigint en centimes, devise 840 (USD), plein de reference 100000000.
Recharge : sgsql -c "UPDATE dmas_cards SET balance=100000000, updated_at=now() WHERE status='ACTIVE' AND balance<100000000;"

Endpoints cles :
- POST 8501/api/admin/dmas/jpos/signon ; POST 8501/api/admin/dmas/cards
- POST 8084/api/admin/dmas/loadtest ; GET .../loadtest/{id}/status?details=true
- POST 8080/api/campaigns/{id}/run  (requiert CAMPAIGN_GENERATE)
- POST 8080/api/executions/start/{id}?mode=SIMPLE | loadtest/{id} | stop/{id}  (requiert TPS_RUN)
- GET  8080/api/executions/{id}/status ; /history ; /admin/history (EXECUTION_VIEW)

## 4. HISTORIQUE DES VERSIONS (tags)

- v1.0.0 (7945c96) : moteur de charge + modele Campagne (multi-paliers + SLA/verdict) + guide + versioning
- v1.1.0 (a48f029) : tirage de cartes reelles + PIN dans les campagnes

## 5. DERNIERS COMMITS (feature/multi-module)

- a48f029  v1.1.0 tirage cartes reelles + PIN  [tag v1.1.0]
- 05a7087  docs SESSION_RESUME v1.1.0 terminee  [= origin]
- ff9b0a7  RBAC CAMPAIGN_GENERATE / TPS_RUN  [local, pas encore push]
