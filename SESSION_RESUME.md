# SESSION RESUME — ScenarioGenerator

> Fichier de reprise de session : etat d'avancement, ce qui est en cours, ce qui reste.
> Couvre DEUX depots : le BACK `ScenarioGenerator` (Java/Spring, repo GitHub) et le
> FRONT `sg-frontend` (Angular 18, dossier separe non encore versionne).

**Derniere mise a jour :** 2026-07-08 (session 10 : incr.2 crypto SWAM complet + packaging)
**Branche back :** chore/cleanup-modules (merge v1.3.0 fait) / feature/multi-network (dev)
**Version courante back :** v1.3.0 (taguee, publiee) - SWAM incr.1+2 crypto complet

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

---

## 14. SWAM — CONSTRUCTION EN COURS (branche feature/multi-network)

> On BATIT SWAM au niveau de DMAS : crypto reelle + persistance des 2 cotes.
> Ce n'est PAS un POC. Modele : miroir exact de DMAS.

### FAIT (commits 138b645, 4af5be1)
**Dialecte ISO (sg-common)** — VALIDE round-trip 16/16 :
- `SwamPackager` : ISO8583:1993 HPS, TOUT-ASCII (IFA_*), 128 champs.
  Autorisation 1100, gestion reseau 1804/1814. DE43 LLVAR ans..40, DE52/DE128 binaires.
- `SwamLengthChannel` : prefixe longueur 4 octets ASCII, parametrable (lengthDigits).
  Header PowerCARD gere plus tard au niveau applicatif.

**Modules (compiles)** :
- `sg-swam-issuer` = switch/CENTRE. Serveur jPOS qui ECOUTE (port ISO lu depuis
  networks, fallback 8510). SwamJposServer repond 1804->1814 DE39=800 et
  1100->1110 DE39=000. REST 8511. HSM SIMULE pour l'instant.
- `sg-swam-acquirer` = Membre/banque. Client jPOS, connexion PERMANENTE vers le
  switch (host/port depuis networks), sign-on par REST. SwamJposClient =
  pushAndWait correle par STAN. Endpoints /api/admin/swam/network/{signon,echo,
  signoff} + /purchase. REST 8094.
- Topologie HPS sec.4 : le Membre se connecte, le CENTRE ecoute (inverse de DMAS).

**Base (migrations v1.3.0)** — 6 tables miroir DMAS, ownership dedie :
- swam_cards, swam_iss_transactions, swam_iss_keys -> swam_issuer_user
- swam_acq_transactions (l'acquereur persiste AUSSI), swam_acq_keys -> swam_acquirer_user
- swam_kek -> postgres (partagee)
- users swam_issuer_user / swam_acquirer_user (mdp postgres123) + grants miroir DMAS
- ports en networks : ISO 8510, REST issuer 8511, REST acq 8094
- yml SWAM : users dedies + liquibase OFF (les modules LISENT le schema)

### FAIT (commits eb1e4ee + incr.2 : d8fb1fd..5a90592) — voir section 17
**Incr.1 (suite) — entites JPA + persistance + logique autorisation :**
- 6 entites JPA dans sg-common (SwamCard, SwamIssTransaction, SwamAcqTransaction,
  SwamIssKey, SwamAcqKey, SwamKek) + repositories.
- Persistance : switch -> swam_iss_transactions ; acquereur -> swam_acq_transactions.
- LOGIQUE AUTORISATION du switch : DECISION A FIGER
  (a) debit REEL du solde : carte existe+active+solde>=montant -> approuve 000 +
      debite ; sinon decline (carte inconnue / solde insuffisant). [RECO]
  (b) carte active -> approuve, sans gestion solde.
- Puis TEST connexion reelle complet : sign-on -> 1100 -> persistance verifiee.

**Incr.2-3 — crypto reelle HPS** (quand la persistance tourne) :
- key exchange SWAM (DE24=811/899, DE48 tags P10/P16, KEK/session keys)
- MAC FIPS PUB 113 sur DE128, PIN block format HPS (DE53), via ThalesHsmService.

### NOTES
- SwamJposClient utilise BaseChannel jPOS (setHost/setPort/connect/receive/send).
- SecurityConfig SWAM = permissif (POC) ; aligner sur DMAS (JWT+CORS) si pilotage IHM.
- Scripts test : 06c-test-swam.sh (arret+ports+demarrage+sign-on+1100+sign-off).

---

## 15. SWAM incr.1 TERMINE + incr.2 CRYPTO a faire (branche feature/multi-network)

### INCR.1 TERMINE ET VALIDE (commit eb1e4ee)
Le switch SWAM est un VRAI emetteur (plus un repondeur en dur) :
- 6 entites JPA (SwamCard, SwamIss/AcqTransaction, SwamIss/AcqKey, SwamKek) + repos
  dans sg-common. Validees contre le schema (ddl-auto=validate au demarrage : OK).
- SwamJposServer : logique autorisation REELLE (option a = debit reel) :
  * carte inconnue -> DE39=114 ; inactive -> 062 ; solde insuffisant -> 116
  * carte OK -> DEBITE le solde, DE39=000 APPROVED
  * persiste chaque autorisation dans swam_iss_transactions
- SwamNetworkController : persiste tx emises dans swam_acq_transactions
- VALIDE bout-en-bout sur socket reel (script 08c-cartes-et-test.sh) :
  3 achats 000/116/114, solde debite 100000->90000, persistance OK des 2 cotes.
- Cartes de test : deploiement/swam_cartes_test.sql
  (5321962145453348 solde 100000 ACTIVE ; 5321000000000011 solde 500 ;
   5321000000000029 BLOCKED)

### INFRA CRYPTO EXISTANTE (reutilisable pour SWAM)
- Interface HsmService (sg-common/iso/crypto/) : RESEAU-AGNOSTIQUE, expose
  generateWorkingKey, importWorkingKey, encryptPinBlock, decryptPinBlock,
  generateMac, verifyMac, computeKcv. IMPL = JposHsmService (jPOS 3DES sous LMK).
- ThalesHsmService (sg-common/hsm/) : generateZmk/Zpk/Zak, PIN block, MAC.
- Convention DMAS : KEK=ZMK, PEK=ZPK, MAK=ZAK.
- Flux DMAS a repliquer : KekBootstrapController, KeyExchangeController,
  SessionOrchestrator (sg-dmas-acquirer).
- Les tables swam_iss_keys/swam_acq_keys/swam_kek ont deja key_type,
  key_under_kek, key_under_lmk, kcv -> pretes.

### INCR.2 CRYPTO REELLE HPS — A FAIRE (PREREQUIS : LIRE LE PDF)
>>> PREREQUIS ABSOLU : ouvrir Description_Interface_Switch-SID_V3-20.pdf
>>> et VERIFIER CES 5 POINTS avant tout code (crypto = on ne devine RIEN) :

1. DE48 (transport cles de session) : FORMAT EXACT des tags P10 (cle MAC) et
   P16 (cle PIN) - structure interne (longueur, KCV inclus ?, ordre, encodage).
   -> section gestion de reseau (§3.13/§4) ou annexe description des DE.

2. Flux echange de cles 1804 DE24=811/899 : SEQUENCE exacte - qui initie,
   quel message porte quelle cle, quelle reponse. 811=cle transport ? 899=cle MAC ?
   echange separe pour cle PIN ? -> §4 (automate SIGN-ON, diagramme p.95).

3. DE53 (controle securite) : description POSITION PAR POSITION (methode
   encryption PIN 00/02, format PIN block 01/25/99, index cle PIN, index cle MAC).

4. DE52 (PIN block) : FORMAT exact (ANSI X9.8 / ISO Format 0 ?) + lien avec DE53.

5. DE128 ou DE64 (MAC) : sur QUELS CHAMPS le MAC est calcule (tout le message
   depuis MTI ? jusqu'ou ?), ALGO precis (FIPS PUB 113 = quel mode), DE64 ou DE128.

### PLAN INCR.2 (une fois le PDF lu + recap valide)
- 2.1 Bootstrap KEK SWAM (repliquer KekBootstrapController, forme KEK sous LMK
  -> swam_kek). PEU dependant de la spec, faisable en premier.
- 2.2 Key exchange SWAM (1804 DE24=811/899, ZPK/ZAK sous KEK, DE48 P10/P16). SPEC.
- 2.3 MAC reel (FIPS sur DE128 via generateMac/verifyMac). SPEC.
- 2.4 PIN block reel (DE52+DE53 via encrypt/decryptPinBlock). SPEC.
Chaque etape committee + testee avant la suivante.

### METHODE DE REPRISE
1. Ouvrir nouvelle session, re-uploader le PDF SWAM.
2. Claude relit les 5 points ci-dessus -> propose un RECAP crypto a valider.
3. Apres validation -> coder 2.1, 2.2, 2.3, 2.4 dans l'ordre.

---

## 16. SWAM incr.2 — RECAP CRYPTO FIGE (lecture PDF HSID v3.20 faite, NE PAS refaire)

> Source : Description_Interface_Switch-SID_V3-20_05012024.pdf. Recap verifie
> ligne par ligne. 3 niveaux : [SPEC]=litteral, [INF]=inference sure non ecrite,
> [DECISION]=trou spec tranche par nous (on maitrise switch + banque).

### Transport des cles (DE48, tags TLV Tag3+Long3+Valeur)
- [SPEC] P16 long=032 = "cle d'encryption du PIN / cle de transport du code
  confidentiel entre CENTRE et membre". P10 long=016 = "cle MAC".
- [INF] P16 = 32 hex = 16 octets = 2-key 3DES = ZPK (appuye par histo v3.12
  "T-DES pour PIN"). P10 = 16 hex = 8 octets = DES simple = ZAK.
- [INF] pas de KCV embarque dans P10/P16 (longueur = cle seule, pas de +6).
- [DECISION] cles transportees CHIFFREES sous ZMK/KEK (spec muette la-dessus ; = DMAS).
- Mapping DMAS : P16=ZPK=PEK, P10=ZAK=MAK, sous KEK=ZMK (bootstrap hors bande).

### Handshake key exchange (1804/1814)
- [SPEC] DE24 : 811="changement cle de transport" (=> P16), 899="changement cle
  MAC" (=> P10). Echange SEPARE, 1 message par cle. Pas de code pour changer ZMK.
- [SPEC] DE48 conditionnel dans 1804/1814 (OK pour porter les cles).
- [DECISION GAP-A] sens = (a) CENTRE-autorite : membre envoie 1804 DE24=811 ->
  CENTRE genere ZPK sous ZMK -> repond 1814 DE24=811, DE39=800, DE48 P16=<ZPK/ZMK>.
  Idem 899 -> P10. La spec (sect.4 + automate p.92-95) ne decrit QUE
  801/802/803/804/805 ; le key-exchange n'y est pas => c'est notre convention.

### PIN (DE52 + DE53)
- [SPEC] DE52 = b 8 (bloc PIN 64 bits). Chiffre sous ZPK si DE53 pos1-2=02.
- [SPEC] DE53 LLVAR n..99 : pos1-2 methode (00 aucune / 02 ZPK),
  pos3-4 format block (01 ANSI / 25 pre-valide / 99 absent),
  pos5-7 index cle PIN (000), pos8-10 index cle MAC (000).
- [INF] "ANSI" (pos3-4=01) = ISO 9564 Format 0 (PIN XOR 12 chiffres droite du PAN
  hors cle de controle).
- Exemple achat WITH_PIN : DE53 = "0201000000".

### MAC (DE128)
- [SPEC] DE128 = b 8, algo FIPS PUB 113, present "Tous les messages".
  DE64 NON utilise (DE128 = seul champ MAC dans toutes les tables de messages).
- [INF] FIPS 113 = DES CBC-MAC (DAA), bloc chiffre final 8 octets, cle = ZAK (P10).
- [DECISION GAP-B] plage MAC = message packe du MTI (inclus) -> dernier champ
  avant DE128. Header PowerCARD EXCLU (a confirmer si vrai interop un jour).

### Codes reponse utiles (ANNEXE A, DE39, n3)
- 000 Approuve / 001 Approuve avec id / 085 Aucune raison de refuser (Account Verif)
- 100 Ne pas honorer / 101 Carte perimee / 116 Fonds insuffisants / 117 PIN incorrect
- 106 Nb essais PIN depasse / 902 Transaction invalide / 909 Defaillance systeme
- 911 Emetteur pas repondu a temps / 912 Emetteur indisponible / 992 Verif PIN impossible
- Sign-on/echo/signoff : reponse OK = 1814 DE39=800 (deja implemente incr.1).

### CHECKS AVANT DE CODER
- [BLOQUANT 2.2] SwamPackager doit packer DE48 en LLLVAR (3 digits), pas LLVAR :
  une cle depasse 99 chars => sinon le key-exchange casse. A VERIFIER EN PREMIER.
- Tables swam_iss_keys/swam_acq_keys/swam_kek ont deja key_type, key_under_kek,
  key_under_lmk, kcv => pretes.
- Infra reutilisable : HsmService (generateWorkingKey/importWorkingKey/
  encryptPinBlock/decryptPinBlock/generateMac/verifyMac/computeKcv) + ThalesHsmService.
  Flux DMAS a repliquer : KekBootstrapController, KeyExchangeController, SessionOrchestrator.

### PLAN (chaque etape commit + test avant la suivante)
- 2.1 Bootstrap KEK SWAM (ZMK sous LMK -> swam_kek). Peu dependant spec. EN PREMIER.
- 2.2 Key exchange 1804 DE24=811/899, ZPK/ZAK sous KEK, DE48 P10/P16 (GAP-A=a).
- 2.3 MAC reel DES CBC-MAC FIPS 113 sur DE128 (GAP-B), via generateMac/verifyMac.
- 2.4 PIN block reel Format 0 DE52 + DE53, via encrypt/decryptPinBlock.

---

## 17. SWAM incr.2 TERMINE + etat session 10 (2026-07-08)

> Session 10 : finalisation crypto SWAM, vrai ZMK ceremonie de cles, packaging deploiement.
> Branche : feature/multi-network (merge -> chore/cleanup-modules, tag v1.3.0).

### INCR.2 CRYPTO COMPLET (commits d8fb1fd..5a90592, tous sur feature/multi-network)

| Incr. | Contenu | Commit | Valide |
|-------|---------|--------|--------|
| 2.1 | Bootstrap KEK (ZMK sous LMK -> swam_kek) | d8fb1fd | E2E OK |
| 2.2a | SwamDe48 TLV, HSM cle simple longueur ZAK, selftest | ec58d5a | round-trip OK |
| 2.2b | Key exchange 811/899, ZPK/ZAK, KCV concordants iss/acq | cd335b1 | KCV match=t |
| 2.3 | MAC reel DE128 DES-CBC-MAC FIPS113 ZAK 8o | 13500f4 | achat MAC valide/rejete |
| 2.4 | PIN block DE52/DE53 ZPK ISO-0, DE39=117 PIN KO | 5a90592 | 3 cas valides |

### DECISIONS CRYPTO FIGEES (complement section 16)
- [DECISION] ZAK simple longueur (8o) MAIS jPOS generateCBC_MAC() = Retail MAC Alg3 (exige 16o).
  Resolution : DES-CBC-MAC MAISON (FIPS 113 Alg1) via javax.crypto dans generateMacSingle/verifyMacSingle.
  La ZAK est dechiffree depuis key_under_kek+kek_clear (3DES-ECB) puis DES/CBC/NoPadding, MAC=dernier bloc.
  DMAS inchange (generateMac/verifyMac de l'interface HsmService non touchees).
- [DECISION] Plage MAC = champs 4,11,37,41,42 en ASCII (comme DMAS, via McMacBuilder). Parametrable yml.
- [DECISION] PIN block : encryptPinBlock/decryptPinBlock standard jPOS (ZPK 16o, jposLen=128, FORMAT00 ISO-0).
  Pas de probleme de longueur car ZPK est double longueur. Methodes HsmService interface directement.
- [DECISION] DE39=117 (PIN incorrect, annexe A HPS), tolerant si ZPK absente ou DE53 invalide.

### VRAIE ZMK (ceremonie de cles, session 10)
- ZMKC#1 KCV=5E5743 XOR ZMKC#2 KCV=DCCC90 -> ZMK=E95870465DD6CB8F041F5EBA4F7C62CB KCV=F6EE59
- Double longueur (16 octets). Verifiee : KCV recalcule = F6EE59 des 2 cotes (issuer + acquereur).
- Bootstrap : POST /api/admin/swam/kek/bootstrap {memberGroupId:"TESTGRP01", kekClear:"E958...62CB"}
- Note : la KEK de test (0123...EF x3) reste dans les SQL de deploiement. A rer-bootstrapper avec ZMK reelle
  apres demarrage sur cible.

### GIT (etat apres session 10)
- Tag v1.3.0 sur 5a90592 (poussee).
- Merge feature/multi-network -> chore/cleanup-modules (commit 9f4b11e, 62 fichiers, push OK).
- feature/multi-network reste ouverte pour la suite.

### PACKAGING (dist-swam-issuer)
- Cree par package-swam-issuer.sh : JAR + 3 LMK + 11 SQL ordonnes + config externe + 2 .bat + README.
- Base ciblee : scenariogenerator (pas generatorscenario = vieux nom).
- A REGENERER si nouveau code (les 2.3/2.4 post-packaging ne sont pas encore dans le dist).
  Commande : bash /d/MoneyCore/package-swam-issuer.sh

### LOGS ET CONFIG (swam-issuer)
- Niveau de log : application.yml -> logging.level.com.staging.sg.swam.issuer: DEBUG/INFO.
- Port jPOS : table networks.issuer_iso_port (lu par SwamJposServer.resolvePort(), fallback 8510).
- Port REST : application.yml -> server.port (8511).
- Champs MAC : application.yml -> swam.mac.fields / representation / enforce / reject-code.
- LMK : application.yml -> dmas.lmk.file (chemin relatif si config externe).
- PIN en clair dans les logs : patch debug temporaire (patch-swam-pin-log.sh). NE PAS COMMITTER.

### PROCHAIN CHANTIER (ouvert)
- **Connexion vrai membre** : le switch ecoute sur 0.0.0.0:8510 (accessible LAN). Pour accueillir un membre
  avec un group id different de TESTGRP01 : patch member_group_id dynamique (DE32/DE33) dans SwamJposServer.
- **Package dist-swam-issuer a regenerer** (post 2.3/2.4).
- **SESSION_RESUME front** : traduire Help + Dashboard + visualisation roles (cf section 9 PENDING GLOBAUX).
- **Versionner sg-frontend** (toujours pas de remote GitHub).
- **Etendre port endpoint** a sg-swam-issuer/sg-swam-acquirer (jPOS risque au restart, a tester prudemment).

---

## 18. PROCEDURES OPERATIONNELLES (reference rapide)

> Commandes a lancer dans Git Bash depuis la racine du projet.
> Adapter les chemins si le projet n'est pas sur D:\MoneyCore\.

### Demarrage rapide SWAM (sur la machine de dev)

```bash
# 1. Demarrer le switch (issuer) — logs en direct
bash /d/MoneyCore/start-swam-issuer.sh

# 2. Dans un autre terminal : demarrer le membre (acquereur)
JAVA="/d/MoneyCore/jdk-21.0.11/bin/java.exe"
JAR=$(find /d/MoneyCore/ScenarioGenerator/sg-swam-acquirer/target -name "*.jar" ! -name "*.original" | head -1)
nohup "$JAVA" -jar "$JAR" > /tmp/swam-acquirer.log 2>&1 &
```

### Test E2E SWAM complet (valide tout le flux crypto)

```bash
bash /d/MoneyCore/ScenarioGenerator/deploiement/swam-e2e.sh
```

Couvre en automatique (12 tests, resultat PASSED/FAILED) :
- Compilation des 2 modules
- Arret/demarrage switch + membre
- Bootstrap KEK (issuer + acquereur) + verification KCV concordants
- Sign-on (1804 DE24=801)
- Key exchange ZPK (811) + ZAK (899) + verification en base
- Achat sans PIN -> approuve (DE39=000)
- Achat PIN correct 1234 -> approuve, solde debite
- Achat PIN incorrect 9999 -> refuse (DE39=117), solde inchange
- Achat fonds insuffisants -> refuse (DE39=116)
- Sign-off (1804 DE24=802)

### Bootstrap KEK manuel (apres demarrage sur nouvelle machine)

```bash
# Issuer (switch) — port REST 8511
curl -X POST http://localhost:8511/api/admin/swam/kek/bootstrap \
  -H "Content-Type: application/json" \
  -d '{"memberGroupId":"TESTGRP01","kekClear":"E95870465DD6CB8F041F5EBA4F7C62CB"}'
# Verifier KCV=F6EE59 dans la reponse

# Acquereur (membre) — port REST 8094
curl -X POST http://localhost:8094/api/admin/swam/kek/bootstrap \
  -H "Content-Type: application/json" \
  -d '{"memberGroupId":"TESTGRP01","kekClear":"E95870465DD6CB8F041F5EBA4F7C62CB"}'
```

### Key exchange manuel (apres bootstrap KEK)

```bash
curl -X POST http://localhost:8094/api/admin/swam/network/signon
curl -X POST http://localhost:8094/api/admin/swam/keyexchange/zpk
curl -X POST http://localhost:8094/api/admin/swam/keyexchange/zak
```

### Tests d'achat manuels

```bash
PAN="5321962145453348"
BASE="http://localhost:8094/api/admin/swam"

# Achat sans PIN
curl -s -X POST "$BASE/purchase?pan=$PAN&amount=000000010000"

# Achat avec PIN correct
curl -s -X POST "$BASE/purchase?pan=$PAN&amount=000000010000&pin=1234"

# Achat avec PIN incorrect -> DE39=117
curl -s -X POST "$BASE/purchase?pan=$PAN&amount=000000010000&pin=9999"
```

### Verifications en base

```bash
PSQL="/d/MoneyCore/PostgreSQL/18/bin/psql.exe"
export PGPASSWORD=postgres123

# KEK chargee (KCV doit etre F6EE59 si vraie ZMK)
"$PSQL" -U postgres -d scenariogenerator -c \
  "SELECT member_group_id, kcv, kek_under_iss_lmk IS NOT NULL AS iss, kek_under_acq_lmk IS NOT NULL AS acq FROM swam_kek;"

# Cles de session (ZPK + ZAK, KCV concordants iss/acq)
"$PSQL" -U postgres -d scenariogenerator -c \
  "SELECT i.key_type, i.kcv AS iss_kcv, a.kcv AS acq_kcv, (i.kcv=a.kcv) AS match \
   FROM swam_iss_keys i JOIN swam_acq_keys a \
   ON i.member_group_id=a.member_group_id AND i.key_type=a.key_type \
   WHERE i.status='ACTIVE' AND a.status='ACTIVE';"

# Dernieres transactions
"$PSQL" -U postgres -d scenariogenerator -c \
  "SELECT pan, stan, amount, response_code, status FROM swam_iss_transactions ORDER BY id DESC LIMIT 5;"

# Solde des cartes de test
"$PSQL" -U postgres -d scenariogenerator -c \
  "SELECT pan, balance, status FROM swam_cards;"
```

### Installation sur un nouveau PC

> Methode validee et testee : `create-db.sql` + `run-create-db.sh`.
> Teste sur base vide — PASSED (44 tables, swam_*=6, swam_cards=3, networks SWAM iso_port=8510).

**Etape 1 — Sur le PC CIBLE**

```bash
# 1. Cloner le projet
git clone https://github.com/ouadieelhadj/ScenarioGenerator.git /f/ScenarioGenerator
cd /f/ScenarioGenerator && git checkout feature/multi-network

# 2. Demarrer PostgreSQL portable (premiere fois : initdb d abord)
PGDATA="/f/MoneyCore/pgsql/data"
# Si initdb pas encore fait :
"/f/MoneyCore/pgsql/bin/initdb.exe" -D "$PGDATA" -U postgres --pwprompt
# mot de passe : postgres123
# Demarrer :
"/f/MoneyCore/pgsql/bin/pg_ctl.exe" -D "$PGDATA" -l "/f/MoneyCore/pgsql/pgsql.log" start

# 3. Creer la base (une seule commande)
bash /f/ScenarioGenerator/deploiement/run-create-db.sh

# Attendu en fin :
#   tables total : 44 | swam_* : 6 | swam_cards : 3 | networks SWAM : 8510

# 4. Valider avec le test E2E SWAM
bash /f/ScenarioGenerator/deploiement/swam-e2e.sh
# Attendu : PASSED (12/12)
```

Notes :
- Adapter les chemins si le projet est sur D:\ au lieu de F:\.
- JDK 21 : dezipper dans /f/MoneyCore/jdk-21.0.11/ (swam-e2e.sh l attend la).
- PostgreSQL portable : zip binaire sur enterprisedb.com (pas le .exe installeur).
- run-create-db.sh auto-detecte psql (cherche dans D:\ et F:\).
- create-db.sql contient : users + DROP/CREATE base + sequences + tables + donnees ref + FK + index.
- Apres E2E, bootstrapper la vraie ZMK si necessaire (voir section Bootstrap KEK manuel).

**Si create-db.sql doit etre regenere (schema a change)**

```bash
# Sur le PC source (celui avec la base live)
bash /d/MoneyCore/ScenarioGenerator/deploiement/build-create-db.sh
cd /d/MoneyCore/ScenarioGenerator
git add deploiement/create-db.sql
git commit -m "deploy: regeneration create-db.sql"
git push origin feature/multi-network
```

### Arret propre

```bash
# Arreter le switch et le membre
for PORT in 8510 8511 8094; do
  for p in $(netstat -ano 2>/dev/null | grep LISTENING | grep ":$PORT" | awk '{print $NF}' | sort -u); do
    taskkill //PID "$p" //F 2>/dev/null || true
  done
done

# Arreter PostgreSQL portable
"/d/MoneyCore/PostgreSQL/18/bin/pg_ctl.exe" -D "/d/MoneyCore/PostgreSQL/18/data" stop
```

### Scripts utiles (dans /d/MoneyCore/)

| Script | Role |
|--------|------|
| `start-swam-issuer.sh` | Compile + lance le switch en avant-plan (logs visibles) |
| `swam-e2e.sh` (deploiement/) | Test E2E complet SWAM |
| `install-full-db.sh` | Cree la base complete sur un nouveau PC |
| `create-full-db.sh` | Genere un dump SQL complet (transfert vers autre PC) |
| `db-restore.sh` | Restaure depuis un dump SQL |
| `package-swam-issuer.sh` | Package le switch pour deploiement (JAR+SQL+bat) |

---

## 19. SESSION 11 — INSTALLATION NOUVEAU PC (2026-07-09, COMPLETED)

> Installation et validation complete de la plateforme SWAM sur un PC secondaire
> (Windows 10, F:\MoneyCore\, PostgreSQL 18 portable zip, JDK 26, IntelliJ).
> E2E SWAM : PASSED 27/27.

### Environnement PC secondaire valide
- PostgreSQL 18 portable : F:\MoneyCore\pgsql\ (initdb UTF8 locale=C via cygpath)
- JDK 26 : F:\MoneyCore\jdk-26_windows-x64_bin\jdk-26.0.1\bin\java.exe
  (compatible source/target=21 du pom.xml — pas de --release donc pas de blocage)
- Maven : F:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn
- Projet : F:\ScenarioGenerator (git clone + checkout feature/multi-network)

### Pieges rencontres et solutions

**[PIEGE] initdb --pwfile sous Git Bash**
- --pwfile=<(echo ...) -> convertit en /proc/PID/fd/N -> FileNotFoundException
- Solution : cygpath -w pour convertir le chemin bash en chemin Windows natif.

**[PIEGE] LMK chemin en dur dans JposHsmService.java**
- @Value("${dmas.lmk.file:D:/MoneyCore/ScenarioGenerator/keys/dmas-lmk.lmk}")
- Solution : ajouter dans application.yml des modules SWAM :
  dmas.lmk.file: F:/ScenarioGenerator/keys/dmas-lmk.lmk
- Le dossier keys/ est desormais versionne dans le repo (environnement test).

**[PIEGE] Grants SWAM manquants dans create-db.sql**
- swam_kek, swam_iss_keys, networks non accordes a swam_issuer_user / swam_acquirer_user.
- Solution : grants complets ajoutes en fin de create-db.sql (commit session 11).
- swam_issuer_user : swam_kek/iss_keys/iss_transactions/cards (SUG) + networks (S)
- swam_acquirer_user : swam_kek/acq_keys/acq_transactions (SUG) + networks/cards (S)
- Les deux : USAGE+SELECT sur ALL SEQUENCES

**[PIEGE] PostgreSQL 18 : bool retourne "true"/"false" au lieu de "t"/"f"**
- swam-e2e.sh comparait [ "$ZPK_MATCH" = "t" ] -> toujours FAIL sur PG18.
- Solution : [ "$ZPK_MATCH" = "t" ] || [ "$ZPK_MATCH" = "true" ] (compatible PG16+PG18).
- Corrige dans deploiement/swam-e2e.sh (commit session 11).

**[PIEGE] JAR verrouille par le process lors de mvn clean**
- Solution : toujours killer les ports 8510/8511/8094 avant de relancer le E2E.

**[PIEGE] Ordre SQL des migrations**
- Les migrations separees jouees dans le mauvais ordre cassent les FK.
- Solution : utiliser uniquement create-db.sql (standalone, ordre correct garanti).

### Procedure demarrage rapide PC secondaire (F:)

    # 1. Demarrer PostgreSQL
    "/f/MoneyCore/pgsql/bin/pg_ctl.exe" -D "/f/MoneyCore/pgsql/data" -l "/f/MoneyCore/pgsql/data/postgres.log" start

    # 2. Killer les ports si services en cours
    for PORT in 8510 8511 8094; do
      for p in $(netstat -ano 2>/dev/null | grep LISTENING | grep ":$PORT " | awk '{print $NF}' | sort -u); do
        taskkill //PID "$p" //F 2>/dev/null || true
      done
    done

    # 3. Lancer le E2E
    bash /f/MoneyCore/swam-e2e-f.sh
