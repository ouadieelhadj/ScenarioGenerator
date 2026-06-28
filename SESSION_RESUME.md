# SESSION RESUME — ScenarioGenerator

> Fichier de reprise de session : etat d'avancement, ce qui est en cours, ce qui reste.

**Derniere mise a jour :** 2026-06-28 (session 2 - audit RBAC complet)
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

## 2. RBAC (etat consolide - 2 sessions)

Session 1 (commits 05a7087, ff9b0a7, 7730480, deja sur origin) :
- Recharge cartes : UPDATE dmas_cards balance vers 100000000 (= 1 000 000,00 USD), cartes ACTIVE sous cible
- CAMPAIGN_GENERATE sur CampaignController.run ; TPS_RUN sur ExecutionController start/loadtest/stop
- Role TESTEUR supprime (inutilise) ; roles restants ADMIN, OBSERVATEUR, EXPLOITATION
- Mots de passe test = Test123! : obs1 (OBSERVATEUR id4), mohamed (EXPLOITATION id3)

Session 2 - AUDIT RBAC COMPLET (commits f7df1f7 + ff27d78, PAS encore push) :
- McController : 6 POST proteges par TPS_RUN
  (mc/authorize, mc/reversal, mc/advice, mc/network/key-exchange, mc/network/signon, mc/network/echo)
  -> c'etait le gros trou : tout authentifie pouvait declencher un key-exchange / signon reseau
- SecurityConfig : exception .requestMatchers("/api/admin/tests/**").authenticated() AVANT /api/admin/**
  -> DECISION METIER : EXPLOITATION doit pouvoir creer/editer des tests (il a TPS_CREATE en base).
     Avant, la regle URL /api/admin/** = hasRole(ADMIN) court-circuitait le @PreAuthorize(TPS_CREATE).
- MessageType : POST/PUT/DELETE = CATALOG_MANAGE (ADMIN only en base)
- Test : GET = EXECUTION_VIEW ; POST/PUT/DELETE = TPS_CREATE
- User : classe entiere = USER_MANAGE (ADMIN only)
- Log orchestrateur renomme : acquiring.log -> GeneratorOrchestrator.log

Teste E2E (obs1 / mohamed / admin) - TOUT VERIFIE :
- OBSERVATEUR : lecture seule (403 sur toutes les ecritures)
- EXPLOITATION : cree des tests (200), lance mc (TPS_RUN), MAIS 403 sur users/message-types
- ADMIN : tout
- Routes /api/admin/users et /api/admin/message-types restent ADMIN-only (non-regression OK)

PIEGE rencontre (a retenir) : un 403 peut venir d'une erreur METIER (ex: contrainte NOT NULL en base),
PAS forcement du RBAC. Outil de diagnostic = logging.level.org.springframework.security: DEBUG
(montre "Authorized method invocation" si le @PreAuthorize passe). DEBUG retire apres usage.

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
- Mapping (table role_permissions, colonnes role_id/permission_id) :
  ADMIN = toutes ; EXPLOITATION = CAMPAIGN_* + TPS_CREATE + TPS_RUN + EXECUTION_VIEW (pas USER/ROLE/CATALOG_MANAGE) ;
  OBSERVATEUR = CAMPAIGN_VIEW + CAMPAIGN_EXPORT + EXECUTION_VIEW (lecture seule)
- JWT (JwtFilter de com.staging.sg.common) : authorities = ROLE_<role> + permissions nominatives
- SecurityConfig : EnableMethodSecurity actif. Chaine URL (ordre IMPORTANT, 1er match gagne) :
  /auth/** permitAll ; /api/status permitAll ; /api/admin/tests/** authenticated ;
  /api/admin/** hasRole(ADMIN) ; /api/** authenticated. Autorisation fine via @PreAuthorize.

Compile : cd MODULE && mvn install -q -DskipTests 2>&1 | grep -viE "^WARNING" ; echo EXIT ${PIPESTATUS[0]}
  Ordre : sg-common AVANT issuer/acquirer/orchestrator.
  IMPORTANT : orchestrateur lance depuis IntelliJ. Apres edition hors IDE (sed),
  faire Build > Rebuild Project PUIS Stop + Run (sinon bytecode obsolete).

Logs (fichiers, lisibles via git bash) :
  logs/GeneratorOrchestrator.log (orchestrateur), logs/dmas-acquirer.log, logs/dmas-issuer.log

ddl-auto = validate (issuer + orchestrator) : chaque entite doit avoir sa table.

Prerequis jPOS : sign-on issuer (POST 8501/api/admin/dmas/jpos/signon) AVANT toute transaction.
Pour WITH_PIN : PEK ACTIVE (key exchange prealable).

Cartes test (dmas_cards) : 7 ACTIVE. PAN principal 5321962145453348, PIN 1234.
balance = bigint centimes, devise 840 (USD), plein 100000000.
Recharge : sgsql -c "UPDATE dmas_cards SET balance=100000000, updated_at=now() WHERE status='ACTIVE' AND balance<100000000;"

Endpoints proteges (orchestrateur) :
- POST /api/campaigns/{id}/run -> CAMPAIGN_GENERATE
- POST /api/executions/start|loadtest|stop -> TPS_RUN
- POST /api/mc/** (authorize/reversal/advice/network/*) -> TPS_RUN
- /api/admin/tests : GET=EXECUTION_VIEW, POST/PUT/DELETE=TPS_CREATE
- /api/admin/message-types : POST/PUT/DELETE=CATALOG_MANAGE
- /api/admin/users : USER_MANAGE (classe)

## 4. RESTE A FAIRE

A finir pour clore la session :
- git push (commits f7df1f7 audit RBAC + ff27d78 renommage log, locaux, en avance sur origin)
- committer ce SESSION_RESUME.md

Pistes suivantes :
- Circuit breaker (colonne stop_on_error_rate prete dans campaigns ; arret auto sur taux d'erreur)
- Champs variables au-dela du PAN (docs/generation-variee-TODO.md ; montants, DE43, processing codes...)
- CRUD campagnes via API (POST /api/campaigns, actuellement creation en SQL)
- Dette : LoadTestOrchestrationService redondant avec CampaignRunService (ne joue que le 1er step) ;
  persistance acq/iss_authorizations ; sql/recharge_cards.sql versionne

Non committe laisse de cote :
- sg-dmas-issuer/application.yml = juste CRLF, ignorer
- .idea/* = config locale, ne pas committer
- dmcs/*.ipm, dmcs/*.txt = artefacts untracked (a .gitignore ou supprimer un jour)

## 5. HISTORIQUE / COMMITS

Tags : v1.0.0 (moteur+Campagne), v1.1.0 (cartes reelles+PIN)

Derniers commits feature/multi-module :
- 7730480  docs SESSION_RESUME session 1  [= origin]
- f7df1f7  RBAC audit (McController TPS_RUN + SecurityConfig tests + MessageType/Test/User)  [local]
- ff27d78  chore renommage log GeneratorOrchestrator.log  [local]
