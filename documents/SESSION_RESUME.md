# SESSION RESUME — ScenarioGenerator

> Fichier de reprise de session : etat d'avancement, ce qui est en cours, ce qui reste.
> Couvre DEUX depots : le BACK `ScenarioGenerator` (Java/Spring, repo GitHub) et le
> FRONT `sg-frontend` (Angular 18, dossier separe non encore versionne).

**Derniere mise a jour :** 2026-07-14 (session 13 : MAC valide par Way4 RC[0] + flux switch reel sign-on/ZPK/ZAK OK)
**Branche back :** feature/multi-network (dev, a jour) / chore/cleanup-modules (merge v1.3.0)
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

> ATTENTION : les decisions GAP-A (sens du key exchange), GAP-B (plage MAC),
> ZAK/P10 et DE128=8 octets sont **OBSOLETES**. Voir section 20 (interop reelle Way4).

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

> ATTENTION : les DECISIONS CRYPTO FIGEES ci-dessous (ZAK simple longueur, plage MAC
> 4,11,37,41,42, DE128 8 octets) sont **OBSOLETES** face au vrai membre. Voir section 20.

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

---

## 20. SESSION 12 — INTEROP MEMBRE REEL WAY4 (2026-07-10 / 2026-07-13, EN COURS)

> ATTENTION : les decisions crypto de la section 20.7 (3DES-CBC-MAC, plage MAC,
> "pas de ZAK") sont **OBSOLETES**. Le vrai algo est X9.19 et il existe bien une
> ZAK poussee par le centre. Voir SECTION 21 (validee contre Way4 + switch reel).

> Premiere connexion d'un VRAI membre (Way4 / PowerCARD, machine Linux 10.23.33.114)
> sur notre switch SWAM. Resultat : SIGN-ON et SIGN-OFF PASSENT (1804/801 -> 1814/800).
> Cette section CORRIGE plusieurs decisions des sections 16 et 17, qui etaient des
> hypotheses (GAP-A / GAP-B) et se revelent FAUSSES face au vrai membre.

### 20.1 TOPOLOGIE DE TEST

    Membre Way4 (Linux)          Aiguilleur / jump host         Switch SWAM
    10.23.33.114        ---->    10.23.33.126:8510      ---->   10.2.54.21:8510
                                 (netsh portproxy)

- Pas de droits admin sur 10.2.54.21 -> pare-feu non ouvrable -> le portproxy restait
  bloque en SYN_SENT. SOLUTION RETENUE : on a deplace le switch SWAM SUR le jump host
  10.23.33.126 (droits admin OK), et supprime le portproxy.
  `netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=8510`
- Sur 10.23.33.126 : PostgreSQL 18 portable dans D:\pgsql, JDK 26 dans
  D:\jdk-26\jdk-26.0.1, JAR + keys dans D:\swam-issuer.
- Lancement du switch (CMD, pas PowerShell — PowerShell casse les backslash du -D) :
  `D:\jdk-26\jdk-26.0.1\bin\java.exe -Ddmas.lmk.file=D:/swam-issuer/keys/dmas-lmk.lmk
   -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5432/scenariogenerator
   -Dspring.datasource.username=swam_issuer_user -Dspring.datasource.password=postgres123
   -jar D:\swam-issuer\sg-swam-issuer-1.0.0-SNAPSHOT.jar`
- Bootstrap KEK avec la VRAIE ZMK (section 17) :
  `curl -X POST http://localhost:8511/api/admin/swam/kek/bootstrap -H "Content-Type: application/json"
   -d "{\"memberGroupId\":\"TESTGRP01\",\"kekClear\":\"E95870465DD6CB8F041F5EBA4F7C62CB\"}"`
  -> KCV attendu F6EE59.

### 20.2 TRAME REELLE WAY4 (sign-on 1804, capturee)

    30 30 39 34                          "0094"        prefixe longueur, 4 car ASCII
    49 53 4F 37 30 31 30 30 30 30 30     "ISO70100000" header PowerCARD, 11 octets
    31 38 30 34                          "1804"        MTI, 4 car ASCII
    82 30 01 80 88 00 ... (16 octets)    bitmap BINAIRE (pas ASCII-hex)
    <DEs en ASCII>
    <DE128 = MAC, 8 car ASCII = 4 octets binaires>

Header PowerCARD (11 o) = 'ISO' (3) + entete (8) :
- pos 1   : produit ('6' emetteur, '7' emetteur+acquereur, '8' acquereur)
- pos 2-5 : version protocole = '0100'
- pos 6-8 : premier DE en erreur, sinon '000'

DEs du sign-on Way4 : DE7, DE11, DE12, DE24=801, DE25, DE33 (LLVAR), DE37, DE128.

### 20.3 CORRECTIONS FAITES ET POUSSEES (commits 9082f45, 9e7fc44)

- **[FIX] Header PowerCARD 11 octets** : `SwamLengthChannel` lit prefixe(4) puis
  header(11), retourne msgLen-11 au packager ; en emission il ajoute 11 a la longueur
  et ecrit `ISO60100000`. Le packager ne voit QUE MTI+bitmap+DEs.
- **[FIX] Bitmap BINAIRE** : `SwamPackager` fields[1] = `IFB_BITMAP(16)` (etait IFA_BITMAP,
  qui attend de l'ASCII-hex -> parsing muet).
- **[FIX] Log payload brut** : `SwamLengthChannel` override `unpack(ISOMsg, byte[])`,
  logge HEX+ASCII du payload complet AVANT parsing, try/catch pour ne plus avaler
  les ISOException silencieusement. NE PAS overrider `streamReceive()` : ca casse la
  liaison permanente. Cycle jPOS = receive() -> getMessageLength() -> streamReceive() -> unpack().
- **[FIX] Key push apres sign-on** : `SwamJposServer.pushZpk()` derriere le flag
  `swam.keypush.enabled` (defaut **false** pour ne pas casser l'E2E local).
  `handleKeyExchange()` (811/899 entrants) est CONSERVE en parallele.
- **[CONFIG]** `dmas.lmk.file` + `logging.level` ajoutes dans les 2 application.yml.

### 20.4 RESULTAT INTEROP

    2026-07-13 09:13 — [SWAM-SRV] Recu MTI=1804 STAN=133001
                       [SWAM-SRV] Gestion reseau SIGN-ON (DE24=801)
                       [SWAM-SRV] Repondu 1814 DE39=800 (SIGN-ON)     <-- OK
                       [SWAM-SRV] Recu MTI=1804 STAN=133002 DE24=802
                       [SWAM-SRV] Repondu 1814 DE39=800 (SIGN-OFF)    <-- OK

Way4 fait sign-on puis sign-off, puis keep-alive (bytes=0 toutes les 2-5 s).
Il n'envoie PAS de demande de key exchange : il ATTEND que le centre pousse la cle.

### 20.5 FLUX REEL CONFIRME (annule et remplace le GAP-A de la section 16)

    1. Membre -> Switch : 1804 DE24=801   (sign-on)
    2. Switch -> Membre : 1814 DE39=800   (reponse sign-on)
    3. Switch -> Membre : 1804 DE24=811   (KEY PUSH : ZPK sous ZMK, dans DE48)
    4. Membre -> Switch : 1814 DE39=800   (accuse) + le membre sauvegarde la ZPK
    5. Membre -> Switch : 0200            (transaction, MAC pose)
    6. Switch -> Membre : 0210

C'est donc le CENTRE qui POUSSE la cle spontanement apres le sign-on.
Le membre ne demande jamais rien. (Section 16 disait l'inverse : c'etait notre convention.)

### 20.6 FORMAT DE48 REEL (corrige la section 16)

Observe sur le switch reel :  `P16033XA10D7ED90967C5E18A7B5BA57FFB0672`

    P16  = tag
    033  = longueur = 33 = 1 (le X) + 32 (la cle hex)
    X    = PREFIXE OBLIGATOIRE (indique cle chiffree sous ZMK)
    A10D...0672 = ZPK chiffree sous ZMK, 32 hex = 16 octets

=> notre `SwamDe48` doit produire `put(TAG_ZPK, "X" + zpkUnderZmkHex)` et NE PAS
   emettre de tag KCV separe (K16/K10 : tags maison, le vrai membre ne les attend pas).

### 20.7 MAC REEL SWAM (annule et remplace le GAP-B + la decision ZAK de la section 17)

Commande HSM Thales cote SWAM (fournie par l'equipe) :

    M6 0 0 01 1 003 U38471EE27378C7DFAEA2CD18E63172EE <data>

    M6   = command code
    0    = single block
    0    = input format binaire
    01   = ISO 9797 MAC Algorithm 1 (= ANSI X9.9)
    1    = ISO 9797 Padding Method 1 (padding 0x00)
    003  = TAK  (Terminal Authentication Key, sous paire LMK 16-17)
    U... = cle DOUBLE longueur (le U = double length Thales)

DECISIONS FIGEES (validees avec l'equipe SWAM) :
- **Cle MAC = la ZMK, utilisee comme TAK.** Il n'y a PAS de ZAK/P10 dans le vrai flux.
  => on ne pousse QUE la ZPK. Le key exchange 899 n'existe pas cote reel.
- **Algorithme = 3DES-CBC-MAC** (ISO 9797 Alg 1 avec cle double longueur),
  padding Method 1 (zeros). Pas le DES simple de l'incr. 2.3.
- **Donnee MACee = le message PACKE, SANS MTI, SANS BITMAP, SANS DE128.**
  Donc les DEs avec leurs prefixes de longueur (ex : DE33 -> `06200853`,
  DE48 -> `039P16033X...`). Ce n'est PAS la liste `4,11,37,41,42` de McMacBuilder.
- **DE128 = les 4 PREMIERS octets du MAC** (le MAC brut fait 8 octets).
  => `SwamPackager` fields[128] = `IFA_BINARY(4)` (8 caracteres ASCII sur le fil).
- Cote reponse : le switch pose son propre DE128 ; le membre verifie en retirant
  le DE128 recu avant de recalculer. Symetrique.

### 20.8 A FAIRE (reprise immediate)

1. **`SwamMacBuilder`** (nouveau, dans sg-common/iso/crypto) : packe l'ISOMsg, retire
   MTI (4o) + bitmap (16o) + DE128, retourne les octets a MACer.
2. **`JposHsmService.generateMacZmk(byte[] data, String zmkClearHex)`** : 3DES-CBC-MAC
   ISO 9797 Alg 1, padding zeros, retourne 8 octets (troncature a 4 cote appelant).
3. **`SwamJposServer`** : `verifyIncomingMac()` / `poseMacOnResponse()` utilisent la ZMK
   (kek clear) + troncature 4 octets. Plus de MAK/ZAK.
4. **`SwamPackager`** : fields[128] = `IFA_BINARY(4)`.
   ATTENTION : passer DE128 de 8 -> 4 CASSE l'E2E local actuel
   (`Binary data length not the same as the packager length (8/4)`) : l'E2E doit etre
   adapte en meme temps (il teste encore le ZAK exchange 899, qui disparait).
5. Activer `swam.keypush.enabled=true` pour le mode reel, puis retester avec Way4 :
   sign-on -> key push ZPK -> accuse -> transaction 0200.

### 20.9 PIEGES RENCONTRES (session 12)

- **[PIEGE] Python absent** sur le PC : tous les scripts de patch doivent utiliser
  `sed` / `cat`, jamais `python`.
- **[PIEGE] JAR verrouille** par le process qui tourne -> `mvn clean` echoue.
  Toujours killer les ports 8510/8511/8094 AVANT de recompiler.
- **[PIEGE] PowerShell casse `-Ddmas.lmk.file=D:\...`** (backslash interpretes).
  Lancer le JAR depuis CMD, et utiliser des slash : `D:/swam-issuer/keys/dmas-lmk.lmk`.
- **[PIEGE] Ne pas overrider `streamReceive()`** dans le channel : la liaison
  permanente se casse. Overrider `unpack()` pour tracer.
- **[PIEGE] `\\10.2.54.21\f$`** inaccessible entre les 2 sous-reseaux : copie manuelle
  via le presse-papier / lecteur RDP (`\\tsclient\f\`).
- Erreurs Way4 `RC[96] Invalid key usage` : c'etait AVANT le fix du header/bitmap.
  Une fois le parsing corrige, le sign-on passe. Ne pas conclure trop vite a un
  probleme HSM cote membre.

---

## 21. SESSION 13 — MAC VALIDE PAR WAY4 + FLUX SWITCH REEL (2026-07-14)

> Journee majeure. Le MAC SWAM est VALIDE par le membre reel Way4 (RC[0]), et le
> flux reseau complet est deroule contre le SWITCH SWAM REEL (sign-on + push
> ZPK/ZAK). Cette section CORRIGE la section 20.7, dont l'algorithme (3DES-CBC),
> la plage MAC et l'affirmation "pas de ZAK" etaient FAUX.

### 21.1 CE QUI EST VALIDE (avec preuves)

| Element | Preuve |
|---|---|
| MAC sortant : X9.19 + ZMK + bitmap bit128 ON | Way4 HSM : `Verify MAC Rs RC[0] VerifRC[0]` |
| Sign-on membre -> switch REEL accepte | reponse `1814 DE39=800` |
| Import ZPK (tag P16) 16 octets depuis switch reel | KCV=0D765E |
| Import ZAK (tag P10) 16 octets depuis switch reel | KCV=B85F60 |
| Header produit '6' accepte par le switch reel | pas de rejet |

Commits : b5abde6 (MAC X9.19 valide Way4), a93927e (flux switch reel).

### 21.2 LE MAC SWAM — LA VERITE (annule et remplace section 20.7)

**Sens MEMBRE -> SWITCH (ce que NOUS emettons, VALIDE) :**
- Algorithme : **ANSI X9.19 = ISO 9797-1 Algorithme 3** (retail MAC).
  NB : PAS le 3DES-CBC simple (Alg 1) qu'on avait code au depart.
  Retail MAC = CBC-DES avec K1 sur tous les blocs, puis sur le DERNIER bloc :
  DES-decrypt K2 + DES-encrypt K1.
- Cle : la **ZMK** en clair (16 octets), utilisee comme cle MAC. Confirme :
  on reproduit le MAC de Way4 a l'octet pres.
- Padding : ISO 9797 Method 1 (zeros).
- Donnee MACee : le message PACKE = **MTI + bitmap(16o) + DEs**, prive de la
  seule VALEUR du DE128. Le BIT 128 reste ALLUME dans le bitmap.
- DE128 : les **4 premiers octets** du MAC (IFA_BINARY(4)).

**PIEGE CRITIQUE DU BITMAP (cause du "MAC check failed" initial) :**
- `msg.unset(128)` eteint le bit 128 -> jPOS n'emet plus le bitmap secondaire
  -> bitmap 8 octets au lieu de 16, premier octet 0x02 au lieu de 0x82
  -> buffer ampute de 8 octets, MAC faux.
- SOLUTION (SwamMacBuilder) : `copy.set(128, new byte[]{0,0,0,0})` puis packer,
  puis retirer les 8 derniers octets ASCII. Le bitmap garde bit128 ON.

**VECTEUR DE TEST (rejouable sans Way4) :**
- Sign-on Way4 recu, donnee 75 octets :
  `31383034823001808800...` (MTI 1804 + bitmap + DEs)
  hex complet dans les logs du 14/07.
- ZMK = E95870465DD6CB8F041F5EBA4F7C62CB
- X9.19 -> MAC 8o `586AC5D390D8...` -> DE128 (4o) = **586AC5D3**
- Si un futur calcul ne redonne pas 586AC5D3 sur ce buffer, l'implementation
  a regresse.

### 21.3 FLUX RESEAU REEL — sequencement M1..M8 (convention de reference)

```
   MEMBRE                                   SWITCH REEL
   (nous ou Way4)                           10.110.7.72:7097
        |                                        |
  [M1]  | ---- 1804 DE24=801 ----------------->  |  SIGN-ON (MAC ZMK)
  [M2]  | <--- 1814 DE39=800 ------------------  |  reponse sign-on
  [M3]  | <--- 1804 DE24=811 ------------------  |  PUSH ZPK
        |      DE48 = P16 033 X<zpk 16o>         |
  [M4]  | ---- 1814 DE39=800 ----------------->  |  accuse ZPK (MAC ZMK)
  [M5]  | <--- 1804 DE24=899 ------------------  |  PUSH ZAK
        |      DE48 = P10 033 X<zak 16o>         |
  [M6]  | ---- 1814 DE39=800 ----------------->  |  accuse ZAK
  [M7]  | <--- 1804 DE24=802 ------------------  |  SIGN-OFF (declenche par switch)
  [M8]  | ---- 1814 DE39=800 ----------------->  |  accuse sign-off
        ============ session etablie ============
  [T1]  | ---- 1100 (achat) ------------------>  |  AUTORISATION
        |      DE52 (PIN/ZPK) + DE128 (MAC)      |  MAC = ??? (a confirmer)
  [T2]  | <--- 1110 DE39=000 ------------------  |  reponse
```

Points confirmes : ZPK sous tag **P16**, ZAK sous tag **P10**, les deux en
16 octets (double longueur), prefixe 'X' obligatoire dans le DE48.

### 21.4 COMMENT LANCER — 3 SCENARIOS

**SCENARIO A — E2E LOCAL (acquereur <-> issuer, tout en local)**
```bash
# networks doit pointer sur localhost :
psql ... -c "UPDATE networks SET issuer_host='localhost', issuer_iso_port=8510 WHERE code='SWAM';"
bash /f/MoneyCore/swam-e2e-f.sh
# Attendu : 27/27 (KEK de test D5D44F, header 6)
```

**SCENARIO B — WAY4 (membre reel) -> NOTRE ISSUER (switch)**
```bash
# 1. JAR issuer sur .126 (D:\swam-issuer\), lancer depuis CMD :
D:\jdk-26\jdk-26.0.1\bin\java.exe -Ddmas.lmk.file=D:/swam-issuer/keys/dmas-lmk.lmk ^
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5432/scenariogenerator ^
  -Dspring.datasource.username=swam_issuer_user -Dspring.datasource.password=postgres123 ^
  -jar D:\swam-issuer\sg-swam-issuer-1.0.0-SNAPSHOT.jar
# Controle demarrage : "ISOServer demarre sur :8510 — keyPush=true macLen=4o"

# 2. Bootstrap VRAIE ZMK (KCV F6EE59) :
curl -X POST http://localhost:8511/api/admin/swam/kek/bootstrap ^
  -H "Content-Type: application/json" ^
  -d "{\"memberGroupId\":\"TESTGRP01\",\"kekClear\":\"E95870465DD6CB8F041F5EBA4F7C62CB\"}"
# Verifier "kcv":"F6EE59"

# 3. Debug PIN en clair (optionnel) : ajouter -Dswam.debug.pin-clear=true au lancement

# 4. Lancer Way4. Chercher dans les logs issuer :
#    [HSM] generateMacZmk (X9.19 ...) / [SWAM-DUMP] / DE39=800
```

**SCENARIO C — NOTRE ACQUEREUR -> SWITCH SWAM REEL (via relais)**
```bash
# 1. Relais ncat sur .114 (Linux, atteint le switch reel) :
ncat -l 11127 --keep-open --sh-exec "ncat 10.110.7.72 7097" &
ss -lnt | grep 11127     # doit montrer 0.0.0.0:11127

# 2. Depuis .21, verifier le chemin :
powershell -Command "Test-NetConnection -ComputerName 10.23.33.114 -Port 11127"
# TcpTestSucceeded : True

# 3. Pointer networks sur le relais :
psql ... -c "UPDATE networks SET issuer_host='10.23.33.114', issuer_iso_port=11127 WHERE code='SWAM';"

# 4. Demarrer l'acquereur (port 8094) :
java.exe -Ddmas.lmk.file=F:/ScenarioGenerator/keys/dmas-lmk.lmk ^
  -jar F:/ScenarioGenerator/sg-swam-acquirer/target/sg-swam-acquirer-1.0.0-SNAPSHOT.jar

# 5. Bootstrap VRAIE ZMK cote acquereur (port 8094) :
curl -X POST http://localhost:8094/api/admin/swam/kek/bootstrap ^
  -H "Content-Type: application/json" ^
  -d "{\"memberGroupId\":\"TESTGRP01\",\"kekClear\":\"E95870465DD6CB8F041F5EBA4F7C62CB\"}"
# Verifier "kcv":"F6EE59"

# 6. Lancer le flux :
bash /f/MoneyCore/AcquiringSwamReel.sh
# Endpoints : /api/admin/swam/network/signon, /keyexchange/zpk, /keyexchange/zak

# 7. APRES le test : remettre networks en local + enlever le relais
psql ... -c "UPDATE networks SET issuer_host='localhost', issuer_iso_port=8510 WHERE code='SWAM';"
# sur .114 : pkill ncat
```
IMPORTANT scenario C : PREVENIR l'equipe SWAM + ARRETER Way4 (conflit de
session sur DE33=300853, switch de prod).

### 21.5 CE QUI RESTE A FAIRE (questions ouvertes)

1. **MAC ENTRANT du switch NON REPRODUCTIBLE.** Les MAC que le switch REEL pose
   sur ses push (M3 ZPK=1778A700, M5 ZAK=EF334A49) ne se recalculent avec AUCUNE
   combinaison testee (ZMK/ZAK x X9.19/3DES/DES x plages MTI+bitmap/DEs seuls).
   - Le mail SWAM (14/07) donne l'algo du CENTRE : ISO 9797 **Algorithme 1**
     (3DES-CBC avec cle double), padding method 1, cle = **TAK**, sur la donnee
     applicative SANS MTI ni bitmap (commence par "0061...").
   - La TAK n'est ni la ZMK ni la ZAK de session (test exhaustif KO).
   - ACTION : demander a SWAM (a) quelle cle exacte, (b) plage exacte, en leur
     donnant le vecteur 1778A700 + le buffer M3. NON BLOQUANT (on ne verifie pas
     le MAC entrant), mais REQUIS pour MACer le 1100.

2. **DE7 decale d'une heure.** .126 en +01:00, Way4/switch en UTC. On envoie
   14:05, ils envoient 13:05. Corriger : SimpleDateFormat DE7 en UTC
   (setTimeZone(TimeZone.getTimeZone("UTC"))). N'affecte pas le MAC mais peut
   declencher un controle anti-rejeu.

3. **Transaction 1100 (achat) contre le switch reel.** Bloque par : cle MAC a
   confirmer (TAK/ZAK ?), PIN block sous ZPK a valider, PAN de test autorise a
   obtenir de l'equipe, feu vert + Way4 arrete.

4. **Sign-off M7 declenche par le switch** apres les key exchanges : comprendre
   si c'est systematique (test) ou un rejet. Refaire un sign-on avant tout 1100.

### 21.6 FLAG DEBUG PIN

- `-Dswam.debug.pin-clear=true` -> log WARN "*** DEBUG PIN EN CLAIR ***"
  (encrypt cote acquereur + decrypt cote issuer). Defaut false.
- A DESACTIVER EN PRODUCTION. Verifier par `grep -r "pin-clear"` avant livraison.

### 21.7 CLES DE REFERENCE (memo)

| Cle | Valeur claire / KCV | Usage |
|---|---|---|
| ZMK (vraie) | E95870465DD6CB8F041F5EBA4F7C62CB / F6EE59 | chiffre les cles + MAC sign-on |
| ZMK sous LMK | E98805FBB4DE36AC2C7742E242CF80D8 | (stockage) |
| KEK de test | 0123456789ABCDEF... / D5D44F | E2E local uniquement |
| ZPK/ZAK | poussees par le switch, KCV variables | par session |

---

## 22. SESSION 17 — MAC ENTRANT CRACKÉ + FLUX ACQUÉREUR CORRIGÉ (2026-07-17)

> Session majeure. Le MAC entrant du switch SWAM est cracké et prouvé (9AA02ED9 MATCH).
> Le séquencement du flux acquéreur est corrigé (modèle push). Le sign-on est maintenant
> MACé. La bascule ZMK→ZAK est implémentée. Mastercard SMS démarré.
> Commit : b76559c (feature/multi-network).

### 22.1 MAC ENTRANT DU SWITCH — CRACKÉ ET PROUVÉ ✅

**Recette validée (vecteur : sign-on Nabil et CAM -> MAC 9AA02ED9 MATCH) :**
- **Cle** : TAK (Terminal Authentication Key), déchiffrée sous ZMK_VISA
- **Algo** : ISO 9797 **Algorithm 1 en 3DES-CBC** (DESede/CBC/NoPadding, IV=0, padding zéros)
- **Buffer** : **DEs bruts** depuis l'ISOMsg, SANS MTI/bitmap/DE128
- **DE128** : 4 premiers octets du MAC (8 octets complets)
- **TAK n'est PAS la ZMK ni la ZAK** : c'est une clé pré-établie par environnement (ZMK_VISA)

**Clés identifiées :**
| Clé | Valeur claire | KCV | Usage |
|---|---|---|---|
| ZMK_VISA | 13AED5DA1F32347523C708C11F2608FD | 2D617C | transport + déchiffre la TAK |
| ZMK_SWAM | E95870465DD6CB8F041F5EBA4F7C62CB | F6EE59 | notre environnement acquéreur |
| TAK_VISA (clair) | 1FB3F48A6D51832CE91C1C734554086D | D75F09 | MAC entrant (Nabil + CAM) |
| TAK_VISA (sous ZMK_VISA) | E3C8EF7C4EEF54D81CE43CE2BCF33E37 | — | chiffré |
| ZAK_VISA (sous ZMK_VISA) | ADFFE5FF95F121FB6028F0840BEE7A8B (ex) | variable | poussée par switch |

**Point clé : la TAK est commune à tous les membres de l'environnement ZMK_VISA**
(Nabil et CAM partagent la même TAK → même signon MAC 9AA02ED9 pour les deux).

**Classe de test : `SwamMacEntrantTest.java`**
- Package : sg-common/.../iso/crypto
- Toutes les méthodes partent de l'ISOMsg (jamais de buffer en dur)
- `buildRaw(msg, withPrefixes)` : DEs bruts ± préfixes LLVAR/LLLVAR
- `macAlg1_3des(key, data)` : la recette validée
- `macMail(key, data)` : DES simple K1 (recette du mail, à tester)
- Vecteur de test : signon Nabil/CAM, cible 9AA02ED9

**MAC entrant NON reproductible avec ZMK_SWAM** : le switch de notre environnement
utilise une TAK propre qu'on n'a pas encore identifiée.

### 22.2 CORRECTION DU FLUX ACQUÉREUR ✅

**Bug identifié :** AcquiringSwamReel.sh appelait `/keyexchange/zpk` et `/keyexchange/zak`,
qui déclenchaient l'émission de `1804/811` et `1804/899` par l'acquéreur (modèle "pull").
Le switch REEL fonctionne en modèle **PUSH** → il rejetait ces demandes avec `DE39=880`.

**Correction `SwamNetworkController.java` (commit b76559c) :**
- Endpoints `/keyexchange/zpk` et `/keyexchange/zak` **retirés**
- `swamMac.apply(req)` ajouté dans `network()` → MAC sur sign-on/echo/sign-off

**Résultat validé sur switch réel :**
```
→ EMIS  1804/801  SIGN-ON  DE128=B5F8792D   ✅ accepté DE39=800
← REÇU  1804/811  PUSH ZPK  → PEK importée (KCV=4A4D4A) + accusé 800  ✅
← REÇU  1804/899  PUSH ZAK  → MAK importée (KCV=09EAC4) + accusé 800  ✅
← REÇU  1804/802  SIGN-OFF  → accusé 800  ✅
```
Plus aucun `1804/811` ou `1804/899` émis par nous. Plus aucun `DE39=880`.

**Correction `SwamMac.java` (commit b76559c) :**
- `apply()` bascule ZMK → **ZAK (MAK)** après réception de la ZAK :
  - si MAK ACTIVE existe dans `swam_acq_keys` → `generateMac(input, mak.keyUnderLmk, ...)`
  - sinon → `generateMacZmk(input, kek.kekClear)` (ZMK, comportement pré-échange)

### 22.3 SCRIPTS OPÉRATIONNELS (dans /f/MoneyCore/)

| Script | Rôle |
|---|---|
| `run-swam-flux.sh` | Bootstrap ZMK_SWAM + sign-on (remplace AcquiringSwamReel.sh) |
| `run-mac-entrant.sh` | Compile sg-common + lance SwamMacEntrantTest |
| `copy-mac-class.sh` | Copie SwamMacEntrantTest.java dans le bon package |
| `AcquiringSwamReel.sh` | Flux corrigé (signon seulement, push gérés par receiver) |

### 22.4 DÉMARRAGE MASTERCARD SMS

- Guide : Single Message System Guide, 2 June 2026, 1909 pages
- Résumé indexé : `documents/mastercard/MASTERCARD_SMS_RESUME.md`
- Architecture : **1 ligne `networks` MASTERCARD_SMS** (option A, rôles issuer+acquéreur)
- MTI : 02xx/03xx/04xx/06xx/08xx (ISO 8583:1987, `0xxx`)
- **Point critique** : encodage **EBCDIC** sur le lien réseau (≠ ASCII de SWAM)
- Sign-on = **DE70=061**, sign-off=062, echo=270
- **Chantier 1** (packager minimal 0800/0810) : en attente de la liste exacte des DE attributs (Ch.5 du guide)

### 22.5 POINTS OUVERTS

1. **TAK de notre session** (ZMK_SWAM) : non identifiée → MAC entrant non vérifiable.
   ZPK et ZAK de session varient à chaque connexion (poussées par le switch).
2. **Push ZPK MAC** (`C6029E72` Nabil) : pas encore matché (plage des DEs à préciser).
3. **Sign-off après ZAK** : le switch clôt après la distribution des deux clés
   (comportement confirme que le switch fait un cycle de key exchange puis déconnecte).
4. **Transaction 1100** contre le switch réel : bloqué (TAK MAC à confirmer).
5. **Mastercard SMS** : chantier 1 en attente (attributs DE du Ch.5, encodage EBCDIC, header MIP).
6. **DE7 UTC** : décalage +1h pas corrigé (peut déclencher contrôle anti-rejeu).

### 22.6 RÉFÉRENCES CLÉS

- Trace Nabil (signon 9AA02ED9) : vecteur de preuve du MAC entrant
- Trace CAM (signon 9AA02ED9) : confirme TAK commune à l'environnement
- Commit `b76559c` : SwamNetworkController + SwamMac + SwamMacEntrantTest
- ZMK_VISA composantes : C1=`E38FD6D9...`, C2=`D0085DBF...`, C3=`20295EBC...`
- ZMK_SWAM composantes : ZMKC#1=`3B752C98...`, ZMKC#2=`D22D5CDE...`

---

## 22. SESSION 18 — MAC SWAM PROUVE + MODULES MASTERCARD SMS (2026-07-18)

### 22.1 RECETTE MAC SWAM — VALIDEE SUR 4 VECTEURS

Spec Thales M6 obtenue en entier. Decomposition confirmee :

    M6 | 0 | 0 | 01 | 1 | 003 | U<cle 32hex> | <len 4hex> | <buffer>
         |   |   |    |   |     |
         |   |   |    |   |     +-- Key Type 003 = TAK (LMK pair 16-17)
         |   |   |    |   +-------- Padding Method 1 = zeros
         |   |   |    +------------ MAC Algorithm 01 = ISO 9797 Alg 1
         |   |   +----------------- Input Format 0 = Binary
         |   +--------------------- Mode Flag 0 = single block
         +------------------------- pas de delimiteur LMK

Le 'U' de la cle = double longueur Thales -> Algorithm 1 en **3DES-CBC**
(la spec dit "Alg 1 = ANSI X9.9 when used with a single-length key" ;
avec cle double c'est du 3DES).

| Message | MTI/DE24 | MAC | Buffer |
|---|---|---|---|
| Sign-on Nabil/CAM | 1804/801 | `9AA02ED9` | DEs bruts SANS prefixes (31 o) |
| Accuse push ZPK | 1814/811 | `E847C263` | DEs bruts AVEC prefixes LLVAR (96 o) |
| Echo test calcul#1 | 1804/803 | `954FC203` | DEs bruts AVEC prefixes (55 o) |
| Echo test calcul#2 | 1804/803 | `D9AC008B` | buffer#1 + `954FC203` ASCII (63 o) |

**DECOUVERTE MAJEURE — MAC EN DEUX PASSES** (echo test) :
1. passe 1 : MAC sur les DEs bruts -> MAC intermediaire (`954FC203`)
2. passe 2 : MAC sur (DEs bruts + les 8 chars ASCII du MAC#1) -> MAC final (`D9AC008B`)
Le DE128 pose sur le fil est le MAC de la **passe 2**.

### 22.2 CLES DE L'ENVIRONNEMENT VISA (Nabil / CAM)

    ZMK_VISA     = 13AED5DA1F32347523C708C11F2608FD   (KCV 2D617C)
    TAK sous ZMK = E3C8EF7C4EEF54D81CE43CE2BCF33E37
    TAK_VISA     = 1FB3F48A6D51832CE91C1C734554086D   (KCV D75F09)

La TAK est **commune a l'environnement** : meme TAK pour Nabil ET CAM.
Deduction : dechiffrement 3DES-ECB de `TAK sous ZMK` avec `ZMK_VISA`.

### 22.3 SwamMacEntrantTest — CLASSE GLOBALE DE RECETTE

Emplacement : `sg-common/src/main/java/com/staging/sg/common/iso/crypto/`

Teste 12 combinaisons par message :
- cle : TAK vs ZMK
- buffer : avec vs sans prefixes LLVAR/LLLVAR
- calcul : simple / chaine-4o / chaine-8o

Toutes les constructions partent de l'`ISOMsg` (jamais de buffer en dur).
Methodes cles : `buildRaw(msg, withPrefixes)`, `macAlg1_3des(key16, data)`,
`decryptTak(takHex, zmkHex)`.

Scripts : `copy-mac-class.sh` + `run-mac-entrant.sh` (dans /f/MoneyCore/).

### 22.4 POINT NON RESOLU

Le MAC du **push ZPK entrant** (`C6029E72`, 1804/811 recu du switch) n'est
reproductible avec aucune des 12 combinaisons. Raison probable : ce message
est calcule par le HSM du switch, on n'a pas sa commande M6 dans nos logs
(seuls les M6 emis par Nabil sont traces). Point laisse ouvert.

### 22.5 SOLUTION PERENNE POUR LIRE LE GUIDE MASTERCARD

Les PDF imprimes depuis le guide (Print to PDF) ne sont pas lisibles :
images sans couche texte, resolution insuffisante, decalage entre numero de
page affiche et numero de feuille.

**SOLUTION ADOPTEE** : le guide complet exporte en `.txt` depuis Adobe Reader
(Fichier -> Enregistrer sous -> Texte accessible), soit 148 157 lignes.
Fichier : `MDS_m_SMS_Guide_en-us-2026-06-02.txt` (3.4 Mo).
On y cherche n'importe quelle section par `grep -n` + `sed -n 'X,Yp'`.
Plus besoin d'imprimer ni d'uploader des PDF page par page.

### 22.6 ATTRIBUTS DES DE MASTERCARD SMS (extraits du guide)

| DE | Representation | Format | Page |
|---|---|---|---|
| DE7 | n-10 | fixe | 345 |
| DE11 | n-6 | fixe | 352 |
| DE33 | n..10 | LLVAR (2 pos) | 385 |
| DE39 | an-2 | fixe | 399 |
| DE70 | n-3 | fixe | 776 |
| DE96 | n-8 | fixe (binary 8o) | 792 |
| **DE64** | — | **NON UTILISE en SMS** | 775 |
| **DE128** | — | **NON UTILISE en SMS** | 1096 |

DE65, DE66, DE67, DE68 egalement non utilises.
=> **Il n'y a PAS de MAC dans le layout 0800/0810 Mastercard SMS.**

### 22.7 LAYOUTS 0800 / 0810 (Tables 74 et 77 du guide)

**0800 acquirer/issuer-generated** :
MTI, bitmap primaire, DE1, DE7 (M), DE11 (M), DE33 (M), DE48 (C, key exchange
si DE70=161), DE63 (O), DE70 (M), DE96 (C)

**0810 acquirer/issuer-generated** :
MTI, bitmap primaire, DE1, DE7 (M), DE11 (ME), DE33 (ME), DE39 (M),
DE44 (C, si DE39=30), DE48 (C), DE63 (ME), DE70 (ME)

### 22.8 MODULES MASTERCARD SMS CREES

Deux modules, sur le modele SWAM :

| Module | REST | ISO | User PG |
|---|---|---|---|
| `sg-mc-sms-acquirer` (le membre) | 8095 | 8096 | `mc_sms_acquirer_user` |
| `sg-mc-sms-issuer` (simulateur MC) | 8097 | 8098 | `mc_sms_issuer_user` |

Le membre se connecte au MIP Mastercard (Appendix D p.1826 : "Customers
connect to the Mastercard Network through at least two MIP"). En test local,
l'acquereur se connecte a notre issuer simule.

Fichiers crees :
- `sg-common/.../iso/MastercardSmsPackager.java` (tous les DE, DE64/DE128 = null)
- `sg-mc-sms-acquirer/.../network/McJposClient.java` (sign-on/echo/sign-off, DE7 en UTC)
- `sg-mc-sms-acquirer/.../api/McNetworkController.java`
- entites + repositories des deux cotes
- `pom.xml` des deux modules

**PIEGE POM** : le parent est `com.staging:scenario-generator` (PAS
`com.staging.sg:ScenarioGenerator`), sans `<relativePath>`. La dependance
`sg-common` est aussi en `com.staging`. Toujours copier le bloc `<parent>`
d'un module existant qui compile.

Build OK :
```
mvn -pl sg-mc-sms-acquirer,sg-mc-sms-issuer -am clean package -DskipTests -q
```

### 22.9 TABLES MC SMS (script V1__create_mc_sms_tables.sql, execute)

Sur le modele exact des tables SWAM (owner postgres, schema public) :

    mc_sms_kek                (modele swam_kek)
    mc_sms_acq_keys           (modele swam_acq_keys)
    mc_sms_iss_keys           (modele swam_iss_keys)
    mc_sms_acq_transactions   (+ auth_id_response, network_id, retrieval_ref ;
                               response_code en VARCHAR(2) car an-2 chez MC)
    mc_sms_iss_transactions   (idem)
    mc_sms_cards              (+ cvv2, service_code)

Users crees : `mc_sms_acquirer_user` / `mc_sms_issuer_user`, memes grants
que leurs equivalents SWAM.

Ligne `networks` :
```sql
INSERT INTO networks (code, name, iso_version, header_type, packager_class,
                      issuer_host, issuer_iso_port, acquirer_jpos_port, active)
VALUES ('MASTERCARD_SMS', 'Mastercard Single Message System', '1987', 'MC_SMS',
        'com.staging.sg.common.iso.MastercardSmsPackager',
        'localhost', 7001, 8095, true);
```
ATTENTION : la colonne s'appelle `active` (boolean), pas `status`.

### 22.10 A FAIRE (reprise)

1. **Serveur ISO cote issuer** : ecoute 8098, recoit 0800, repond 0810.
2. **Test sign-on local** acquereur -> issuer.
3. **Framing MIP** : 2 octets big-endian suppose dans McJposClient, a confirmer
   (la doc est dans le *Secured Data Communications Guide*, non disponible).
4. **Encodage EBCDIC** : le guide dit EBCDIC p.163. Packager en ASCII (IFA_*)
   pour le dev ; prevoir `MastercardSmsPackagerEbcdic` (IFE_*) pour le MIP reel.
5. **DE96 (Message Security Code)** : "password" en binaire packe pour le
   sign-on. Valeur a obtenir de Mastercard.
6. **Transaction 0200/0210** apres le socle reseau.

---

## 23. SESSION 19 — SOCLE RESEAU MC SMS VALIDE + DECISION MULTI-BANQUES (2026-07-19)

### 23.1 SIGN-ON / ECHO / SIGN-OFF VALIDES EN LOCAL

Flux complet acquereur (8095) -> issuer simule (8098), **une seule socket** :

| Appel | MTI | DE70 | STAN | DE39 |
|---|---|---|---|---|
| sign-on  | 0800 -> 0810 | 061 | 000001 | 00 |
| echo     | 0800 -> 0810 | 270 | 000002 | 00 |
| sign-off | 0800 -> 0810 | 062 | 000003 | 00 |

`grep -c "Connecte au MIP"` = **1** : les trois echanges passent par la meme
liaison. La liaison permanente est prouvee.

Trame sign-on emise (51 octets, framing `0033`) :

    30 38 30 30 | 82 20 00 00 80 00 00 00 04 00 00 00 00 00 00 00
    0719171730  | 000001 | 10 9000000001 | 061

Reponse 0810 (53 octets, framing `0035`) : memes champs + DE39=00.
Les champs ME (DE7, DE11, DE33, DE70) sont bien recopies par l'issuer.

### 23.2 BUGS CORRIGES CETTE SESSION

| Symptome | Cause | Correction |
|---|---|---|
| `fld[0] is null` au packing | MTI absent du packager | `fld[0] = new IFA_NUMERIC(4, "MESSAGE TYPE INDICATOR")` — comme SwamPackager |
| HTTP 401 sur tous les endpoints | pas de `SecurityConfig` dans les modules MC | classe copiee de `sg-swam-acquirer` |
| `server.port` non injecte (Tomcat sur 8080) | `.imports` ne vaut que pour les auto-configurations | `META-INF/spring.factories` avec `org.springframework.boot.env.EnvironmentPostProcessor=` |
| `Could not resolve placeholder 'jwt.expiration-ms'` | propriete absente | `jwt.expiration-ms=86400000` (valeur SWAM) |
| Liquibase changelog introuvable | auto-config active | `spring.liquibase.enabled=false` |
| `Field length 12 too long. Max: 10` sur DE33 | bouchon `000000000001` (12 chiffres) | `9000000001` — format impose par le guide |

**LECON DE METHODE** : trois de ces bugs (`fld[0]`, `SecurityConfig`, format
DE33) auraient ete evites en regardant l'equivalent SWAM AVANT d'ecrire la
classe MC. Regle retenue : **avant d'ecrire une classe MC SMS, consulter son
equivalent SWAM.**

### 23.3 ALIGNEMENT MastercardSmsPackager SUR SwamPackager

Comparaison structurelle (types et longueurs volontairement NON alignes :
SWAM est ASCII, MC SMS sera EBCDIC sur le MIP reel) :

| | SwamPackager | MastercardSmsPackager (avant) | (apres) |
|---|---|---|---|
| Annotation | `@Component` | absente | `@Component` |
| Tableau | methode d'instance `buildFields()` | `static final` + bloc `static` | methode d'instance |
| Constructeur | `setFieldPackager(buildFields())` | `setFieldPackager(fld)` | `setFieldPackager(buildFields())` |
| Methodes surchargees | aucune | aucune | aucune |

SWAM n'a **pas** ete modifie.

### 23.4 IDENTIFIANTS MASTERCARD — CONFIRMES PAR LE GUIDE

| Champ | Format | Contenu | Page | Messages |
|---|---|---|---|---|
| **DE33** | `n..10` LLVAR, **min 10 / max 10** | Processor ID, format impose `9000xxxxxx` | 385 | 0800, 0810, 0200 |
| **DE32** | `n..9` LLVAR, **min 9 / max 9** | Acquiring Institution ID | 383 | 0200, 04xx |
| **ICA** | 6 chiffres | Interbank Card Association, identifiant du membre | — | hors ISO (reporting, settlement) |
| **DE96** | b-8 (8 octets) | Message Security Code, "password" du sign-on | 792 | 0800 DE70=061 |

Citations du guide :
- DE33 : *"The processor ID is a ten-digit number of the format: 9000xxxxxx,
  where the Single Message System-assigned processor ID will be up to the last
  six digits xxxxxx."* Exemples reels cites p.29448 : `9000000084`, `9000000752`.
- ICA : *"The unique six digit number assigned by Mastercard that identifies a
  customer or processing endpoint."* Decline en ACQUIRING ICA, ISSUING ICA,
  SETTLEMENT ICA.

**Valeurs actuelles = BOUCHONS DEV.** Le processor ID reel, l'ICA et le DE96
sont a obtenir de Mastercard avant tout test contre un MIP.

**Notre role sur MC SMS : ACQUEREUR uniquement.** Le module
`sg-mc-sms-issuer` simule Mastercard et n'a aucune identite propre : dans le
0810 il recopie le DE33 recu (champ ME).

### 23.5 [DECISION] MODELE DE DONNEES BANQUE

Retenu : **table mere `bank` + une table fille par reseau**.

    bank                  identite de l'etablissement (nom, BIC, pays)
      |
      +-- bank_mc_sms     processor_id, acquiring_inst_id, ica,
      |                   settlement_ica, message_security_code
      +-- bank_swam       (a creer lors de la migration SWAM)
      +-- bank_dmas       (a creer lors de la migration DMAS)

Ecarte : une table unique avec toutes les colonnes par reseau (colonnes vides,
`ALTER TABLE` sur la table centrale a chaque nouveau reseau).

Chaque table fille porte les identifiants **aux formats de son reseau**, avec
les contraintes CHECK correspondantes (ex. `processor_id ~ '^9000[0-9]{6}$'`).

SQL prepare : `V2__create_bank_tables.sql` — **NON EXECUTE**, en attente.

### 23.6 [DECISION] PLATEFORME MULTI-BANQUES

**La plateforme doit gerer plusieurs banques. La banque est choisie a chaque
appel REST, pas fixee au demarrage du module.**

    POST /api/admin/mc/network/signon?bank=BANQUE_A

#### Portee du chantier (NON COMMENCE)

**Schema** — ajouter `bank_id` sur toutes les tables acquereur et issuer,
tous reseaux confondus :

    mc_sms_kek, mc_sms_acq_keys, mc_sms_acq_transactions,
    mc_sms_iss_keys, mc_sms_iss_transactions, mc_sms_cards
    + equivalents SWAM et DMAS

Les cles uniques sont a revoir : une cle ACTIVE **par banque**, pas globale.
Exemple : `swam_acq_keys` a aujourd'hui une unicite sur
`(member_group_id, key_type, status)` — il faudra y integrer la banque.

**Code** — `McSmsJposClient` :
- champ `channel` unique -> map `bankCode -> channel`
- un thread receiver **par banque** (une session par processor ID)
- corrélation `bankCode:STAN` et non plus `STAN` seul
  (deux banques peuvent emettre le meme STAN sur deux sockets differentes)
- le DE33 vient de `bank_mc_sms.processor_id` de la banque choisie,
  plus du fichier properties

Meme travail a terme sur `SwamJposClient`.

**API** :
- banque en parametre de chaque endpoint reseau
- `GET /api/admin/mc/banks`    — banques disponibles
- `GET /api/admin/mc/sessions` — etat des liaisons ouvertes

**Ordre d'execution** : Mastercard d'abord (pas encore en production), SWAM
ensuite et **prudemment** (il tourne contre le switch reel).

**Point ouvert** : en local, toutes les banques pointeront vers le meme issuer
simule (localhost:8098), qui ne les distinguera pas. A decider : faire en
sorte que le simulateur differencie les sessions par leur DE33.

### 23.7 ETAT DES MODULES MC SMS

| Module | REST | ISO | Source des ports |
|---|---|---|---|
| `sg-mc-sms-acquirer` | 8095 | — (client pur) | `networks.acquirer_rest_port` |
| `sg-mc-sms-issuer` | 8097 | 8098 | `networks.issuer_rest_port` / `issuer_iso_port` |

`acquirer_jpos_port` (8096) est **inutilise** pour MC SMS : l'acquereur ouvre
une socket sortante et n'ecoute sur rien. Le MIP pousse ses messages spontanes
sur cette meme socket (gere par le thread receiver).

### 23.8 RESTE A FAIRE

1. Chantier multi-banques (section 23.6)
2. Tables `bank` / `bank_mc_sms` — SQL pret, non execute
3. Obtenir de Mastercard : processor ID reel, ICA, DE96
4. Framing MIP reel — 2 octets big-endian **suppose**, spec dans le
   *Secured Data Communications Guide* (non disponible)
5. Encodage EBCDIC (guide p.163) — packager actuel en ASCII pour le dev
6. PEK exchange DE70=161 : parser DE48 subelement 11, importer la PEK,
   persister (TODO dans `handleMipPush`)
7. Transaction 0200/0210
8. Basculer SWAM sur les ports pilotes par la base (config deja modifiee,
   a retester prudemment — jPOS risque au restart)
9. [SWAM] Transaction 1100/1200 contre le switch reel : necessite un PAN de
   test et la config des comptes cote switch
10. [SWAM] DE7 en UTC dans `SwamJposClient` (decalage +1h)

---

## 24. SESSION 20 — ECHANGE DE CLES MASTERCARD SMS (2026-07-20)

Cette section documente **les deux mecanismes** d'echange de cles du Single
Message System. Le mecanisme **162 est implemente et valide** ; le **163
(TR-31) ne l'est pas**, mais tout ce qu'il faut pour l'implementer est
consigne ici — il ne sera pas necessaire de relire les specifications.

Sources : guide Mastercard SMS du 2 juin 2026 + **traces reelles du
simulateur officiel** (AcquirerSwitchSimulator, format Mastercard Credit
26Q3). En cas de divergence, la trace fait foi.

---

### 24.1 LES SEPT CODES DE70 DE GESTION DE CLES

Table 726 du guide (ligne 59962 du fichier texte) :

| Code | Description |
|---|---|
| 161 | Encryption key exchange — **livraison** de la cle |
| 162 | Solicitation for encryption key exchange — **demande**, mecanisme DE48 |
| 163 | Solicitation for encryption key exchange: **TR-31 keyblock** — demande, mecanisme DE110 |
| 164 | Encryption key exchange confirmation of success |
| 165 | Encryption key exchange advice of failure |
| 166 | Load Comm Key |
| 167 | Load previous Comm Key |

Le code 161 sert de livraison **dans les deux mecanismes** ; c'est le code de
sollicitation (162 ou 163) qui determine le format de transport.

---

### 24.2 MECANISME 162 — DE48 SUBELEMENT 11  [IMPLEMENTE ET VALIDE]

#### Flux

    Membre -> MIP : 0800 DE70=162   sollicitation
    MIP -> Membre : 0810 DE70=162   DE39=00, SANS cle
    MIP -> Membre : 0800 DE70=161   la cle, DE48 SE11        [SPONTANE]
    Membre -> MIP : 0810 DE70=161   accuse (00 ou 96)
    MIP -> Membre : 0820 DE70=161   acquittement : cle utilisable

**Le flux est ASYNCHRONE**, contrairement a SWAM ou la cle arrive dans la
reponse au 1804 (un seul aller-retour). Ici la demande n'obtient qu'un
accuse ; la cle arrive plus tard sur le thread receiver. D'ou la machine a
etats : PENDING -> RECEIVED -> ACTIVE.

Le guide (ligne 36090) : *"Upon receipt of a Network Management Advice/0820
message, processors may begin to use the new working key delivered in the
Network Management Request/0800 message."* Le 0820 est donc l'autorisation
d'usage, pas une simple politesse.

**Observation de la trace** : le MIP envoie le 0820 **meme si le membre a
repondu DE39=96**. Notre simulateur reproduit ce comportement.

#### Structure du DE48

Suite de subelements `ID(2) + Longueur(2) + Valeur` — attention, la longueur
est sur **2 positions**, alors que le DE48 SWAM utilise Tag(3)+Longueur(3).

Subelement 11 (Key Exchange Data Block), deux formats selon la longueur :

| SF | Contenu | an-54 (double) | an-70 (triple) |
|---|---|---|---|
| 1 | Key Class ID | 2 — `PK` (PIN Key) | 2 |
| 2 | Key Index Number | 2 — `00` | 2 |
| 3 | Key Cycle Number | 2 — `00`..`99` | 2 |
| 4 | Cle chiffree sous ZMK | 32 hex | 48 hex |
| 5 | Key Check Value | 16 (4 hex + 12 espaces) | 16 |

Variante **an-38** observee dans le 0820 : SF1-SF3 renseignes, SF4 et SF5 a
blanc (16 espaces chacun).

Exemple reel (trace Mastercard) :

    1154PK0000E02B0E8BD4644E6341182D71F4F3F5B543A1____________
    ^^ ^^ ^^ ^^ ^^      SF4 cle chiffree (32)        SF5 KCV (16)
    |  |  |  |  +-- SF3 cycle
    |  |  |  +----- SF2 index
    |  |  +-------- SF1 classe = PK
    |  +----------- longueur 54
    +-------------- subelement 11

Notre emission, structurellement identique :

    1154PK0000708F1964DBAE610781C6211436254A696E19____________

#### Cryptographie — VERIFIEE CONTRE LA TRACE

    cle chiffree = 3DES-ECB(cle claire) sous ZMK
    KCV          = 3DES-ECB(8 octets nuls) avec la cle claire

Verification faite sur les valeurs de la trace, 4 correspondances sur 4 :

| Element | Valeur | Resultat |
|---|---|---|
| ZMK | `13AED5DA1F32347523C708C11F2608FD` | KCV `2D617C` — **meme ZMK que SWAM** |
| Cle claire | `BC4AEA2F5BB3FD1504624F8623835D5B` | — |
| Chiffree | `E02B0E8BD4644E6341182D71F4F3F5B5` | **MATCH** |
| KCV | `43A1866D253E9365` | **MATCH**, tronque a `43A1` |

**PIEGE** : la trace du simulateur annonce *"Key Check Value Encryption
Algorithm: DES-CBC"*, mais c'est bien du **3DES avec la cle double
longueur**. Le DES simple sur la moitie gauche donne autre chose.

**PIEGE** : Mastercard tronque le KCV a **4 caracteres** dans SF5, la ou SWAM
en conserve 6. La comparaison doit donc porter sur les 4 premiers caracteres.

#### Classes implementees

**sg-common**
- `McSmsDe48.java` — parseur/generateur des subelements. `KeyExchangeBlock`
  decode SF1-SF5, detecte double/triple/acquittement par la longueur.
  Methodes `putKeyExchange()` et `putKeyExchangeAck()`.

**sg-mc-sms-acquirer** (le membre)
- `McSmsKeyExchange.java` — `solicitKeyExchange()` envoie le 162 ;
  `handleKeyDelivery()` importe la cle sous LMK, verifie le KCV sur 4
  caracteres, persiste en statut RECEIVED ; `handleKeyAcknowledgement()`
  passe a ACTIVE et retire l'ancienne cle.
- `McSmsJposClient.java` — route les 0800/161 et les 0820 vers le service.
  **`@Lazy` obligatoire** sur l'injection de `McSmsKeyExchange` : sans lui,
  Spring detecte un cycle (le service prend le client au constructeur).
- `McKeyExchangeController.java` — bootstrap ZMK, sollicitation, consultation.

**sg-mc-sms-issuer** (simulateur Mastercard)
- `McSmsIssKeyExchange.java` — genere une PEK double longueur (parite impaire),
  la chiffre sous ZMK, calcule le KCV, envoie le 0800/161 puis le 0820.
  Crypto en JCE standard, pas de HSM : c'est un outil de test.
- `McSmsJposServer.java` — sur 0800/162 repond 0810 puis declenche la
  livraison asynchrone. Refuse explicitement le 163 avec DE39=96.
- `McIssKeyController.java` — expose la derniere cle livree, pour comparaison.

#### Endpoints

    POST /api/admin/mc/keys/bootstrap-zmk?zmk=<32 ou 48 hex>
    POST /api/admin/mc/keys/solicit
    GET  /api/admin/mc/keys/current
    GET  /api/admin/mc/sim/last-key          (issuer, port 8097)

#### Resultat du test local

    Simulateur : clair     A45449BF80BF1AF72F7AEAF7B03EEFF2
                 chiffree  708F1964DBAE610781C6211436254A69
                 KCV       6E197BAF48A47D84  -> envoie "6E19"

    Membre     : KCV importe 6E197B  (calcule par le HSM apres import)
                 statut ACTIVE, 16 octets

Le HSM a dechiffre la cle et recalcule un KCV identique : les deux cotes
detiennent bien la meme cle. Les 5 etapes se sont enchainees sans erreur.

---

### 24.3 MECANISME 163 — TR-31 KEYBLOCK VIA DE110  [NON IMPLEMENTE]

Tout ce qui suit vient de traces reelles. Suffisant pour implementer sans
relire les specifications.

#### Flux

    Membre -> MIP : 0800 DE70=163   solicitation TR-31 or AES
    MIP -> Membre : 0810 DE70=163   DE39=00
    MIP -> Membre : 0800 DE70=161   keyblock, DE110           [SPONTANE]
    Membre -> MIP : 0810 DE70=161   accuse
    MIP -> Membre : 0820 DE70=161   acquittement

Meme sequence que le 162 ; seul le transport change.

#### Structure du DE110

**ATTENTION — LE GUIDE ET LA PRATIQUE DIVERGENT.**

Le guide (p.921, ligne 70567) decrit un DE110 « Encryption Data » en
**BER-TLV binaire** : Dataset ID (1 octet) + Dataset length (2 octets) +
tags codes ISO 8825. Dataset 04 = Key Exchange, tags 80 (Control), 81
(Key-set Identifier), 83 (Algorithm), 86 (Key Index), 87 (Encrypted Data),
88 (Key Checksum Value).

**La trace montre autre chose** : des subelements ASCII `ID(2)+len(3)+valeur`,
etiquetes « Additional Data-2 » (l'autre usage du DE110, p.890) :

    09 080 B0080P0TB00E000022D08F4891AD6042734A1E432242CE80D6B928DF5A496751D63E5EE08A5D7D90
    10 006 B3F2DE

    total 2+3+80 + 2+3+6 = 96, coherent avec le prefixe LLLVAR "096"

| Subelement | Contenu | Longueur |
|---|---|---|
| 09 | ANSI X9 TR-31 Key Block | 80 |
| 10 | Key Check Value | 6 |

Noter que la longueur est ici sur **3 positions**, contre 2 dans le DE48.

**Recommandation** : implementer le format de la trace (subelements ASCII).
Le BER-TLV du guide est probablement la cible d'une migration future — le
guide dit lui-meme (ligne 68439) *"when ready to migrate to DE 110
Encryption data must first..."*.

#### Le keyblock TR-31 (ANSI X9.143)

    B0080P0TB00E0000 22D08F4891AD6042734A1E432242CE80D6B928DF5A496751D63E5EE08A5D7D90
    ^^^^^^^^^^^^^^^^ header 16 caracteres        payload 64 caracteres

| Position | Valeur | Signification |
|---|---|---|
| 0 | `B` | Version B — TDES key derivation binding |
| 1-4 | `0080` | Longueur totale du keyblock |
| 5-6 | `P0` | Key Usage — **PIN Encryption Key** |
| 7 | `T` | Algorithm — TDES |
| 8 | `B` | Mode of Use — chiffrement et dechiffrement |
| 9-10 | `00` | Key Version Number |
| 11 | `E` | Exportability — exportable |
| 12-13 | `00` | Nombre de blocs optionnels |
| 14-15 | `00` | Reserve |
| 16-79 | 64 hex | 48 = cle chiffree (24 octets) + 16 = MAC (8 octets) |

Le KCV est dans le subelement 10 : `B3F2DE`, soit **3 octets** — contre
4 caracteres (2 octets) dans le DE48. Verifie contre la trace : la cle claire
`F4EF91DF862564EF38B952DA312910D9` donne le KCV `B3F2DECD89772341`, tronque
a `B3F2DE`.

#### Ce qui reste a determiner pour l'implementation

1. **La KBPK** (Key Block Protection Key) qui protege le keyblock. La trace ne
   la montre pas. Le guide n'en parle pas — c'est probablement dans le
   *Security Guide* ou le *Secured Data Communications Guide*, non disponibles.
   Sera injectee hors bande comme la ZMK.
2. **Le deverrouillage du keyblock** : derivation des cles de chiffrement et
   de MAC depuis la KBPK selon X9.143, verification du MAC, dechiffrement.
   **Le HSM jPOS ne sait probablement pas le faire nativement** — c'est le
   point technique le plus lourd de ce chantier.
3. **Le packager** : le DE110 est declare `IFA_LLLCHAR(999)` par la boucle
   generique DE105-127 de `MastercardSmsPackager`. A verifier si le format
   ASCII de la trace passe tel quel.

---

### 24.4 ECARTS ENTRE LE GUIDE ET LA PRATIQUE

Trois divergences constatees. **La trace du simulateur officiel prime.**

| Point | Guide | Trace reelle |
|---|---|---|
| **DE33** | *"ten-digit number of the format 9000xxxxxx"*, min 10 / max 10 (p.385) | `002202` et `022905` — **6 chiffres, un ICA** |
| **DE110** | BER-TLV binaire, Dataset 04, tags 80-88 (p.921) | subelements ASCII `09`/`10`, « Additional Data-2 » |
| **KCV DE48** | 4 caracteres hex + espaces (p.36330) | conforme : `43A1` + 12 espaces |

Le DE33 est le plus genant : notre bouchon `9000000001` respecte le guide
mais pas la pratique. **A verifier avec Mastercard** avant tout test reel.
Notre packager `IFA_LLNUM(10)` accepte les deux longueurs en LLVAR, donc pas
de blocage technique.

---

### 24.5 AUTRES OBSERVATIONS DES TRACES

- **EBCDIC confirme sur le fil.** Toutes les trames du simulateur sont en
  EBCDIC (`F0F8F0F0` = "0800"). Notre packager ASCII ne passera pas contre un
  MIP reel — la variante `IFE_*` reste a faire.
- **DE2 (PAN)** porte l'ID du client : `41232` dans les traces.
- **DE63 (Network Data)** : `MCC0000J1` = Financial Network Code (`MCC`,
  Mastercard mixed BIN Immediate Debit) + Banknet Reference Number (`0000J1`),
  incremente a chaque message.
- **Mastercard echange les PEK toutes les 24 heures** (guide, ligne 7751),
  plus a la demande en cas de probleme.

---

### 24.6 CORRECTIONS DE CETTE SESSION

| Symptome | Cause | Correction |
|---|---|---|
| Cycle de dependances au demarrage | `McSmsKeyExchange` prend le client au constructeur, le client reference le service | `@Lazy` sur l'injection dans `McSmsJposClient` |
| `value too long for type character varying(6)` | KCV complet (16 car.) ecrit dans une colonne VARCHAR(6) | troncature a 6 dans le controller et le service |

SWAM evite le premier probleme parce que `SwamJposClient` fait l'import
lui-meme, sans passer par `SwamKeyExchange`.

---

### 24.7 RESTE A FAIRE SUR L'ECHANGE DE CLES

1. **TR-31 (163)** — voir 24.3. Point dur : le deverrouillage du keyblock.
2. **Codes 164 / 165** — confirmation de succes et avis d'echec ne sont pas
   emis par le membre. A ajouter apres l'import.
3. **Comm Key (166 / 167)** — jamais decrite dans ce guide. Les tags 83 et 87
   du DE110 disent que la cle est chiffree *"under the current communications
   key"*, ce qui suggere que la Comm Key remplace la ZMK dans le mecanisme
   TR-31. A clarifier avec Mastercard.
4. **Renouvellement automatique 24 h** — non gere.
5. **Multi-banques** — `member_group_id` est fixe par propriete
   (`mc.sms.member-group-id`). A revoir dans le chantier multi-banques
   (section 23.6).

---

### 24.8 BASCULE EN EBCDIC  [FAIT]

Le MIP Mastercard parle EBCDIC. Les deux modules ont ete bascules pour que
le test local reproduise fidelement le format du fil.

#### Preuve

Trame 0810 sign-on emise par notre simulateur, relevee dans le dump du
channel :

    F0F8F1F0                    "0810"
    8220000082000000            bitmap 1 : DE7, DE11, DE33, DE39
    0400000000000000            bitmap 2 : DE70
    F0F7F2F0F1F1F0F0F1F1        "0720111011"        DE7
    F0F0F0F0F0F1                "000001"            DE11
    F1F0 F9F0F0F0F0F0F0F0F0F1   "10" + "9000000001" DE33
    F0F0                        "00"                DE39
    F0F6F1                      "061"               DE70

53 octets, coherent avec le prefixe de longueur `0035`.

Comparaison avec la trace du simulateur Mastercard officiel : structure
identique. Les seules differences sont fonctionnelles — leur bitmap
`C220000082000002` porte DE2 et DE63 que nous n'emettons pas encore, et
leur DE33 fait 6 chiffres (ecart guide/pratique, cf. 24.4).

#### Classe

`sg-common/.../iso/MastercardSmsPackagerEbcdic.java`

Couvre TOUS les champs, contrairement a `McPackagerEbcdic` (DMAS) qui se
limite au sign-on. Correspondance de types :

| ASCII | EBCDIC |
|---|---|
| `IFA_NUMERIC` | `IFE_NUMERIC` |
| `IFA_LLNUM` | `IFE_LLNUM` |
| `IFA_LLCHAR` | `IFE_LLCHAR` |
| `IFA_LLLCHAR` | `IFE_LLLCHAR` |
| `IFA_LLLNUM` | `IFE_LLLCHAR` — jPOS 2.1.9 n'a pas d'`IFE_LLLNUM` |
| `IF_CHAR` | `IFE_CHAR` |
| `IFA_BINARY` | `IFB_BINARY` — vrai binaire, pas de l'hex ASCII |
| `IFB_BITMAP` | inchange |

**Les prefixes de longueur passent aussi en EBCDIC** : dans la trace,
`F0F6` = "06" pour le DE33 et `F0F9F6` = "096" pour le DE110. Les `IFE_LL*`
s'en chargent automatiquement.

**Les champs binaires ne changent pas** : bitmaps, DE52 (PIN block) et
DE96 (Message Security Code).

#### Choix du packager

En dur, comme DMAS (`new McPackagerEbcdic()` dans `DmasJposServer` et
`DmasJposClient`). `MastercardSmsPackager` (ASCII) est conserve et coexiste
— il n'est plus reference par les modules mais reste disponible.

Une variante pilotee par `networks.default_field_encoding` serait possible
(la colonne existe deja dans `NetworkRef`, inutilisee) mais n'a pas ete
retenue : le MIP reel est en EBCDIC, autant tester dans ce format.

#### Test de non-regression

Le flux complet a ete rejoue en EBCDIC : sign-on, sollicitation 162,
livraison 161, accuse, acquittement 0820. KCV `EFC3F0` identique des deux
cotes, PEK ACTIVE.

---

## 25. PROCEDURES DE TEST DES TROIS RESEAUX (2026-07-21)

Rappel commun a tous : **PostgreSQL doit tourner**. Depuis la mise en place
du `NetworkPortEnvironmentPostProcessor`, les ports REST sont lus dans la
table `networks` AVANT le demarrage de Tomcat — sans base, le module refuse
de demarrer avec le message `[SG-PORTS] Base injoignable`.

    export PGPASSWORD=postgres123
    /f/MoneyCore/pgsql/bin/pg_ctl.exe -D /f/MoneyCore/pgsql/data \
        -l /f/MoneyCore/pgsql/data/pg.log start

Piege recurrent : un JAR verrouille ou un port occupe par un process Java
resté en vie. Avant tout build ou test :

    taskkill //F //IM java.exe        # double slash obligatoire en Git Bash

---

### 25.1 AUTHENTIFICATION

DMAS protege ses endpoints par JWT ; il faut un token avant tout appel.

    Login : admin / Admin123!
    POST /auth/login   corps {"login":"admin","password":"Admin123!"}
                       reponse : champ "token"

    TOKEN=$(curl -s -X POST http://localhost:8084/auth/login \
        -H "Content-Type: application/json" \
        -d '{"login":"admin","password":"Admin123!"}' \
        | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

    curl -X POST http://localhost:8084/api/admin/dmas/network/signon \
        -H "Authorization: Bearer $TOKEN"

En Git Bash, entourer le mot de passe de quotes SIMPLES : le `!` serait
sinon interprete par l'historique du shell.

SWAM et MC SMS ont un `SecurityConfig` en `permitAll` sur les endpoints
d'administration : pas de token necessaire.

---

### 25.2 DMAS — `test-dmas.sh`

    bash /f/MoneyCore/mc-sms/test-dmas.sh
    bash /f/MoneyCore/mc-sms/test-dmas.sh <login> <password>   # si besoin

Enchaine : arret des process, controle des ports, PostgreSQL, purge des
logs, demarrage **mastercard d'abord** (c'est le serveur) puis **member**
(le client), authentification, sign-on, et affichage des statuts.

| Port | Role |
|---|---|
| 8084 | REST membre |
| 8500 | ISO reseau — **seul serveur ISO** |
| 8501 | REST reseau |
| 8600 | ex-ISO membre — **doit rester libre** depuis l'inversion |

Le script verifie trois choses :
- aucun `Address already in use` au demarrage du reseau
- le compteur `[JPOS-CLI]` (liaison permanente) contre `[DMAS-ACQ]`
  (connexion ephemere) : seul le premier doit apparaitre
- que plus personne n'ecoute sur 8600

Endpoints :

    POST /api/admin/dmas/network/{signon,signoff,echo}   membre  (8084)
    GET  /api/admin/dmas/network/status                  membre
    GET  /api/admin/dmas/jpos/status                     reseau  (8501)
    POST /api/admin/dmas/jpos/push/network?de70=&wait=&de48=
    POST /api/admin/dmas/jpos/push/advice?de70=&de48=

Les deux derniers permettent au reseau d'emettre vers le membre sur la
liaison permanente (echange de cles, advices, et les 0100/0200 a venir).

---

### 25.3 MASTERCARD SMS — `test-keyexchange.sh`

    bash /f/MoneyCore/mc-sms/test-keyexchange.sh

Enchaine : PostgreSQL, arret des process, purge des logs, demarrage
**issuer d'abord** (simulateur MIP, serveur) puis **acquereur** (membre,
client), bootstrap de la ZMK, sign-on, sollicitation d'echange de cle,
puis comparaison des KCV des deux cotes.

| Port | Role |
|---|---|
| 8095 | REST acquereur (membre) |
| 8096 | ISO acquereur — non utilise |
| 8097 | REST issuer (MIP simule) |
| 8098 | ISO issuer — **seul serveur ISO** |

Endpoints :

    POST /api/admin/mc/keys/bootstrap-zmk?zmk=<32 ou 48 hex>
    POST /api/admin/mc/keys/solicit                 0800 DE70=162
    GET  /api/admin/mc/keys/current                 etat des cles
    GET  /api/admin/mc/sim/last-key                 cle livree (8097)
    POST /api/admin/mc/network/signon

Resultat attendu : meme KCV des deux cotes et PEK en statut ACTIVE.
La ZMK de reference est `13AED5DA1F32347523C708C11F2608FD` (KCV 2D617C),
la meme que pour SWAM.

Verifier qu'une seule socket est ouverte :

    grep -c "Connecte au MIP" /f/ScenarioGenerator/logs/mc-sms-acquirer.log
    # doit valoir 1

---

### 25.4 SWAM

Les trois scenarios (local, relais, switch reel) sont decrits en detail a
la **section 21.4**, avec la topologie du relais ncat et les adresses du
switch.

| Port | Role |
|---|---|
| 8094 | REST acquereur |
| 8511 | REST issuer |
| 11127 | relais ncat vers le switch reel (10.23.33.114) |

Particularite : SWAM est le seul reseau avec un **MAC (DE64)**. En cas
d'echec, verifier d'abord la recette MAC de la section 22.1, validee sur
quatre vecteurs.

---

### 25.5 ORDRE DE DEMARRAGE — REGLE GENERALE

Depuis l'inversion DMAS, les trois reseaux suivent le meme schema :

    le SERVEUR d'abord, le CLIENT ensuite

| Reseau | Serveur (demarrer en 1er) | Client (en 2nd) |
|---|---|---|
| DMAS | sg-mc-dmas-mastercard | sg-mc-dmas-member |
| MC SMS | sg-mc-sms-issuer | sg-mc-sms-acquirer |
| SWAM | sg-swam-issuer | sg-swam-acquirer |

Le client se connecte a la demande : si le serveur n'est pas la, il ne
plante pas au demarrage mais echoue au premier sign-on avec un message
explicite.

---

### 25.6 RESULTAT DU TEST D'INVERSION DMAS (2026-07-21)

Premier test complet apres l'inversion client/serveur. Tout est valide.

    --- SIGN-ON ---
    {"label":"SIGN-ON","mti_sent":"0800","de070":"061","stan":"000001",
     "mti_received":"0810","de039":"00","success":true}

    --- STATUT MEMBRE ---
    {"role":"CLIENT","connected":true,"signed_on":true,
     "member_group_id":"TESTGRP01"}

    --- STATUT RESEAU ---
    {"role":"SERVER","session_active":true,"member_group_id":"40260"}

**La liaison est bien permanente**, prouve par les sockets restees
ouvertes apres l'echange :

    TCP  0.0.0.0:8500        0.0.0.0:0              LISTENING    76744  (reseau)
    TCP  127.0.0.1:8500      127.0.0.1:59194        ESTABLISHED  76744
    TCP  127.0.0.1:59194     127.0.0.1:8500         ESTABLISHED  95656  (membre)

Avec l'ancien mecanisme ephemere, la connexion aurait ete fermee
immediatement apres la reponse.

Compteurs : 4 lignes `[JPOS-CLI]`, **0** ligne `[DMAS-ACQ]`. Le port 8600
n'est plus ecoute par personne.

#### Corrections apportees pendant ce test

| Symptome | Cause | Correction |
|---|---|---|
| `Address already in use: bind` sur 8500 | `McDmasMastercardHandler` ouvrait son propre `ServerSocket`, en concurrence avec l'`ISOServer` | Retrait du serveur socket du handler ; les `handleXxx(req, out)` deviennent des `buildXxxResponse(req)` qui RETOURNENT la reponse |
| Logique de cle dupliquee | `processReceivedPek` du serveur doublonnait `handleKeyExchange` du handler, en moins complet | `processReceivedPek` supprime ; le serveur delegue tout au handler |
| `403` sur les endpoints | JWT : DMAS protege ses routes, contrairement a SWAM et MC SMS | Recuperer un token via `/auth/login` (admin / Admin123!) |
| `permission denied for table users` | Les GRANT n'avaient ete poses que sur les tables `mc_dmas_*` | `GRANT SELECT, UPDATE ON users` et `key_store` aux deux utilisateurs |

#### Repartition des responsabilites apres nettoyage

    McDmasMastercardServer   transport seul : accepte, lit, route, envoie
    McDmasMastercardHandler  decision metier : autorisation, reversal,
                             advices, completion, import de cles

    0800  ->  handler.buildNetworkResponse         ->  0810
    0100  ->  handler.buildAuthResponse            ->  0110
    0400  ->  handler.buildReversalResponse        ->  0410
    0120  ->  handler.buildAdviceResponse          ->  0130
    0420  ->  handler.buildReversalAdviceResponse  ->  0430

#### Points ouverts

1. **Code DE70 du sign-on** : le client emet `061`, l'ancien
   `McDmasNetworkManager` utilisait `001`. Le serveur accepte les deux en
   attendant que les specifications DMAS tranchent.
2. **`member_group_id` incoherent** : le membre annonce `TESTGRP01`, le
   reseau enregistre `40260` — c'est le DE2, qui porte
   `dmas.jpos.group-signon-id` et non le member group. A verifier dans les
   specifications : que doit contenir le DE2 d'un 0800 ?
3. **`SessionOrchestrator`** utilise encore `McDmasNetworkManager`
   (`@Deprecated`, connexion ephemere). A basculer, puis supprimer le
   manager et `McDmasNetworkUtil.sendAndReceive`.
4. **Sens de l'echange de cles** : le membre pousse toujours la PEK vers le
   reseau. Chez MC SMS et SWAM, c'est le reseau qui distribue. A trancher
   avec les specifications DMAS.

---

## 26. DMAS — ECHANGE DE CLES (2026-07-21)

### 26.1 TROIS MECANISMES, UNE SEULE DESTINATION

Quel que soit le chemin, la cle atterrit dans la meme table :

    membre  ->  mc_dmas_member_keys
    reseau  ->  mc_dmas_mastercard_keys

Une transaction 0100 dechiffrera donc le PIN avec la meme cle, peu importe
comment elle est arrivee.

| # | Mecanisme | Endpoint / declencheur | Statut initial |
|---|---|---|---|
| 1 | Injection manuelle | `POST /keys/inject` | ACTIVE |
| 2 | Sollicitation 162 | `POST /keys/solicit` | RECEIVED puis ACTIVE |
| 3 | Push par le membre | `POST /keyexchange/pek` | ACTIVE |

Le mecanisme 3 est l'implementation historique : le membre genere la cle et
la pousse au reseau. Conserve en attendant que les specifications DMAS
tranchent, car il est l'inverse de ce que montrent les traces.

---

### 26.2 PREREQUIS — LA KEK

La KEK (equivalent de la ZMK) est injectee **hors bande** des deux cotes.
Elle n'est jamais echangee sur le reseau.

    POST /api/admin/dmas/kek/bootstrap
    corps {"memberGroupId":"TESTGRP01","kekClear":"13AED5DA1F32347523C708C11F2608FD"}

Valeur de reference : `13AED5DA1F32347523C708C11F2608FD`, KCV `2D617C` —
la meme que pour SWAM et MC SMS.

Sans KEK, l'injection comme la sollicitation echouent : la PEK est toujours
transportee chiffree sous la KEK.

---

### 26.3 INJECTION MANUELLE

    POST /api/admin/dmas/keys/inject?clear=<32|48 hex>
    POST /api/admin/dmas/keys/inject?underZmk=<32|48 hex>&kcv=<hex>
    GET  /api/admin/dmas/keys/current

`clear` : la cle en clair, chiffree sous la KEK avant import.
`underZmk` : la cle deja chiffree, cas reel. Si `kcv` est fourni, il est
verifie et la cle est rejetee en cas de discordance.

Dans les deux cas la cle est importee sous le LMK local par le HSM, puis
persistee avec ses deux formes et son KCV. L'ancienne cle du meme type
passe en RETIRED.

Test : `bash /f/MoneyCore/mc-sms/test-key-injection.sh`

Resultat obtenu — meme PEK injectee des deux cotes :

    cote   | key_type | kcv    | status | key_length
    membre | PEK      | 43A186 | ACTIVE | 16
    reseau | PEK      | 43A186 | ACTIVE | 16

Le KCV `43A186` est exactement celui calcule a partir de la trace du
simulateur (`43A1866D253E9365` tronque a 6) : **la cryptographie DMAS est
identique a celle decodee dans les traces**.

---

### 26.4 MECANISME 162 — SOLLICITATION  [IMPLEMENTE ET VALIDE]

#### Flux

    membre -> reseau : 0800 DE70=162   sollicitation
    reseau -> membre : 0810 DE70=162   accuse, SANS cle
    reseau -> membre : 0800 DE70=161   la cle, DE48 SE11   [SPONTANE]
    membre -> reseau : 0810 DE70=161   accuse
    reseau -> membre : 0820 DE70=161   acquittement -> cle utilisable

Flux **ASYNCHRONE** : la sollicitation n'obtient qu'un accuse ; la cle
arrive plus tard sur le thread listener. D'ou la machine a etats
PENDING -> RECEIVED -> ACTIVE. Le 0820 est ce qui autorise l'usage.

Le reseau emet le 0820 **meme si le membre a rejete la cle** — comportement
observe dans la trace du simulateur officiel.

#### Classes

**Cote membre** (`sg-mc-dmas-member`)
- `McDmasKeyExchange.solicitPek()` — envoie le 162
- `McDmasKeyExchange.handleKeyDelivery()` — importe, verifie le KCV,
  persiste en RECEIVED ; retourne le DE39 du 0810
- `McDmasKeyExchange.handleKeyAcknowledgement()` — passe a ACTIVE
- `McDmasMemberClient` — route les 0800/161 et 0820/161 vers le service.
  **`@Lazy` obligatoire** sur l'injection : le service prend le client au
  constructeur, cycle Spring sinon.
- `McDmasKeySolicitController` — `POST /api/admin/dmas/keys/solicit`

**Cote reseau** (`sg-mc-dmas-mastercard`)
- `McDmasKeyDelivery` — genere la PEK, la chiffre sous KEK, pousse le
  0800/161, attend le 0810, envoie le 0820. Livraison dans un thread
  separe pour ne pas bloquer le listener.
- `McDmasMastercardServer` — declenche `deliverAsync()` apres avoir emis
  le 0810 d'une sollicitation acceptee. **`@Lazy`** la aussi.
- `McDmasMastercardHandler` — libelle les codes 162, 164, 165

#### Test

    bash /f/MoneyCore/mc-sms/test-key-162.sh

Resultat obtenu, les 5 etapes en 800 ms :

    11:13:10.061  membre -> 0800/162  STAN=609773
    11:13:10.063  reseau -> 0810/162  DE39=00
    11:13:10.378  reseau    PEK generee, KCV=8CB3C7
    11:13:10.379  reseau -> 0800/161  STAN=237574
    11:13:10.415  membre    import OK, KCV recu = calcule
    11:13:10.436  membre -> 0810/161  DE39=00
    11:13:10.865  reseau -> 0820/161
    11:13:10.878  membre    PEK ACTIVE

    cote   | key_type | kcv    | status | key_length
    membre | PEK      | 8CB3C7 | ACTIVE | 16
    reseau | PEK      | 8CB3C7 | ACTIVE | 16

---

### 26.5 CODES DE70 DE GESTION DE CLES

| Code | Description | Implemente |
|---|---|---|
| 161 | Livraison de la cle (DE48 SE11) | oui, dans les deux sens |
| 162 | Sollicitation d'echange | oui |
| 163 | Sollicitation TR-31 (DE110) | non — voir section 24.3 |
| 164 | Confirmation de succes | emis par le mecanisme 3 |
| 165 | Avis d'echec | emis par le mecanisme 3 |

---

### 26.6 RECAPITULATIF DES SCRIPTS DE TEST DMAS

| Script | Objet |
|---|---|
| `test-dmas.sh` | sign-on, liaison permanente, controle des ports |
| `test-key-injection.sh` | injection manuelle de la KEK et de la PEK |
| `test-key-162.sh` | echange de cle complet par sollicitation |

Tous prennent `<login> <password>` en parametres optionnels
(defaut : `admin` / `Admin123!`) et redemarrent les modules eux-memes,
**reseau d'abord, membre ensuite**.

---

### 26.7 POINTS OUVERTS

1. **Sens de l'echange** : les mecanismes 2 (reseau distribue) et 3 (membre
   pousse) coexistent. Les traces montrent le reseau distribuant ; a
   confirmer dans les specifications DMAS avant de supprimer le 3.
2. **Code DE70 du sign-on** : `061` (client actuel) ou `001` (ancien
   `McDmasNetworkManager`) ? Le serveur accepte les deux.
3. **DE2 d'un 0800** : le membre annonce `TESTGRP01`, le reseau enregistre
   `40260` — c'est `dmas.jpos.group-signon-id`. Que doit contenir le DE2 ?
4. **`SessionOrchestrator`** utilise encore le mecanisme ephemere
   (`McDmasNetworkManager`, `@Deprecated`).
5. **TR-31 (163)** non implemente — specification complete en section 24.3.

---

### 26.8 FLUX SYSTEM GENERATED + PIEGE DES DEUX IDENTIFIANTS (2026-07-21)

#### Ce que disent les specifications (p.154 et p.157)

Les deux flux decrits font **distribuer la cle par le RESEAU** :

    customer generated  le client SOLLICITE (0800/162), le reseau livre
    system generated    le reseau livre spontanement, toutes les 24 h

**Piege de vocabulaire** : "customer generated" qualifie la DEMANDE, pas la
cle. C'est ce qui avait fait croire que le membre devait generer la PEK.

Consequence : `exchangePek` (le membre pousse) est marque `@Deprecated`
NON CONFORME. Conserve car il exerce le chemin de reception du handler
(`importKeyFromDe48`), mais a ne pas utiliser contre un vrai MIP.

Autres precisions tirees des specifications :
- **DE33** identifie le demandeur dans le 0800 de sollicitation
- **DE2** porte l'ID du client dans le 0800 de livraison
- la cle voyage "in DE 48 **or DE 110**" — les deux transports
- le 0820 peut aussi "advise of the failure", pas seulement le succes

#### Renouvellement automatique

`McDmasKeyDelivery.scheduledRenewal()` — `@Scheduled`, desactive par
defaut. Proprietes dans l'`application.yml` du module mastercard :

    dmas:
      pek:
        auto-renewal: false
        renewal-interval-ms: 86400000        # 24 h
        renewal-initial-delay-ms: 86400000

Declenchement manuel pour les tests :

    POST /api/admin/dmas/jpos/push/pek

#### PIEGE : DEUX IDENTIFIANTS AUX NOMS PROCHES

C'est la cause de l'echec "KEK absente pour 40260" rencontre en test.

| Identifiant | Valeur | Role |
|---|---|---|
| `member_group_id` | `TESTGRP01` | **cle de recherche EN BASE** (KEK, PEK) |
| Group Sign-on ID | `40260` | identifiant du membre **SUR LE RESEAU**, porte par le DE2 |

**REGLE : chaque cote cherche ses cles avec SON identifiant local.**
Ne jamais utiliser le DE2 d'un message recu comme cle de recherche.

    cote membre  ->  dmas.member-group-id  (defaut TESTGRP01)
    cote reseau  ->  dmas.member-group     (defaut TESTGRP01)
    DE2 emis     ->  server.getActiveMemberGroupId() / group-signon-id

Deux erreurs successives ont ete commises avant d'arriver la :
1. `deliverSpontaneous` prenait `getActiveMemberGroupId()` (= 40260) pour
   chercher la KEK cote reseau
2. puis, une fois le DE2 corrige a 40260, `handleKeyDelivery` cherchait la
   KEK cote membre avec ce meme DE2

#### Resultat du test des trois mecanismes

`bash /f/MoneyCore/mc-sms/test-key-162.sh` purge la PEK avant chaque
mecanisme et verifie qu'il y a bien 2 cles ACTIVE pour 1 seul KCV distinct.

    1. sollicitation 162   KCV=663E3E   OK
    2. system generated    KCV=593002   OK
    3. injection manuelle  KCV=43A186   OK

Les KCV 1 et 2 changent a chaque execution (cle generee aleatoirement par
le reseau) ; le 3 vaut toujours 43A186, la cle injectee etant fixe.

#### Note de configuration

L'`application.yml` du module mastercard declare `dmas.member-group-id`
alors que le code lit `dmas.member-group` (sans `-id`). Cela fonctionne
par le defaut `TESTGRP01`, mais la propriete n'est pas reellement lue.
A uniformiser.

---

## 27. DMAS MULTI-BANQUE (2026-07-21)

DMAS abandonne la table `networks` au profit de `mc_dmas_interface`, et
peut desormais servir plusieurs banques. SWAM et MC SMS ne sont pas
touches et continuent d'utiliser `networks`.

### 27.1 UN SEUL PARAMETRE AU DEMARRAGE

    java -jar sg-mc-dmas-member.jar --sg.interface=DMAS_BANK_A

Le module lit ensuite TOUT en base : identifiants ISO, ports, cible.
Plus aucune valeur en dur — ni `FORWARDING_ID`, ni `TESTGRP01`, ni
`40260`. Le parametre est OBLIGATOIRE : sans lui, le module refuse de
demarrer plutot que de tourner avec des defauts qui masqueraient une
erreur de configuration.

Une liste est acceptee, et le module pilote alors N banques dans une
seule JVM :

    --sg.interface=DMAS_BANK_A,DMAS_BANK_B

Le port REST est celui de la PREMIERE interface ; les appels designent
ensuite la banque par `?bank=022905`, optionnel en mono-banque.

### 27.2 LA TABLE

`mc_dmas_interface`, une ligne par banque et par Mastercard :

| Colonne | Role |
|---|---|
| `id_interface` | passe au demarrage |
| `bank_code` | six chiffres, format ICA, unique |
| `acq_ica_de32` / `acq_arid` | role acquereur |
| `iss_ica_de100` / `iss_arid` | role emetteur |
| `fwd_id_de33` | DE33, six chiffres chez DMAS |
| `group_signon_de2` | DE2 des 0800 |
| `member_group_id` | cle d'indexation des cles EN BASE |
| `host` / `rest_port` / `iso_port` | ecoute |
| `target_host` / `target_port` | ou se connecter |
| `status` | OFF, SIGNON, PEK_EXCHANGED, READY, SIGNOFF |

`target_host` / `target_port` rendent la cible indifferente : un module
de ce projet ou un vrai MIP, selon la valeur.

    UPDATE mc_dmas_interface
    SET target_host = '10.23.33.114', target_port = 11127
    WHERE id_interface = 'DMAS_BANK_A';

Jeu de test livre :

| id_interface | bank_code | REST | ISO | cible |
|---|---|---|---|---|
| DMAS_BANK_A | 022905 | 8084 | — | localhost:8500 |
| DMAS_BANK_B | 022906 | 8085 | — | localhost:8503 |
| DMAS_MASTERCARD_1 | 002202 | 8501 | 8500 | — |
| DMAS_MASTERCARD_2 | 002203 | 8502 | 8503 | — |

### 27.3 CLOISONNEMENT DES DONNEES

`bank_code` a ete ajoute aux cinq tables DMAS (`kek`, `member_keys`,
`mastercard_keys`, `cards`, `transactions`), avec des index prefixes par
la banque. Sans cela, deux banques partageant un `member_group_id`
ecraseraient mutuellement leurs cles. Les donnees existantes ont ete
rattachees a la banque A.

### 27.4 N LIAISONS DANS UNE JVM

**Cote membre** — `McDmasMemberClient` devient un gestionnaire. Chaque
banque a sa `Connection` : channel, thread d'ecoute, map `pending`,
etat `signedOn`. Les liaisons sont independantes ; si l'une tombe,
l'autre continue.

**Cote Mastercard** — `McDmasMastercardServer` ouvre un `ISOServer` par
interface, chacun conservant les sessions des membres qui s'y
connectent, indexees par DE2.

Toutes les methodes existent en deux formes, avec et sans banque. Sans
banque, c'est l'interface principale qui repond — les classes
appelantes existantes n'ont pas eu a changer.

### 27.5 IDENTIFICATION DU MEMBRE PAR LE DE2

C'est le mecanisme central du multi-banque cote reseau :

    sign-on DE2=40260
       -> lookupByGroupSignon("40260")
       -> banque 022905
       -> member_group_id TESTGRP01
       -> les cles de CETTE banque

`McDmasMastercardHandler` resout la banque a chaque message via
`memberGroupIdForDe2()`. Un meme serveur peut donc accueillir plusieurs
membres sans melanger leurs cles.

### 27.6 STATUT DES INTERFACES

    OFF -> SIGNON -> PEK_EXCHANGED -> READY -> SIGNOFF -> OFF

`READY` suit automatiquement `PEK_EXCHANGED`. `OFF` est pose au
demarrage et au `@PreDestroy`. Le Mastercard peut aussi poser le statut
d'un membre via `markStatus()`, puisqu'il constate en premier la chute
d'une socket.

### 27.7 RESULTAT DU TEST

`bash scripts/test/test-dmas-multibank.sh` — deux JVM, deux couples :

    JVM 1  --sg.interface=DMAS_MASTERCARD_1,DMAS_MASTERCARD_2
    JVM 2  --sg.interface=DMAS_BANK_A,DMAS_BANK_B

    [JPOS-SRV:002202] ISOServer demarre sur :8500
    [JPOS-SRV:002203] ISOServer demarre sur :8503
    [JPOS-SRV] 2 serveurs ISO demarres
    2 interfaces, port REST 8084 (la premiere)

    membres connectes : {"002202":["40260"], "002203":["40261"]}

    cote   | member_group_id | kcv    | status
    membre | TESTGRP01       | 4FAA98 | ACTIVE
    reseau | TESTGRP01       | 4FAA98 | ACTIVE
    membre | TESTGRP02       | 275866 | ACTIVE
    reseau | TESTGRP02       | 275866 | ACTIVE

    VERDICT : 4 cles ACTIVE, 2 KCV distincts

Chaque banque a SA cle, partagee avec SON Mastercard. Quatre sockets
`ESTABLISHED`, deux par couple, portees par deux processus seulement.

### 27.8 CE QUI RESTE

1. Une transaction 0100 ne connait pas encore la banque : `bank_code`
   est en base mais les requetes ne le filtrent pas partout.
2. Le `NetworkPortEnvironmentPostProcessor` ne lit que le `rest_port` de
   la premiere interface ; suffisant, mais a garder en tete.
3. Le renouvellement automatique 24 h n'a pas ete teste en conditions
   reelles.
4. `SessionOrchestrator` utilise encore `McDmasNetworkManager`
   (`@Deprecated`, connexion ephemere).
