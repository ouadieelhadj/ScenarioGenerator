# SESSION RESUME — ScenarioGenerator

> Fichier de reprise de session : etat d'avancement, ce qui est en cours, ce qui reste.

**Derniere mise a jour :** 2026-06-28 (session 3 - circuit breaker)
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

## 2. TRAVAIL REALISE (3 sessions, tout sur origin sauf push final a faire)

Session 1 (commits sur origin) :
- Recharge cartes : UPDATE dmas_cards balance vers 100000000 (cartes ACTIVE sous cible)
- CAMPAIGN_GENERATE sur CampaignController.run ; TPS_RUN sur ExecutionController start/loadtest/stop
- Role TESTEUR supprime ; roles restants ADMIN, OBSERVATEUR, EXPLOITATION
- Mots de passe test = Test123! : obs1 (OBSERVATEUR id4), mohamed (EXPLOITATION id3)

Session 2 - audit RBAC complet (sur origin) :
- McController : 6 POST proteges par TPS_RUN (authorize/reversal/advice/network key-exchange/signon/echo)
- SecurityConfig : exception /api/admin/tests/** authenticated AVANT /api/admin/** (EXPLOITATION cree des tests)
- MessageType=CATALOG_MANAGE, Test=EXECUTION_VIEW/TPS_CREATE, User=USER_MANAGE
- Log orchestrateur renomme GeneratorOrchestrator.log

Session 3 - CIRCUIT BREAKER (commit 0cb7e17, PAS encore push) :
- CampaignRunService : a la fin de chaque palier, calcule taux d'erreur cumule (declined/total).
  Si campaign.stopOnErrorRate non-NULL ET taux > seuil -> break la boucle des paliers.
  Statut = STOPPED_ERROR_RATE ; verdict_detail = "Circuit breaker: taux err X% > seuil Y% apres palier N"
  (le detail breaker ecrit APRES computeVerdict pour primer sur le detail SLA).
- Retrocompatible : si stop_on_error_rate = NULL, comportement inchange (les 2 campagnes existantes sont a NULL).
- Granularite : arret ENTRE paliers (pas en cours de palier - le detail des tx n'arrive qu'en fin de palier).
- TESTE E2E : campagne 3 paliers, PAN fixe carte solde bas, montant > solde, seuil 5%
  -> arret apres palier 1 (50 tx, 100% declined), paliers 2-3 NON joues, status STOPPED_ERROR_RATE, verdict FAILED. OK.

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
- SecurityConfig chaine URL (ordre IMPORTANT) : /auth/** et /api/status permitAll ;
  /api/admin/tests/** authenticated ; /api/admin/** hasRole(ADMIN) ; /api/** authenticated.

Compile : cd MODULE && mvn install -q -DskipTests 2>&1 | grep -viE "^WARNING" ; echo EXIT ${PIPESTATUS[0]}
  Ordre : sg-common AVANT issuer/acquirer/orchestrator.
  IMPORTANT : orchestrateur lance depuis IntelliJ -> apres edition hors IDE, Build>Rebuild Project PUIS Stop+Run.

Logs (fichiers, lisibles via git bash) :
  logs/GeneratorOrchestrator.log, logs/dmas-acquirer.log, logs/dmas-issuer.log
  Diagnostic securite : logging.level.org.springframework.security: DEBUG (retirer apres usage).

ASTUCE git bash : le caractere ! declenche l'expansion d'historique meme dans python -c.
  Faire 'set +H' avant tout script contenant des ! (ex: !allResults.isEmpty()).

Prerequis jPOS : sign-on issuer (POST 8501/api/admin/dmas/jpos/signon, avec token admin) AVANT toute transaction.
  Pour WITH_PIN : PEK ACTIVE (TESTGRP01). Verifier : sgsql -c "SELECT * FROM dmas_acq_keys WHERE key_type='PEK';"

Cartes test (dmas_cards) : 7 ACTIVE. PAN principal 5321962145453348, PIN 1234.
balance = bigint centimes, devise 840 (USD), plein 100000000.
Recharge : sgsql -c "UPDATE dmas_cards SET balance=100000000, updated_at=now() WHERE status='ACTIVE' AND balance<100000000;"

Config campagne (champ config JSON) :
  DE002_PAN (PAN fixe), DE002_PAN_MODE=RANDOM (tire dmas_cards), DE004_AMOUNT (centimes), WITH_PIN, ENTRY_MODE
  Colonnes campagne : sla_p95_max_ms, sla_error_rate_max, sla_approval_min, stop_on_error_rate (circuit breaker)

Endpoints proteges (orchestrateur) :
- POST /api/campaigns/{id}/run -> CAMPAIGN_GENERATE
- POST /api/executions/start|loadtest|stop -> TPS_RUN
- POST /api/mc/** -> TPS_RUN
- /api/admin/tests GET=EXECUTION_VIEW POST/PUT/DELETE=TPS_CREATE ; message-types=CATALOG_MANAGE ; users=USER_MANAGE

## 4. RESTE A FAIRE

A finir pour clore la session 3 :
- git push (commit 0cb7e17 circuit breaker, local, en avance sur origin)
- committer ce SESSION_RESUME.md

Pistes suivantes :
- Champs variables au-dela du PAN (docs/generation-variee-TODO.md ; montants min/max, DE43, processing codes...)
- CRUD campagnes via API (POST /api/campaigns, actuellement creation en SQL)
- Circuit breaker : evolution possible -> arret EN COURS de palier si l'acquereur exposait un compteur
  d'erreurs courant dans le status du loadtest (aujourd'hui le detail des tx n'arrive qu'en fin de palier)
- Dette : LoadTestOrchestrationService redondant avec CampaignRunService (ne joue que le 1er step) ;
  persistance acq/iss_authorizations ; sql/recharge_cards.sql versionne

Non committe laisse de cote : sg-dmas-issuer/application.yml (CRLF), .idea/*, dmcs/*.ipm+.txt (untracked)

## 5. HISTORIQUE / COMMITS

Tags : v1.0.0 (moteur+Campagne), v1.1.0 (cartes reelles+PIN)

Derniers commits feature/multi-module :
- f7df1f7  RBAC audit (McController, SecurityConfig tests, MessageType/Test/User)
- ff27d78  chore renommage log GeneratorOrchestrator.log
- 4b74e9b  docs SESSION_RESUME audit RBAC  [= origin]
- 0cb7e17  feat circuit breaker (stop_on_error_rate)  [local, pas encore push]

## 6. Session 4 - CRUD campagnes via API

Commit feat(campagne): CRUD via API (a push).
Endpoints CampaignController (sous /api/campaigns, autorisation fine par @PreAuthorize) :
- POST   /api/campaigns        -> CAMPAIGN_CREATE  (cree campagne + paliers en 1 transaction)
- GET    /api/campaigns        -> CAMPAIGN_VIEW    (liste)
- GET    /api/campaigns/{id}   -> CAMPAIGN_VIEW    (detail + paliers)
- PUT    /api/campaigns/{id}   -> CAMPAIGN_CREATE  (remplacement COMPLET : champs absents -> null, paliers remplaces)
- DELETE /api/campaigns/{id}   -> CAMPAIGN_CREATE  (supprime campagne + paliers)
- POST   /api/campaigns/{id}/run -> CAMPAIGN_GENERATE (existant)

Fichiers : sg-common/dto/CampaignRequest.java, CampaignDto.java ; acquirer/service/CampaignCrudService.java ;
CampaignController.java (enrichi). Sortie = CampaignDto (evite soucis serialisation LAZY User/loadSteps).

Teste E2E : create (campagne 4 + 2 paliers), get liste/detail, put (remplace par 1 palier), delete (404 + base vide).
RBAC verifie : OBSERVATEUR GET=200, POST=403.

ATTENTION : le PUT fait un remplacement COMPLET (pas un merge partiel). Un client doit renvoyer TOUS les champs,
sinon ils repassent a null. Evolution possible : ajouter un PATCH partiel.
