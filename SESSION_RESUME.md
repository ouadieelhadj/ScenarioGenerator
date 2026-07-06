# SESSION RESUME — ScenarioGenerator

> Fichier de reprise de session : etat d'avancement, ce qui est en cours, ce qui reste.
> Couvre DEUX depots : le BACK `ScenarioGenerator` (Java/Spring, repo GitHub) et le
> FRONT `sg-frontend` (Angular 18, dossier separe non encore versionne).

**Derniere mise a jour :** 2026-07-04 (session 9 : demarrage projet SWAM multi-reseau)
**Branche back :** chore/cleanup-modules
**Version courante back :** v1.1.0 (taguee, publiee) + travaux post-v1.1.0

---

## 0. METHODE DE COLLABORATION (IMPORTANT a lire en debut de session)

- L'assistant (Claude) travaille dans un **conteneur Linux isole**. Il n'a **AUCUN acces** au disque D:,
  a IntelliJ, a la base PostgreSQL, ni a GitHub de l'utilisateur.
- **L'utilisateur execute** toutes les commandes shell/bat sous Windows (Git Bash) et colle les resultats.
- L'assistant **genere des livrables** dans son conteneur (`/home/claude`), les copie dans
  `/mnt/user-data/outputs/`, et les partage via `present_files`. Ce sont typiquement des **scripts .sh**
  que l'utilisateur telecharge, place dans `/d/MoneyCore/`, et lance : `bash /d/MoneyCore/xxx.sh`.
- L'assistant **ne peut pas pousser** sur GitHub lui-meme : il prepare les commandes git, l'utilisateur les execute.

### Pieges recurrents (a garder en tete)
- **[Git Bash] TOUJOURS `set +H`** avant un script contenant `!` (expansion d'historique).
- **[Windows .bat] ne JAMAIS utiliser la variable `TMP`** (reservee = java.io.tmpdir, casse Tomcat) -> utiliser `SGTMP`.
- **[Assemblage scripts .sh]** ne PAS echapper les variables (`\$ORCH`) dans les heredoc -> utiliser des
  **chemins en dur**. Methode robuste = fonction `add_file` avec `printf` + `cat >> "$OUT"`.

---

## 1. ETAT DE LA PLATEFORME (BACK)

Plateforme de simulation et test de charge monetique Mastercard DMAS.
Racine : `/d/MoneyCore/ScenarioGenerator`. Java 21 (`/d/MoneyCore/jdk-21.0.11/bin/java.exe`). PostgreSQL 18.

Modules :
- **sg-generator-orchestrator** (REST 8080, package `com.staging.sg.acquirer`, main `SgAcquirerApplication`,
  user PG `scenario_user`) : orchestrateur (campagnes, executions, users, rapports, autorisations 0100)
- **sg-dmas-acquirer** (REST 8084 / jPOS 8600, user `dmas_acquirer_user`) : acquereur (KEK, PEK, reseau, moteur de charge)
- **sg-dmas-issuer** (REST 8501 / jPOS 8500, user `dmas_issuer_user`) : emetteur (cartes, sign-on, reponses)
- **sg-common** : 33 entites JPA + 33 repositories + DTOs partages

Compilation orchestrateur (depuis la racine) :
`mvn -q -pl sg-generator-orchestrator -am clean package -DskipTests`
JAR : `sg-generator-orchestrator/target/sg-generator-orchestrator-1.0.0-SNAPSHOT.jar`
Les WARNING Maven/Java sont inoffensifs.

Cles crypto de test : KEK memberGroupId=`TESTGRP01`, kekClear=`0123456789ABCDEF` x3 (triple length).
Carte test PAN `5321962145453348`, PIN `1234`, devise 840 (USD), balance = bigint centimes.
LMK : `D:/MoneyCore/ScenarioGenerator/keys/dmas-lmk.lmk`.

---

## 2. RBAC (verifie en base) — commun back + front

- **Roles** (table roles) : ADMIN, OBSERVATEUR, EXPLOITATION. **Pas d'endpoint de gestion des roles**
  (`/api/admin/roles` n'existe pas). Les 3 roles sont donc en dur cote front.
- **12 Permissions** : USER_MANAGE, ROLE_MANAGE, CATALOG_MANAGE, CAMPAIGN_VIEW, CAMPAIGN_CREATE,
  CAMPAIGN_GENERATE, CAMPAIGN_EXPORT, CARD_PROVISION, CAMPAIGN_REPLAY, TPS_CREATE, TPS_RUN, EXECUTION_VIEW.
- **JWT** (JwtFilter de com.staging.sg.common) : claims `sub`(login), `role`, `permissions[]`.
  authorities = ROLE_<role> + permissions nominatives.
- **SecurityConfig orchestrateur** (ordre IMPORTANT) : `/auth/**` + `/api/status` permitAll ;
  `/api/admin/tests/**` authenticated ; `/api/admin/**` hasRole("ADMIN") ; `/api/**` authenticated.
  `@EnableMethodSecurity` actif. CORS configure pour `http://localhost:4200` (GET/POST/PUT/DELETE/OPTIONS).

Login : admin / Admin123! (POST /auth/login, champ `login`, reponse champ `token`).
Comptes test : obs1 / Test123! (OBSERVATEUR) ; mohamed / Test123! (EXPLOITATION).

---

## 3. CONTRATS API BACK (confirmes, utilises par le front)

**CampaignController** (`/api/campaigns`) :
- GET -> CAMPAIGN_VIEW (liste) ; GET/{id} -> CAMPAIGN_VIEW (detail+paliers)
- POST -> CAMPAIGN_CREATE ; PUT/{id} -> CAMPAIGN_CREATE (remplacement COMPLET) ; DELETE/{id} -> CAMPAIGN_CREATE
- POST/{id}/run -> CAMPAIGN_GENERATE (renvoie campaignExecutionId)
- GET/executions/{execId} -> suivi d'une execution ; GET/{id}/executions -> executions d'une campagne
- Pas d'endpoint "toutes les executions" (uniquement par campagne).
- CampaignRequest : name, description?, category, config(JSON), expectedDe039?, active, slaP95MaxMs?,
  slaErrorRateMax?, slaApprovalMin?, stopOnErrorRate?, loadSteps[]{stepOrder,startSeconds,endSeconds,tpsValue,concurrency?}

**UserController** (`/api/admin/users`, ADMIN) :
- GET (liste UserDto) ; GET/{id} ; POST create ; PUT/{id} update ; PUT/{id}/toggle (activer/desactiver)
- CreateUserRequest{login,password,email,role} ; UserDto{id,login,email,role,active}

**DMAS** :
- Cartes (issuer 8501) : POST /api/admin/dmas/cards CardRequest{pan,pin,balance,currency,expiry} ;
  GET/{pan} ; POST/{pan}/balance BalanceRequest{balance}. pan+pin obligatoires.
- KEK (acquereur 8084) : POST /api/admin/dmas/kek/bootstrap {memberGroupId, kekClear}
- PEK (acquereur 8084) : POST /api/admin/dmas/keyexchange/pek?memberGroupId=TESTGRP01
- Reseau (acquereur 8084) : POST /api/admin/dmas/network/signon, /signoff, /echo, GET /status
- Test 0100 (ORCHESTRATEUR 8080) : POST /api/mc/authorize McAuthRequest{DE002_PAN, DE004_AMOUNT(long),
  DE003_PROCESSING_CODE, DE018_MCC, DE022_POS_ENTRY_MODE, DE049_CURRENCY_CODE, DE052_PIN, ...}

**PortConfigController** (`/api/admin/config`, ADMIN) — AJOUTE SESSION 8 (voir section 7) :
- GET /port -> {service:"orchestrator", currentPort}
- POST /port {port} -> valide (1024-65535), renvoie {oldPort,newPort,restarting:true} PUIS redemarre

---

## 4. HISTORIQUE BACK (sessions 1-5, deja documentees ci-dessous en detail)

- **Session 1** : recharge cartes, RBAC run/start, role TESTEUR supprime.
- **Session 2** : audit RBAC complet (McController TPS_RUN, SecurityConfig, permissions par controleur).
- **Session 3** : CIRCUIT BREAKER (stop_on_error_rate ; statut STOPPED_ERROR_RATE ; commit 0cb7e17).
- **Session 4** : CRUD campagnes via API (CampaignController enrichi, CampaignDto ; PUT = remplacement complet).
- **Session 5** : champs variables au-dela du PAN (VARIABLE_FIELDS.AMOUNT mode RANGE ; TpsCampagnePreparation).
  (Details complets conserves en section 10 "ARCHIVE sessions 1-5".)

---

## 5. SESSION 6 — PROCEDURE BASE PROPRE (COMPLETED, versionnee)

**[DECISION]** Base creee 100% par SQL (abandon du create Hibernate), puis services en `ddl-auto=validate`.

Repartition propriete des tables : scenario_user=29, dmas_acquirer_user=2 (dmas_acq_keys, dmas_kek),
dmas_issuer_user=4 (dmas_cards, dmas_iss_keys, dmas_transactions, key_store).
Grants croises decouverts empiriquement : `users` aux 3 users ; `dmas_kek` a issuer ;
**`dmas_cards` a scenario_user** (l'orchestrateur lit les cartes pour les campagnes = dependance runtime).

Dossier **`deploiement/`** versionne (commit e3d908e, push OK sur chore/cleanup-modules) :
`1_create-data_base.bat`, `2_start-services.bat`, `3_scenario-e2e.bat`, `structure_tables.sql`,
`donnees_reference.sql`, `README.md`. Test E2E valide PASSED (39 tx approuvees).
[Piege resolu] variable TMP -> SGTMP dans les .bat.

**PENDING** : externaliser chemins/mots de passe des scripts ; nettoyer fichiers parasites du repo ; decider merge branche.

---

## 6. SESSION 7 — IHM ANGULAR (FRONT `sg-frontend`)

**[DECISION]** Projet Angular **SEPARE** (microservices, pas Maven) dans `D:\MoneyCore\sg-frontend`.
Node v24.16.0, npm 11.13.0. **Angular 18 standalone** + PrimeNG (theme Aura) + **ngx-translate v18**.
Developpe dans IntelliJ (File>Open le DOSSIER). Lancement : `cd /d/MoneyCore/sg-frontend && npm start`
-> http://localhost:4200 (laisser la fenetre ouverte).

### Architecture front
- `core/` : auth (AuthService decode le JWT cote client, PAS de /whoami ; signals) ; guards (authGuard +
  permissionGuard) ; interceptors (auth Bearer + error 401->logout) ; models ; theme (ThemeService +
  _tokens.scss + themes.ts) ; i18n (LanguageService) ; **config (api.config.ts CENTRALISE)** ; services.
- `shared/directives/hasPermission` ; `layout/` (main-layout sidebar filtree par permission + topbar
  theme/langue/user + menu.ts) ; `features/` (login, dashboard, campaign-generation, campaign-orchestration,
  execution-view, dmas, admin, config, profile, help).

### Ecrans construits et VALIDES
1. **Socle** : auth JWT, RBAC par permission (guard+directive+menu filtre), theme clair/sombre a chaud +
   couleur primaire (tokens CSS, persistant localStorage).
2. **Login** (decodage token client, menu RBAC filtre).
3. **i18n fr/en/es** (ngx-translate **v18**). Correctifs v18 : `TranslatePipe`/`TranslateDirective`
   (plus de TranslateModule), `setFallbackLang` (plus setDefaultLang), `provideTranslateService` +
   `provideTranslateHttpLoader({prefix,suffix})`, `fallbackLang:'fr'`. Fichiers `src/assets/i18n/{fr,en,es}.json`
   (cles hierarchiques, coherence verifiee par python). Selecteur drapeaux topbar.
4. **Generation de campagne** : tableau + dialog CRUD + paliers de charge + 14 infobulles CSS (data-tip). Traduit.
5. **Orchestration de campagne** : liste campagnes ACTIVES + suivi temps reel (polling 2s, interval+switchMap
   jusqu'a etat terminal COMPLETED/ERROR/STOPPED_ERROR_RATE/FAILED, bouton stopper le suivi + rafraichir). Traduit.
6. **Consultation des executions** : selecteur de campagne (pas d'endpoint global) -> tableau executions ->
   clic = dialog detail. Traduit.
7. **Monetique DMAS** : 4 onglets (Reseau signon/signoff/status ; Cles KEK bootstrap + PEK ; Cartes provisionner/
   consulter/solde ; Test 0100 champs ISO + avances) + zone de log resultats. Valeurs pre-remplies (TEST_DEFAULTS). Traduit.
8. **Administration** : tableau users (id/login/email/role/actif) + dialog create/edit (login non modifiable en
   edition, password optionnel en edition = vide inchange) + toggle actif. Protege USER_MANAGE. Traduit. VALIDE.
9. **Configuration** : voir section 8 (ports des services). Extensible pour d'autres params.
10. **Help** : ecran dedie, 5 sections depliables. **PENDING : pas encore traduit (francais en dur).**

### Config API centralisee (`src/app/core/config/api.config.ts`)
Tous les endpoints (auth, campaigns, config, users, dmas) + TEST_DEFAULTS (KEK, carte, auth 0100).
URL de base derivees de `environment.ts` (apiOrchestrator 8080, apiAcquirer 8084, apiIssuer 8501).
Helpers `url.orchestrator/acquirer/issuer(path)`. AuthService/CampaignService/DmasService/UserService refactores dessus.

---

## 7. SESSION 8 — CHANGEMENT DE PORT A CHAUD (back + front, VALIDE)

**[IDEE utilisateur]** Ajouter aux services un endpoint pour modifier leur port + se redemarrer ;
si le front recoit OK, il met a jour sa config. **[DECISION]** Restart du **contexte Spring** (pas relance JVM),
port persiste dans `application.yml`. Teste UNIQUEMENT sur l'orchestrateur (pas de jPOS = moins risque).

### Back orchestrateur (COMPLETED, compile, teste) — 2 classes + 1 modif
- `sg-generator-orchestrator/.../config/RestartService.java` : ecrit le port dans
  `src/main/resources/application.yml` (preserve le reste du fichier), puis dans un thread avec delai 1.5s
  ferme le contexte (`ctx.close()`) et relance `SpringApplication.run(SgAcquirerApplication.class, args)`.
  **[BUG RESOLU : accumulation des `--server.port`]** au 2e restart Spring recevait "8090,8080" ->
  NumberFormatException. Fix = retirer tout `--server.port=` existant des args avant d'ajouter le nouveau.
- `sg-generator-orchestrator/.../api/PortConfigController.java` (`/api/admin/config`, protege ADMIN
  par SecurityConfig) : GET /port -> {service,currentPort} ; POST /port {port} -> valide, renvoie OK
  immediatement {oldPort,newPort,restarting:true}, puis restart.
- `SgAcquirerApplication.java` : correctif du log de demarrage pour afficher le PORT REEL via
  `env.getProperty("local.server.port")` (le @Value affichait l'ancien port apres restart — cosmetique).

Scripts back livres : `apply-port-config-back.sh` (chemins en dur), `fix-restart-accumulation.sh`,
`fix-main-realport.sh` / `fix-main-port-log.sh`.
TESTE : cycles 8080->8090->8080->8070 OK (restart contexte propre : Hikari/JPA/network fermes puis rouverts).

### Front (COMPLETED) — ecran "Configuration"
- Menu Configuration (ADMIN via permission USER_MANAGE). Extensible pour d'autres parametres.
- `port-config.service.ts` (GET port reel, POST changement, updateFrontPort localStorage).
- Tableau 3 services : orchestrateur VALIDE ; acquereur 8084 / emetteur 8501 affichent "injoignable"
  (leur back n'a PAS encore l'endpoint config/port).
- Change port -> back redemarre -> front reconnecte auto apres 8s (verifyReconnect).

### [POINT CLE] Synchro front/back via localStorage — PIEGE IMPORTANT
- Le front lit le port dans `localStorage['sg-ports'].orchestrator` s'il existe, sinon `environment.ts` (8080).
- Une valeur de test restee dans le localStorage (ex 8070) provoque "Service injoignable" (le front tape le
  mauvais port). **Le localStorage est INTERNE au navigateur, INACCESSIBLE depuis Git Bash**
  (se vide via F12 > Application > Local Storage > Clear, ou console `localStorage.removeItem('sg-ports')`).
- **[DECISION]** Flag `USE_LOCALSTORAGE_PORTS = false` dans api.config.ts (`fix-ignore-localstorage-ports.sh`
  applique) -> le front IGNORE le localStorage et utilise les ports des fichiers. Comportement previsible.
  Compromis : le suivi dynamique apres changement de port ne marche plus (il faut ajuster environment.ts).
- **REGLE : garder l'orchestrateur sur 8080** (port de reference du front). Si on change son port pour tester,
  le remettre sur 8080 ensuite, sinon le front ne le trouve plus.

---

## 8. SCRIPTS .sh LIVRES (a placer dans /d/MoneyCore puis `bash /d/MoneyCore/xxx.sh`)

FRONT : apply-campaign-generation.sh, apply-tooltips.sh, apply-help.sh, apply-i18n-1.sh, fix-i18n-v18.sh,
apply-i18n-campaign.sh, apply-orchestration.sh, apply-execution-view.sh, apply-api-config.sh, apply-dmas.sh,
apply-config-screen.sh, apply-admin.sh, fix-ignore-localstorage-ports.sh.

BACK : apply-port-config-back.sh, fix-restart-accumulation.sh, fix-main-realport.sh, fix-main-port-log.sh.

---

## 9. RESTE A FAIRE (PENDING GLOBAUX)

### Front
- **Dashboard** (vue d'accueil, placeholder actuel).
- **Traduire l'ecran Help** (francais en dur) + placeholders restants (profile).
- **Frontendiser les roles** : aujourd'hui les 3 roles sont en dur (pas d'endpoint back roles). A decider :
  ecran de VISUALISATION du mapping role->permissions (lecture) OU gestion (necessite endpoints back).
  -> PROCHAIN SUJET EN COURS quand la session s'est terminee (cartographie back a faire : chercher
  RoleController/PermissionController, entites Role/Permission, mapping des permissions par role).
- **Versionner le front** `sg-frontend` (pas encore de repo git initialise).

### Back
- **Etendre l'endpoint port a l'acquereur (8084) et l'emetteur (8501)**. ATTENTION : ces services ont des
  serveurs **jPOS** (8600/8500) qui devront se rouvrir au restart du contexte (risque, a tester prudemment).
- **Bouton "Reinitialiser localStorage"** dans l'ecran Config si on repasse USE_LOCALSTORAGE_PORTS a true.
- Push des commits back en avance sur origin (voir section commits).

### Sujet SUSPENDU : suppression du monde Test
**[DECISION utilisateur]** Veut le mode Campagne uniquement et supprimer les tables du monde Test
(`tests`, `executions`, `results`, `user_tests`, `tps_steps`), APRES avoir fini les ecrans front.
**[CARTOGRAPHIE FAITE — CAS A : code TRES actif, NE PAS supprimer sans refactoring lourd]** :
- Entites : Test, Execution, Result, TpsStep existent dans sg-common (user_tests n'a PAS d'entite).
- Repositories UTILISES par le coeur : **ExecutionRepository et ResultRepository sont utilises par
  McAcquirer, McAdviceManager, McReversalManager, TpsEngine** (= coeur monetique + moteur TPS !).
- Controleurs ExecutionController + TestController encore exposes.
**CONCLUSION : couplage fort au coeur monetique. Supprimer les tables casserait McAcquirer/TpsEngine/validate.
SUSPENDU** — necessiterait de supprimer d'abord le code (controleurs->services->repos->entites), recompiler,
PUIS retirer les tables, dans cet ordre et prudemment.

---

## 10. ARCHIVE — DETAILS SESSIONS 1-5 (BACK, conserves)

### Session 3 - CIRCUIT BREAKER (commit 0cb7e17)
CampaignRunService : a la fin de chaque palier, taux d'erreur cumule (declined/total). Si stopOnErrorRate
non-NULL ET taux > seuil -> break la boucle des paliers. Statut STOPPED_ERROR_RATE ; verdict_detail
"Circuit breaker: taux err X% > seuil Y% apres palier N" (ecrit APRES computeVerdict pour primer sur SLA).
Retrocompatible (stop_on_error_rate NULL = inchange). Arret ENTRE paliers. Teste E2E OK.

### Session 4 - CRUD campagnes via API
Endpoints CampaignController sous /api/campaigns, @PreAuthorize fin. PUT = remplacement COMPLET
(champs absents -> null). Fichiers : CampaignRequest.java, CampaignDto.java, CampaignCrudService.java.
Sortie = CampaignDto (evite serialisation LAZY). Teste E2E + RBAC (OBSERVATEUR GET=200 POST=403).

### Session 5 - Champs variables (montant)
VARIABLE_FIELDS dans le config JSON, sans nouvelle colonne. Premier champ : AMOUNT mode RANGE {min,max}.
Classe TpsCampagnePreparation cote acquereur (construite 1x avant les threads, nextTx()->PreparedTx) =
POINT D'EXTENSION UNIQUE pour futurs champs variables. PAN reste RANDOM (pool lu en base au lancement
orchestrateur, tire en memoire acquereur ; l'acquereur n'accede JAMAIS la base par tx ; PANs/PINs PAS dans
le JSON pour PCI). Chaine : config -> CampaignRunService -> LoadTestRequest -> TpsCampagnePreparation ->
buildAuth0100 (DE4). Teste E2E (40 tx, 12 montants distincts dans la plage).
A SUIVRE : etendre VARIABLE_FIELDS a ENTRY_MODE (DE22), puis PROC_CODE (DE3) et DE43.

### Infos pratiques back (rappel)
- Canal SQL : `PGPASSWORD=postgres123 "/d/MoneyCore/PostgreSQL/18/bin/psql.exe" -U postgres -h localhost -d scenariogenerator`
- Prerequis jPOS : sign-on issuer AVANT toute transaction. Pour WITH_PIN : PEK ACTIVE (TESTGRP01).
- Config campagne (JSON) : DE002_PAN, DE002_PAN_MODE=RANDOM, DE004_AMOUNT, WITH_PIN, ENTRY_MODE,
  VARIABLE_FIELDS. Colonnes : sla_p95_max_ms, sla_error_rate_max, sla_approval_min, stop_on_error_rate.

---

## 11. COMMITS / TAGS

Tags : v1.0.0 (moteur+Campagne), v1.1.0 (cartes reelles+PIN).
Branche courante : **chore/cleanup-modules**.
- Dossier `deploiement/` : commit e3d908e (push OK).
- A PUSH : commits back du changement de port (RestartService, PortConfigController, SgAcquirerApplication),
  + circuit breaker/CRUD/champs variables si pas encore pousses sur cette branche.
- Front `sg-frontend` : PAS ENCORE versionne (a initialiser si souhaite).

---

## 12. PROJET SWAM — nouveaux modules multi-reseau (DEMARRAGE, session 9)

> Nouveau chantier : simuler le switch national marocain **SWAM (Switch Al Maghrib)**, sur le modele
> de ce qui a ete fait pour DMAS (Mastercard). SWAM relie les banques marocaines pour les operations
> monetiques nationales. Format : **ISO 8583 avec personnalisation SWAM des champs**.

### Objectif
Developper DEUX modules dans le MEME projet ScenarioGenerator (comme sg-dmas-*) :
- **sg-swam-issuer** : simule **le switch SWAM lui-meme** (le point central qui recoit/route)
- **sg-swam-acquirer** : simule **une banque connectee** au switch (emet des transactions vers SWAM)
- Operations dans les DEUX SENS (la banque envoie vers le switch ; le switch peut solliciter la banque).

### [DECISION] Architecture multi-reseau : OPTION A retenue
Contexte : la plateforme va accumuler plusieurs reseaux (DMAS/Mastercard aujourd'hui, puis SWAM,
puis VISA SMS/BASE I, puis Mastercard MDS...). Trois options avaient ete posees :
- A) Dupliquer par reseau (rapide, mais duplication) ← **CHOISIE**
- B) Coeur commun + adaptateurs enfichables (NetworkAdapter) — pereine mais gros refactoring
- C) Module unique pilote par config

**Choix utilisateur : OPTION A** (dupliquer pour SWAM maintenant), **AVEC adaptations de sg-common**
au fur et a mesure (ce qui est partageable va dans sg-common a cote de l'existant Mastercard).
Refactorisation vers l'option B envisageable plus tard quand VISA/MDS arriveront.

### PREREQUIS BLOQUANT : la specification technique SWAM
Le `SwamPackager` depend ENTIEREMENT de la spec. A recuperer/partager en debut de session :
- tableau des champs DE (2,3,4,7,11,32,37,39,41,49...) : format (num/alphanum/binaire), longueur, type (fixe/LLVAR/LLLVAR)
- encodage (ASCII / EBCDIC / BCD)
- couche transport (longueur d'en-tete, header ISO, framing)
- MTI supportes (0100/0110, 0200/0210, 0800/0810...)
- codes specifiques (processing codes, response codes) + partie crypto (MAC, PIN block, cles)

### Gabarit DMAS a repliquer (structure existante confirmee)
**sg-dmas-acquirer** (package com.staging.sg.dmas.acquirer) :
- network/ : DmasJposServer, McDmasAuthorization, McDmasReversal, McDmasAdvice, McDmasNetworkManager,
  McDmasKeyExchange, LoadTestService, TpsCampagnePreparation, SessionOrchestrator
- api/ : ~15 controllers (Auth, Authorization, Advice, Reversal, DmasNetwork, KekBootstrap, KeyExchange,
  LoadTest, MacTest, PackagerTest, PinTest, Session, WhoAmI, CryptoTest)
- config/SecurityConfig

**sg-dmas-issuer** (package com.staging.sg.dmas.issuer) :
- issuer/ : DmasJposClient, McDmasIssuer
- api/ : Auth, Card, JposNetwork, KekBootstrap ; config/SecurityConfig

**Packager** : `sg-common/src/main/java/com/staging/sg/common/iso/McPackager.java` (+ McPackagerEbcdic).
Etend `org.jpos.iso.ISOBasePackager`, definit les 128 champs via ISOFieldPackager[] (ex :
`fields[2] = new IFA_LLNUM(19, "PAN")`, `fields[4] = new IFA_NUMERIC(12, "AMOUNT TRANSACTION")`).
-> `SwamPackager` sera construit sur ce meme modele, avec les champs de la spec SWAM.

**HSM/crypto partage** (dans sg-common, reutilisable) : `hsm/ThalesHsmService.java`, `iso/crypto/HsmService.java`.

**pom.xml racine** — modules actuels : sg-common, sg-generator-orchestrator, sg-dmcs-issuer,
sg-dmcs-acquirer, sg-dmas-acquirer, sg-dmas-issuer. (Note : des modules sg-dmcs-* existent deja =
precedent de duplication pour un autre reseau.) jpos.version=2.1.9.
-> AJOUTER sg-swam-issuer + sg-swam-acquirer dans <modules>.

### PLAN DE CONSTRUCTION (ordre propose, a demarrer en nouvelle session)
1. Recuperer + analyser la spec SWAM -> recap de comprehension a valider AVANT de coder.
2. `SwamPackager` dans sg-common (le dialecte ISO SWAM).
3. `sg-swam-issuer` (le switch) : serveur jPOS + logique de routage/reponse.
4. `sg-swam-acquirer` (la banque) : client jPOS + transactions (autorisation, etc.) dans les 2 sens.
5. Declaration dans le pom parent + compilation.
6. Endpoints REST + integration IHM (onglet/ecran SWAM cote front, plus tard).

### Point d'attention
- En monetique, ne RIEN deviner : chaque champ/format vient de la spec. Un recap de comprehension
  est valide avec l'utilisateur avant de coder.
- Reutiliser au maximum l'infra existante (HSM, pattern jPOS, securite JWT, moteur de charge).


---

## 13. v1.2.0 — SOCLE MULTI-RESEAU (COMPLETED, branche feature/multi-network)

> Refactoring de l'existant (DMAS + orchestrateur + front) pour rendre la
> plateforme reseau-consciente et MTI-parametrique, AVANT de construire SWAM.
> Principe : SELECTION en base, PROTOCOLE en code (Option A conservee).

### Base (4 migrations SQL dans deploiement/)
- **table `networks`** (referentiel gouverne) : metadonnees protocole
  (iso_version, length_prefix_size/encoding, header_type, default_field_encoding,
  mac_present, pin_block_format, packager_class) + infra (hosts/ports).
  * DMAS (verifie code) : 2 octets big-endian BINARY, McPackagerEbcdic, header NONE,
    ports 8084/8600/8501/8500/8080.
  * SWAM (spec HPS) : 4 octets ASCII, header POWERCARD, champs ASCII, ports acq/iss NULL.
- **message_types** : + `network` (defaut DMAS) + `direction` (ACQ_TO_ISS/ISS_TO_ACQ/BOTH).
  4 types SWAM ajoutes (1100/1200/1420/1804), category reseau-agnostique.
- **campaigns** : + `network` (defaut DMAS) + `initiator` (ACQUIRER/ISSUER, defaut ACQUIRER).
  data : ancienne category 'DMAS' -> 'AUTHORIZATION'.
- **FK** message_types.network + campaigns.network -> networks.code.
- **grants** networks -> scenario_user.

### Java (sg-common + orchestrateur)
- Entite `NetworkRef` (table networks, nommee ainsi pour ne pas heurter NetworkUtil)
  + `NetworkRepository`. `MessageTypeRepository.findByNetworkAndCategory/findByNetwork`.
- Campaign/CampaignDto/CampaignRequest + network/initiator ; MessageType + network/direction.
- **CampaignCrudService** : validation coherence initiator<->direction
  (STRICT si type existe ; TOLERANT sinon = warning ; defauts DMAS/ACQUIRER retrocompatibles).
- **CampaignRunService** : resout le MTI depuis message_types (network,category)
  et le transmet au moteur. Fallback 0100.
- **McDmasAuthorization** : buildAuth0100/WithPin surchargees MTI-parametriques
  (anciennes signatures conservees par delegation ; sendAuthorization manuel inchange).
- **LoadTestRequest** + champ mti ; LoadTestService passe req.mti.
- Endpoints : `NetworkController` (GET /api/networks) ; MessageTypeController
  etendu (GET ?network=X&category=Y).

### Front (sg-frontend, DESORMAIS VERSIONNE en local, commit 7e03b2f)
- Ecran generation de campagne : le champ texte 'category' remplace par 3 selecteurs
  Reseau / Type(categorie filtree par reseau) / Initiateur. Colonne Reseau au tableau.
- Services network + message-type ; modeles NetworkRef/MessageTypeRef.
- i18n fr/en/es (network, initiator). Dialog elargi (860px, grille adaptative).

### VALIDATION
- E2E DMAS : COMPLETED / PASSED / 37/37 approuvees / 0 declinee.
  Log 'MTI resolu=0100 (network=DMAS category=AUTHORIZATION)' -> resolution base OK,
  comportement DMAS strictement identique (refactoring a comportement constant).

### DETTE / A SUIVRE
- **networks.*_port** peuples mais NON lus au runtime (source de verite = application.yml
  + PortConfigController session 8). A brancher plus tard si networks devient source unique.
- **Front** : remote GitHub a creer (versionne en local seulement).
- **PROCHAIN CHANTIER : SWAM back** — SwamPackager (tout-ASCII, header PowerCARD, HSM simule),
  modules sg-swam-issuer + sg-swam-acquirer (gabarit DMAS), automate 1804/1814, autorisation 1100.
  Le socle est pret : ajouter SWAM = INSERT deja faits + packager/transport/2 modules en code.
