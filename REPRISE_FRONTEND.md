# Reprise du chantier frontend global

## État au 2 août 2026

Le cadrage final est `documents/design/frontend/CADRAGE_FRONTEND_FINAL_V1.md`.
Le manuel opérateur est `tests/frontend/MANUEL_TEST_FRONTEND_GLOBAL.md`.

## Travail terminé

- audit Angular, navigation dynamique, RBAC, Utilisateurs et SQL Maker/Checker ;
- menu commun permanent et menus métier hiérarchiques ;
- modules ServerPOS, Acquisition, Issuing, SWAM Membre, DMAS Membre, DMCS
  Membre et Simulateurs ;
- sites marchands national/international exclusivement sous Simulateurs ;
- registre `componentKey` fail-closed et garde de route dynamique ;
- routes Administration Utilisateurs/Rôles ;
- API backend de consultation des rôles et permissions ;
- écrans Mes opérations/Mes validations, fermés si les API workflow manquent ;
- catalogue SQL idempotent `sql/19_frontend_global_catalog.sql` ;
- traductions FR/EN/ES ;
- retrait des fixtures PAN/PIN/clé codées en dur dans le frontend ;
- Playwright contractuel et scripts Git Bash.

## Validation exacte

- `npm.cmd run build` : succès ;
- `npx.cmd playwright test frontend-global-shell.spec.ts` sur le serveur isolé :
  3 tests, 3 réussis, code retour 0 ;
- Maven embarqué, `-pl sg-generator-orchestrator -am test` : BUILD SUCCESS ;
- `sg-common` : 69 tests, 0 échec ;
- orchestrateur : 44 sources compilées, aucun test propre au module.

## Premier travail non terminé

1. appliquer les migrations SQL 18 puis 19 sur une base de recette ;
2. démarrer l'orchestrateur et le frontend ;
3. exécuter `tests/frontend/gitbash/run-connected-playwright.sh` avec les
   identifiants fournis par l'environnement ;
4. implémenter ensuite les API Maker/Checker réelles avant d'autoriser les
   actions de soumission, approbation ou rejet ;
5. raccorder progressivement chaque écran générique à son API métier.

Ne pas inventer de demandes workflow, de secrets ou de données monétiques pour
faire passer le test connecté.

## Processus actifs

Aucun serveur de test démarré par cette session n'est encore actif. Un ancien
serveur Angular sur le port 4200 a été observé et volontairement conservé car il
n'appartient pas à cette session.

## Sauvegarde Git du lot frontend

- branche : `codex/AddingGlobalFrontrend` ;
- commit : `8e8a22d feat(frontend): add global modular portal foundation` ;
- branche distante : `origin/codex/AddingGlobalFrontrend` ;
- état : commit créé et poussé avec succès.

Le module de déploiement a ensuite été implémenté et testé localement. Son état,
ses tests et la promotion future vers la recette sont décrits exclusivement dans
`REPRISE_DEPLOYMENT.md`.

## Mise à jour documentaire et Playwright du 3 août 2026

### Travail terminé

- guide utilisateur global généré à partir du frontend Angular réel :
  `documents/user-guide/GUIDE_UTILISATEUR_SCENARIOGENERATOR_V1.docx`, avec
  versions PDF et Markdown dans le même répertoire ;
- 30 captures Playwright non sensibles produites automatiquement dans
  `documents/user-guide/assets/` par
  `sg-frontend/e2e/user-guide-captures.spec.ts` ;
- manuel frontend porté en version 2.0 :
  `tests/frontend/MANUEL_TEST_FRONTEND_GLOBAL.md` ;
- chargeur Git Bash ajouté : `tests/frontend/gitbash/load-env.sh`. Il doit être
  sourcé avant le lanceur connecté et ne journalise aucune valeur ;
- scripts de génération reproductible du guide ajoutés sous
  `tools/documentation/`.

### Validation exacte

- `bash tests/frontend/gitbash/run-all.sh` : build Angular réussi puis
  `frontend-global-shell.spec.ts`, 4 tests réussis sur 4 ;
- `user-guide-captures.spec.ts` : 1 test réussi, 30 captures produites ;
- `load-env.sh` : syntaxe Git Bash valide et export contrôlé avec un fichier
  temporaire sans afficher sa valeur ;
- PDF final : 33 pages rendues et contrôlées visuellement, aucune page blanche
  ni texte tronqué ;
- DOCX généré avec `python-docx` et archive Office valide. Le rendu LibreOffice
  n'a pas été possible sur cette machine car LibreOffice n'est pas installé ;
  deux tentatives d'export Microsoft Word ont été interrompues après blocage,
  sans arrêter le processus Word qui appartenait déjà à l'utilisateur.

### Premier travail non terminé

Le test Playwright connecté contre l'environnement réel reste à exécuter après
chargement du fichier `.env` local et démarrage de l'orchestrateur/frontend :

```bash
cd /d/MoneyCore/ScenarioGenerator
source ./tests/frontend/gitbash/load-env.sh
bash ./tests/frontend/gitbash/run-connected-playwright.sh
```

Ne jamais ajouter les identifiants ou les valeurs monétiques au dépôt. Les
formulaires métier des espaces encore marqués « Fondation enregistrée » restent
à raccorder aux API réelles module par module.

### Processus actifs et fichiers de la session

Aucun serveur Playwright, Angular ou processus Word démarré par cette session
n'est encore actif. Fichiers ajoutés ou modifiés dans ce lot :

- `REPRISE_FRONTEND.md` ;
- `sg-frontend/e2e/user-guide-captures.spec.ts` ;
- `tests/frontend/MANUEL_TEST_FRONTEND_GLOBAL.md` ;
- `tests/frontend/gitbash/load-env.sh` ;
- `tools/documentation/build_frontend_user_guide.py` ;
- `tools/documentation/render_user_guide_pdf.py` ;
- `documents/user-guide/GUIDE_UTILISATEUR_SCENARIOGENERATOR_V1.{docx,md,pdf}` ;
- `documents/user-guide/assets/*.png` (30 captures).

## Séparation FuturPayment SwitchLab / FuturPayment Switch — Lot 0

### Tâche de référence

- nom : `Adding the two frontend` ;
- thread : `019fd187-9ee0-7603-9e98-78fd01c933a9` ;
- pointeur persistant : `CURRENT_CODEX_TASK.md`.

### Développement présent dans le worktree

- deux applications Angular du même workspace : `futurpayment-switchlab` et
  `futurpayment-switch` ;
- deux points d'entrée, deux configurations TypeScript, deux sorties `dist` et
  deux ports de développement distincts (`4210` et `4220`) ;
- identité produit injectée : marque, sous-titre, menus, groupes de modules et
  modules autorisés ;
- filtrage fail-closed de la navigation dynamique par produit ;
- garde de routes par produit afin d'interdire les routes LAB au Switch et les
  routes membre/production à SwitchLab ;
- titre du navigateur aligné sur le produit ;
- une entrée BFF unique par produit dans les environnements Angular :
  `/api-switchlab` et `/api-switch` en production ;
- contrats frontend normalisés pour pagination, erreurs, actions autorisées,
  santé et exécutions ;
- scripts npm séparés pour démarrer et construire chaque produit.

### Limites et décisions conservées

- le code partagé Angular et le design system restent communs ; les menus et
  routes métier sont séparés à l'exécution et au build ;
- le raccordement réel des chemins `/api-switchlab` et `/api-switch` au reverse
  proxy/BFF relève du déploiement ;
- le bundle SwitchLab inclut désormais `sg-mc-sms-issuer`, `sg-dmcs-issuer`,
  `sg-swam-issuer` et `sg-generator-orchestrator` ;
- le bundle Switch inclut désormais `sg-mc-sms-acquirer` et
  `sg-swam-acquirer`, tandis que `sg-dmcs-issuer` en est exclu ;
- aucun écran métier des lots suivants n'est déclaré terminé par ce Lot 0.

### Validation différée avec l'utilisateur

Les tests de séparation ne doivent pas être lancés automatiquement. Ils seront
préparés et exécutés avec l'utilisateur. Consigner ici leurs commandes et leurs
résultats exacts après cette validation commune.

Compilations réalisées le 5 août 2026, sans exécuter de tests :

- `npm.cmd run build:switchlab -- --configuration development` avec
  `NG_BUILD_MAX_WORKERS=1` : succès, artefact
  `sg-frontend/dist/futurpayment-switchlab` ;
- `npm.cmd run build:switch -- --configuration development` avec
  `NG_BUILD_MAX_WORKERS=1` : succès, artefact
  `sg-frontend/dist/futurpayment-switch`.

Aucun test unitaire, Playwright, connecté ou de séparation n'a été lancé dans
ce lot. La prochaine étape est la validation commune des deux frontends.

## Fondations backend FuturPayment — Lot 0 du 5 août 2026

### Séparation mise en place

- deux contrats Java indépendants : `sg-switchlab-api-contracts` et
  `sg-switch-api-contracts`, sans contrat partagé entre les produits ;
- deux BFF Spring Boot indépendants : `sg-switchlab-bff` sur le port `8090` et
  `sg-switch-bff` sur le port `8091` ;
- le BFF SwitchLab déclare la frontière `SIMULATORS_ONLY` et le BFF Switch la
  frontière `MEMBERS_ONLY` ;
- chaque BFF possède ses propres paramètres backend et JWT
  (issuer, audience et secret) dans son `application.yml` ;
- les environnements Angular de développement pointent respectivement sur
  `http://localhost:8090` et `http://localhost:8091`.

### État de sécurité et limites exactes

- seules les routes d'identité et de santé du produit sont publiques ;
- toutes les autres routes sont fermées par défaut (`denyAll`) tant que
  l'authentification, le RBAC et les proxys métier propres à chaque produit ne
  sont pas développés ;
- aucun appel croisé entre BFF n'a été introduit ;
- ce socle ne prétend pas rendre opérationnels les API métier, le
  Maker/Checker, le journal transactionnel, le registre d'interfaces, le
  rapprochement ou le settlement.

### Vérifications sans tests

- compilation Maven de `sg-switchlab-bff,sg-switch-bff` avec leurs contrats et
  `-Dmaven.test.skip=true` : `BUILD SUCCESS` ;
- validation Maven de `sg-simulators-bundle,sg-members-bundle` avec
  `-Dmaven.test.skip=true` : `BUILD SUCCESS` ;
- aucun test unitaire, fonctionnel, Playwright ou connecté n'a été exécuté.

La prochaine étape sera décidée avec l'utilisateur. Les tests restent différés
pour être préparés et exécutés ensemble.

## FuturPayment SwitchLab — développement du Lot 1 le 5 août 2026

### Frontend développé

- dashboard SwitchLab réel avec sélection d'environnement, état global,
  compteurs et cartes de disponibilité des onze composants simulés ;
- contrats frontend dédiés aux environnements, composants, capacités, agrégat
  de santé et traces structurées ;
- espace Exploitation affichant les connexions/composants et les dernières
  traces corrélées du BFF ;
- réutilisation des écrans existants Utilisateurs, Rôles, Déploiements et
  Licences exclusivement derrière le BFF SwitchLab ;
- suppression des modules membre, du bundle membre et de leur sélection dans
  les écrans de déploiement lorsque le produit actif est SwitchLab.

### BFF SwitchLab développé

- proxy à liste blanche vers l'orchestrateur SwitchLab pour `/auth/login`,
  `/api/me/navigation`, les utilisateurs, les rôles et les déploiements ;
- aucun chemin générique et aucun backend membre autorisé ;
- contrôle de session des agrégats SwitchLab par validation du jeton auprès de
  `/api/me/navigation` ;
- catalogue des onze simulateurs et sondes configurables par variables
  `*_HEALTH_URL` et `*_CAPABILITIES_URL` ;
- état `UNKNOWN` lorsque l'endpoint n'est pas configuré, jamais de faux état
  `UP` ;
- timeout court des sondes, normalisation `UP/DOWN/DEGRADED/UNKNOWN` et
  identifiant de corrélation ;
- journal mémoire borné à 500 requêtes avec méthode, chemin, statut, durée et
  corrélation, sans corps HTTP, jeton, secret ou donnée monétique ;
- CORS de développement limité à `http://localhost:4210`.

### Compilations, sans tests

- `mvn ... -pl sg-switchlab-bff -am compile -Dmaven.test.skip=true` :
  `BUILD SUCCESS` (7 sources contrats et 8 sources BFF) ;
- `npm.cmd run build:switchlab -- --configuration development` : succès,
  artefact `sg-frontend/dist/futurpayment-switchlab` ;
- aucun test unitaire, fonctionnel, Playwright ou connecté n'a été lancé.

### Limites avant acceptation du Lot 1

- le backend orchestrateur doit être disponible via
  `SWITCHLAB_BACKEND_BASE_URL` (valeur locale par défaut :
  `http://localhost:8080`) ;
- les URLs réelles de santé et capacités doivent être renseignées dans
  l'environnement de déploiement ; sans cela, les composants restent
  volontairement `UNKNOWN` ;
- le catalogue BFF expose pour l'instant une référence d'environnement
  configurable ; son alimentation depuis le registre de déploiement doit être
  validée lors du raccordement connecté ;
- la séparation physique de la base utilisateurs/rôles et des clés JWT doit
  être vérifiée dans l'environnement d'exécution ;
- le critère d'acceptation fonctionnel du Lot 1 reste non validé jusqu'aux tests
  réalisés avec l'utilisateur.

## FuturPayment SwitchLab — développement du Lot 2 le 5 août 2026

### TPE & POS développé

- nouvelle entrée de menu et route SwitchLab `/lab/pos` ;
- écran autonome avec six espaces : Transaction, Field-map, Repeat, RKI,
  MTIP sentinelle et Historique ;
- transaction ISO avec paramètres MTI, processing code, PAN, expiration,
  montant, entry mode, terminal, marchand et MAC ;
- field-map JSON avec champs texte/binaires, champs à supprimer, validation et
  PIN temporaire ;
- repeat de la dernière transaction par terminal ;
- assistant RKI en deux phases et option de changement avec confirmation ;
- catalogue des sept scénarios natifs de `sg-way-pos-simulator` et du cas
  `MCD01.Test.01.Scenario.01` ;
- affichage de la réponse obtenue, du résultat attendu, du verdict, de la durée
  et de la corrélation ;
- historique en mémoire du BFF, borné à 200 exécutions.

### BFF POS développé

- liste blanche dédiée sous `/api/switchlab/v1/pos/**` ;
- backend exclusivement configuré par `SWITCHLAB_POS_BASE_URL`, avec valeur
  locale par défaut `http://localhost:8532` ;
- raccordement aux API existantes `/transactions`, `/field-map`, `/repeat`,
  `/key-change` et `/key-change/confirm` du simulateur WayPOS ;
- construction serveur du message ISO du cas sentinelle depuis la référence
  existante, avec dates, STAN et RRN générés au moment de l'appel ;
- verdict sentinelle automatique sur `responseMti=0110`, `responseCode=00` et
  `approved=true` ;
- timeout de connexion court et timeout de lecture compatible avec le timeout
  du simulateur POS.

### Protection des données

- aucun PAN, PIN ou clé de certification n'a été ajouté en dur dans Angular ;
- PAN et PIN du cas MCD01 sont saisis au moment de l'exécution ;
- les champs PAN/PIN du formulaire sont effacés après l'appel ;
- le PIN n'est jamais conservé dans l'historique et le PAN y est masqué ;
- le journal BFF ne contient ni corps HTTP, ni jeton, ni donnée monétique.

### Compilations sans tests

- `mvn ... -pl sg-switchlab-bff -am compile -Dmaven.test.skip=true` :
  `BUILD SUCCESS` (10 sources contrats et 11 sources BFF) ;
- `npm.cmd run build:switchlab -- --configuration development` : succès,
  artefact `sg-frontend/dist/futurpayment-switchlab` ;
- `git diff --check` sur les fichiers du Lot 2 : aucune erreur ;
- aucun test unitaire, fonctionnel, Playwright ou connecté n'a été lancé.

### Limites avant acceptation du Lot 2

- l'équivalence avec la procédure shell validée reste à vérifier ensemble sur
  un serveur WayPOS, des routes, des cartes et des clés de certification réels ;
- le verdict automatique couvre la réponse réseau attendue du cas sentinelle ;
  les contrôles physiques TPE et carte de la référence Mastercard ne peuvent
  pas être prouvés par le seul BFF ;
- l'historique conserve un résumé de requête volontairement masqué et non les
  données PAN/PIN complètes ;
- aucun résultat `PASSED` réel du cas sentinelle n'est déclaré avant le test
  connecté réalisé avec l'utilisateur.

## FuturPayment SwitchLab — développement du Lot 3 le 5 août 2026

### Test Center développé

- nouvelle route `/lab/test-center` et entrée de menu Campagnes & Tests ;
- catalogue de contrôles de disponibilité pour les onze simulateurs ;
- présence du scénario interactif POS `MCD01.Test.01.Scenario.01` avec ses
  références de données obligatoires ;
- filtres par réseau et indication `AUTOMATED`/`INTERACTIVE` ;
- création de campagnes multi-simulateur avec tests sélectionnés, profil,
  disponibilité minimale, durée agrégée maximale et références de données ;
- seules les références `secret://`, `vault://`, `env://` ou `artifact://`
  sont acceptées : aucune donnée claire n'est enregistrée ;
- exécution fonctionnelle des contrôles de santé sur plusieurs simulateurs ;
- rapport consolidé attendu/obtenu avec disponibilité réelle, SLA, verdict,
  durée et corrélation ;
- import contrôlé de métadonnées de résultats JUnit, Playwright et
  certification, sans exécution de commande depuis le navigateur.

### Séparation et sécurité

- toutes les routes proxifiées, hors login, font désormais valider le jeton
  auprès du backend SwitchLab avant transmission ;
- les routes sûres de consultation/CRUD des campagnes, exécutions, tests,
  réseaux et types de messages existants sont listées explicitement ;
- `/api/campaigns/{id}/run`, `/api/executions/start/**` et
  `/api/executions/loadtest/**` sont bloqués dans le BFF car ils utilisent le
  moteur DMAS Member historique ;
- aucun moteur ou backend membre n'a été rendu accessible à SwitchLab.

### Profils SLA et charge

- profil `FUNCTIONAL` disponible pour les contrôles multi-simulateur ;
- profils `LOAD`, `STRESS`, `ENDURANCE` et `SPIKE` visibles mais bloqués
  fail-closed avec la raison exacte ;
- aucun faux moteur de charge n'a été simulé et aucun basculement vers DMAS
  Member n'est possible.

### Compilations sans tests

- compilation BFF et contrats : `BUILD SUCCESS` (18 sources contrats et
  13 sources BFF) ;
- compilation Angular `build:switchlab --configuration development` : succès,
  artefact `sg-frontend/dist/futurpayment-switchlab` ;
- `git diff --check` : aucune erreur ;
- aucun test unitaire, fonctionnel, Playwright, connecté ou de charge exécuté.

### Limites avant achèvement et acceptation du Lot 3

- campagnes, rapports et preuves du nouveau Test Center sont actuellement
  conservés en mémoire et disparaissent au redémarrage du BFF ;
- les adaptateurs transactionnels multi-réseau nécessaires à `LOAD`, `STRESS`,
  `ENDURANCE` et `SPIKE` restent à développer ;
- l'import de certification conserve des métadonnées/références, pas encore le
  contenu analysé d'une procédure ;
- les exports PDF/XLSX consolidés ne sont pas encore développés ;
- le critère final combinant plusieurs scénarios métier, leurs SLA et un export
  unique reste non validé ;
- aucune campagne réelle ne sera déclarée `PASSED` avant les tests effectués
  avec l'utilisateur.
# Mise à jour — Lot 3 SwitchLab finalisé en développement (5 août 2026)

- Campagnes FUNCTIONAL, LOAD, STRESS, ENDURANCE et SPIKE avec durée, TPS cible et concurrence bornés.
- Moteur multi-simulateur de sondes réelles, métriques disponibilité, taux d'erreur et p95.
- Persistance JSON atomique configurable par `SWITCHLAB_TEST_CENTER_STORE_PATH`.
- Analyse contrôlée des manifestes de certification ; PAN, PIN, clés, secrets, Track 2 et cryptogrammes refusés.
- Rapports exportables en PDF et XLSX sans dépendance au backend Switch membre.
- Compilation Maven du BFF/contrats et compilation Angular SwitchLab réussies.
- Aucun test exécuté, conformément à la décision utilisateur.
- Limites et problèmes suivis dans `SOUCIS_FRONTEND_SWITCHLAB.md`.
- Prochaine étape : Lot 4 SwitchLab.

# Mise à jour — Lot 4 SwitchLab développé avec blocages backend (5 août 2026)

- Nouveau cockpit Angular `/lab/online` pour Mastercard DMAS, Mastercard SMS, VisaNet Online et SWAM Online.
- Contrats et adaptateurs BFF séparés sous `/api/switchlab/v1/online/**`.
- Vues normalisées de réseaux, sessions et état de connexion.
- Statut de clé DMAS assaini : type, état, KCV et référence HSM logique uniquement.
- Catalogue de scénarios nominaux/refus avec disponibilité et cause de blocage explicites.
- Scénario DMAS `0800/0810` echo exécutable sans PAN ni clé fournie par le navigateur.
- Les réponses backend brutes contenant des clés ou données sensibles ne sont jamais relayées.
- Compilation Maven BFF/contrats et compilation Angular SwitchLab réussies sans tests.
- Blocages : API SMS dangereuse/incomplète, PAN SWAM par défaut, enveloppe Visa brute et absence de route financière DMAS ; détails dans `SOUCIS_FRONTEND_SWITCHLAB.md`.
- Le développement frontend/BFF du lot est posé, mais le critère d'acceptation nominal/refus multi-réseau reste bloqué par les API backend absentes.
- Prochaine étape : Lot 5 SwitchLab Clearing.

# Mise à jour — Lot 5 SwitchLab développé avec blocages backend (5 août 2026)

- Nouveau cockpit Angular `/lab/clearing` pour Visa Base II, SWAM LIS et Mastercard DMCS.
- Contrats et adaptateurs BFF dédiés sous `/api/switchlab/v1/clearing/**`.
- Upload multipart contrôlé pour SWAM LIS : nom de fichier, extension et taille validés.
- Lancement EOD disponible pour SWAM LIS et DMCS lorsque leurs URL sont configurées.
- Métadonnées d'artefacts et preuves représentées par références opaques `artifact://`.
- Les imports DMCS par chemin serveur et les enveloppes clearing brutes ne sont pas exposés.
- Compilation Maven BFF/contrats et compilation Angular SwitchLab réussies sans tests.
- Blocages : import DMCS dangereux, adaptateur d'artefact Visa absent et téléchargements de preuves non uniformes ; détails dans `SOUCIS_FRONTEND_SWITCHLAB.md`.
- Le cockpit et les flux sûrs sont développés ; le critère fichier de bout en bout reste partiellement bloqué par les API backend.
- Prochaine étape : Lot 6 SwitchLab E-commerce & 3DS.

# Mise à jour — Lot 6 SwitchLab développé avec blocages de sécurité backend (5 août 2026)

- Nouveau cockpit Angular `/lab/ecommerce` pour site marchand, gateway Visa/Mastercard et réseau 3DS.
- Contrats et adaptateurs BFF dédiés sous `/api/switchlab/v1/ecommerce/**`.
- Sondes de santé et capacités normalisées pour les trois composants.
- Catalogue Visa/Mastercard frictionless et challenge visible avec cause de blocage explicite.
- Aucune saisie ni transmission de PAN, expiration, OTP ou donnée de challenge par le frontend.
- La route sandbox divulguant l'OTP n'est jamais relayée.
- Compilation Maven BFF/contrats et compilation Angular SwitchLab réussies sans tests après correction d'un conflit de nom TypeScript.
- Blocages : résolveur serveur de références carte absent, OTP sandbox exposé et simulateur acquiring autonome à confirmer ; détails dans `SOUCIS_FRONTEND_SWITCHLAB.md`.
- Le cockpit est développé ; le critère de parcours complet authentification puis autorisation reste bloqué par les API backend.
- Prochaine étape : Lot 7 SwitchLab Industrialisation commerciale.

# Mise à jour — Lots 0 à 7 SwitchLab développés, tests différés (5 août 2026)

- Nouveau cockpit Angular `/lab/industrialization` et API `/api/switchlab/v1/industrialization/**`.
- Readiness installation/mise à jour, sauvegarde, restauration, audit, licences, sécurité, observabilité, documentation et qualification.
- Sauvegarde JSON téléchargeable limitée à la configuration non sensible ; `containsSecrets=false`.
- Restauration non exposée tant que le workflow Maker/Checker n'est pas opérationnel.
- Audit et alertes marqués partiels : traces BFF en mémoire et moteur d'alertes durable absent.
- Compilation Maven finale de `sg-switchlab-bff` et `sg-switchlab-api-contracts` réussie.
- Compilation Angular finale de `futurpayment-switchlab` réussie.
- Aucun test automatisé ou manuel lancé, conformément à la décision utilisateur de tester après le développement.
- Les Lots 0 à 7 disposent désormais de leurs fondations frontend/BFF et de routes séparées SwitchLab.
- Ne pas présenter les critères d'acceptation comme validés : les blocages backend et sécurité SWLAB-001 à SWLAB-011 sont centralisés dans `SOUCIS_FRONTEND_SWITCHLAB.md`.
- Prochaine étape recommandée : revue commune du registre, correction des API backend bloquantes, puis préparation et exécution des tests avec l'utilisateur.

# FuturPayment Switch — Lot 1 développé le 6 août 2026

- BFF Switch limité au backend membre configuré par `SWITCH_BACKEND_BASE_URL`.
- Proxy authentifié ajouté pour login, navigation, utilisateurs, rôles et déploiements.
- CORS de développement limité à `http://localhost:4220`.
- Contrats propres au produit Switch pour capacité du registre et définition d'interface.
- Nouvelle route Angular `/product/interfaces` avec code, banque, réseau, protocole, format, host/port, priorité, failover et références certificat/clé.
- Les références sont uniquement logiques (`vault://`, `hsm://`) ; aucun secret n'est résolu dans le navigateur.
- Les actions création, Maker/Checker et activation restent désactivées si les capacités backend sont absentes.
- L'écran Exploitation Switch consolide les états de connexion retournés par le registre membre.
- Utilisateurs, rôles et déploiements réutilisent les écrans communs mais passent par le BFF Switch séparé.
- Compilation Maven `sg-switch-bff` et contrats : succès, sans tests.
- Compilation Angular `futurpayment-switch` : succès, sans tests.
- Dépendances ouvertes : registre backend et Maker/Checker absents ; voir `SOUCIS_FRONTEND_SWITCH.md`.
- Dette de séparation : des chunks lazy SwitchLab sont encore générés dans l'artefact Switch à cause du fichier de routes commun, bien qu'ils soient absents du menu et bloqués par guard.
- Prochaine étape : corriger la séparation physique des routes, puis poursuivre le Lot 2 Acquisition.

# FuturPayment Switch — Lots 2 à 7 développés le 6 août 2026

## Séparation physique finalisée

- fichiers de routes indépendants pour legacy, FuturPayment Switch et FuturPayment SwitchLab ;
- `app.config.ts` rendu neutre : aucun import des routes legacy depuis les points d'entrée produit ;
- registres dynamiques injectés par produit : complet pour legacy, membre pour Switch et LAB générique pour SwitchLab ;
- environnements Angular corrigés : SwitchLab vers son BFF `8090`, Switch vers son BFF `8091` ;
- build Switch sans chunks de composants SwitchLab ;
- build SwitchLab sans chunks de composants Switch, `visa-workspace` ou `clearing-workspace` membre ;
- contrôle croisé des JavaScript générés : zéro classe de composant métier du produit opposé.

## Lot 2 — Acquisition POS et e-commerce

- nouveau cockpit `/product/acquiring` ;
- sondes réelles et capacités de `sg-acquiring` et `sg-way-pos-server` via des URL membre configurables ;
- produits d'acceptation, commerçants/points de vente, contrats, terminaux/affectations, boutiques, profils/routage, transactions, 3DS et audit représentés séparément ;
- distinction explicite entre endpoint backend présent, consultation disponible et action autorisée ;
- aucune écriture exposée lorsque les catalogues GET, l'identité Maker/Checker ou le résolveur de référence carte manquent.

## Lot 3 — Issuing membre

- nouveau cockpit `/product/issuing` ;
- sonde de santé et capacités de `sg-card-issuing` ;
- produits, contrats, cartes, interfaces, autorisations et pré-clearing cadrés avec leurs dépendances exactes ;
- aucune consultation carte fictive et aucun PAN dans le frontend.

## Lot 4 — Réseaux temps réel

- nouveau cockpit `/product/networks` ;
- sondes réelles de `sg-mc-dmas-member`, `sg-mc-sms-acquirer`, `sg-swam-acquirer` et `sg-visa-online-member` ;
- sessions, état des clés, routage temps réel et journal transactionnel représentés sans relayer de clé ou de données monétiques ;
- absence d'API consolidée et de résolveur carte affichée en blocage fail-closed.

## Lot 5 — Clearing, rapprochement et settlement

- nouveau cockpit `/product/clearing` ;
- sondes réelles de `sg-dmcs-acquirer`, `sg-swam-lis-member` et `sg-visa-base2-member` ;
- fichiers, EOD, rapprochement, settlement et litiges séparés fonctionnellement ;
- rapprochement et settlement laissés indisponibles tant que les moteurs/API consolidés sont absents.

## Lot 6 — E-commerce et 3DS membre

- nouveau cockpit `/product/ecommerce` ;
- sonde réelle de `sg-3ds-member`, confirmé comme contenant ACS et 3DS Server, et de Visa Online membre ;
- authentifications, preuves et autorisations e-commerce visibles avec leurs limites ;
- aucune saisie de PAN, OTP, clé ou secret et aucune preuve fictive.

## Lot 7 — Industrialisation

- nouveau cockpit `/product/industrialization` ;
- vue de disponibilité des neuf modules membre configurables ;
- déploiements et licences réutilisent les écrans communs via le BFF Switch séparé ;
- observabilité, audit, sauvegarde et restauration restent bloqués lorsque leurs API durables ou Maker/Checker manquent.

## Configuration BFF membre

- identité/navigation : `SWITCH_BACKEND_BASE_URL` ;
- Acquisition : `SWITCH_ACQUIRING_BASE_URL` et `SWITCH_WAY_POS_BASE_URL` ;
- Issuing : `SWITCH_ISSUING_BASE_URL` ;
- réseaux : `SWITCH_DMAS_BASE_URL`, `SWITCH_SMS_BASE_URL`, `SWITCH_SWAM_BASE_URL`, `SWITCH_VISA_ONLINE_BASE_URL` ;
- clearing : `SWITCH_DMCS_BASE_URL`, `SWITCH_SWAM_LIS_BASE_URL`, `SWITCH_VISA_BASE2_BASE_URL` ;
- 3DS membre : `SWITCH_THREE_DS_BASE_URL`.

## Compilations et statut exact

- Maven `sg-switch-bff -am compile -Dmaven.test.skip=true` : `BUILD SUCCESS`, 10 sources contrats et 11 sources BFF ;
- contrôle TypeScript `tsc --noEmit` Switch et SwitchLab : succès ;
- build Angular `futurpayment-switch --configuration development` : succès ;
- build Angular `futurpayment-switchlab --configuration development` : succès ;
- `git diff --check` sur les frontends/BFF/contrats : aucune erreur ;
- aucun test unitaire, fonctionnel, Playwright ou connecté lancé, conformément à la décision utilisateur.

Les fondations frontend/BFF des Lots 0 à 7 du Switch sont développées et compilées. Cela ne valide pas les critères fonctionnels de bout en bout : les dépendances SW-001, SW-002 et SW-004 à SW-011 restent ouvertes dans `SOUCIS_FRONTEND_SWITCH.md`. La prochaine étape autorisée est la revue commune puis la préparation des tests avec l'utilisateur, sans présenter les fonctions backend absentes comme opérationnelles.

# Trois cahiers de recette frontend préparés le 6 août 2026

- `tests/frontend/RECETTE_FRONTEND_SWITCHLAB.md` : 92 cas uniques propres à SwitchLab ;
- `tests/frontend/RECETTE_FRONTEND_SWITCH.md` : 83 cas uniques propres à Switch ;
- `tests/frontend/RECETTE_FRONTEND_TRANSVERSE.md` : 15 cas uniques communs aux deux produits ;
- actions communes, métier, administration, workflow, déploiement et séparation produit incluses ;
- chaque action classée `TESTABLE`, `CONDITIONNEL`, `LECTURE`, `BLOQUÉ` ou `NON EXPOSÉ` ;
- prérequis, rôles, résultats attendus, protections des données et fiche de preuve fournis ;
- aucun test ou cas de recette exécuté pendant la rédaction ;
- le tableau de bord Switch encore en fondation est suivi sous SW-011.

Les contrôles transverses ont été retirés des deux cahiers produit et regroupés
dans le cahier commun. Celui-ci distingue la séparation technique des parcours
métier POS, Online, Clearing et E-commerce/3DS, actuellement bloqués lorsque
les API membre ou simulateur nécessaires manquent.

Prochaine étape : relire les trois cahiers avec l'utilisateur, arrêter le périmètre de la première session, préparer uniquement les services et données autorisés, puis renseigner les résultats cas par cas.

## Réorganisation de la recette transverse le 6 août 2026

### Jalon atteint

- les tests nécessitant les deux produits ont été retirés des cahiers SwitchLab
  et Switch puis regroupés dans `RECETTE_FRONTEND_TRANSVERSE.md` ;
- le cahier commun couvre builds, ports, BFF, identités, routes, navigation,
  données d'administration, déploiements et parcours métier croisés ;
- les parcours POS, Online, Clearing et E-commerce/3DS restent marqués
  `BLOQUÉ` tant que leurs API manquantes empêchent une preuve corrélée dans les
  deux produits ;
- aucun cas de recette n'a été exécuté.

### Premier travail non terminé

Relire les trois cahiers avec l'utilisateur, choisir la première campagne
transverse, démarrer les services autorisés et consigner chaque résultat avec
une preuve assainie. Aucun parcours métier transverse ne doit être déclaré
réussi avant observation des deux côtés.

### Fichiers ajoutés ou modifiés

- `CURRENT_CODEX_TASK.md` ;
- `REPRISE_FRONTEND.md` ;
- `tests/frontend/RECETTE_FRONTEND_SWITCHLAB.md` ;
- `tests/frontend/RECETTE_FRONTEND_SWITCH.md` ;
- `tests/frontend/RECETTE_FRONTEND_TRANSVERSE.md`.

### Vérifications exactes

- comptage des identifiants : SwitchLab 92/92 uniques, Switch 83/83 uniques,
  transverse 15/15 uniques, soit 190 identifiants uniques au total ;
- recherche des anciens identifiants déplacés dans les cahiers produit : aucun
  résultat ;
- recherche de cas produit citant le produit opposé : aucun résultat ;
- numérotation des sections : continue dans les trois cahiers ;
- `git diff --check` sur les cinq fichiers : code retour 0, avec uniquement
  l'avertissement Git existant de conversion LF vers CRLF pour ce journal ;
- aucun test fonctionnel, Playwright ou connecté exécuté.

### Processus actifs

Aucun processus démarré par cette session. Aucun listener observé sur les ports
4210, 4220, 8090 ou 8091 à la fin de la vérification.

## Début de la recette transverse le 7 août 2026

### Résultats obtenus avec l'utilisateur sous Git Bash

- `TR-ARC-001` : `RÉUSSI` ; build Angular développement de
  `futurpayment-switch` terminé en 10,091 s, puis absence de composant
  SwitchLab vérifiée dans les JavaScript, source maps et noms de fichiers ;
- `TR-ARC-002` : `RÉUSSI` ; build Angular développement de
  `futurpayment-switchlab` terminé en 7,202 s, puis absence des composants
  Switch membre, `visa-workspace` et `clearing-workspace` vérifiée dans les
  JavaScript, source maps et noms de fichiers ;
- variables utilisées pour limiter la consommation sur la machine de recette :
  `NG_BUILD_MAX_WORKERS=1` et `NODE_OPTIONS=--max-old-space-size=1536` ;
- aucun test fonctionnel, Playwright ou connecté exécuté à ce stade.

### Premier travail non terminé

Commencer les cas propres à SwitchLab, puis les cas propres à Switch, avant de
reprendre les contrôles transverses nécessitant les quatre processus.

## Recette SwitchLab IHM du 7 août 2026

### Jalon atteint

- les 26 cas `TESTABLE` du cahier SwitchLab ont été automatisés dans
  `sg-frontend/e2e/switchlab-ihm-contract.spec.ts` ;
- la campagne complète Chromium a terminé avec `26 passed (33.0s)`, code
  retour 0 et un worker ;
- la preuve consolidée est
  `tests/frontend/PROOF_OF_TEST_SWITCHLAB_IHM_2026-08-07.md` ;
- l'exécution porte uniquement sur l'IHM réelle du port 4210 avec contrats HTTP
  contrôlés et assainis ; aucune action monétique, ISO ou recette connectée
  n'est déclarée réussie ;
- aucun PAN, PIN, CVC, clé ou secret réel n'a été utilisé.

### Incident de démarrage corrigé

Le nouvel artefact de l'orchestrateur échouait au démarrage parce que
`PermissionRepository` n'était pas inclus dans la liste JPA explicite. Il a été
ajouté dans
`sg-common/src/main/java/com/staging/sg/common/persistence/OrchestratorPersistenceConfiguration.java`.
Le package Maven a ensuite réussi en 38,968 s et le backend a démarré sur 8080
avec 19 repositories JPA.

### Vérifications exactes

- groupes préparatoires : commun/dashboard/POS `8/8`, Test Center `12/12` ;
- après correction de deux libellés du harnais, campagne finale :
  `node ./node_modules/@playwright/test/cli.js test e2e/switchlab-ihm-contract.spec.ts --timeout 15000` ;
- résultat final : `26 passed (33.0s)`, code retour 0 ;
- rapport : `sg-frontend/playwright-report-switchlab/index.html`.

### Processus encore actifs

- frontend SwitchLab : port 4210, PID 13952 ;
- BFF SwitchLab REST : port 8090, PID 23416 ;
- backend identité REST : port 8080, PID 1676 ;
- PostgreSQL : port 5432, PID 22724 ;
- ancien frontend global : arrêté, aucun listener attendu sur 4200.

### Premier travail non terminé

Les 26 cas `TESTABLE` sont terminés. Restent hors de cette preuve les 11 cas
`LECTURE`, 42 `CONDITIONNEL`, 10 `BLOQUÉ` et 3 `NON EXPOSÉ`, ainsi que toute
recette connectée ou monétique. Ne pas les présenter comme réussis sans leurs
prérequis et observations propres.

## Recette Switch IHM du 7 août 2026

### Jalon atteint

- inventaire ligne par ligne du cahier Switch : 83 cas, dont 11 `TESTABLE` ;
- les 11 cas ont été automatisés dans
  `sg-frontend/e2e/switch-ihm-contract.spec.ts` ;
- campagne complète Chromium : `11 passed (52.4s)`, code retour 0, un worker ;
- preuve : `tests/frontend/PROOF_OF_TEST_SWITCH_IHM_2026-08-07.md` ;
- rapport séparé : `sg-frontend/playwright-report-switch/index.html` ;
- aucun test transverse, appel SwitchLab, ISO ou monétique exécuté.

### Correction issue de la recette

Les cartes de services des écrans Acquisition et Domaines membre n'affichaient
pas le code technique fourni par le contrat. Les composants
`switch-acquiring.component.ts` et `switch-member-domain.component.ts`
affichent désormais ce code, ce qui permet de prouver notamment
`SG_DMCS_ACQUIRER` et la frontière membre.

### Rapports Playwright

`playwright.config.ts` accepte désormais `E2E_REPORT_FOLDER` afin de conserver
une preuve distincte par produit. Le rapport SwitchLab a été régénéré dans
`playwright-report-switchlab` : `26 passed (1.4m)`, code retour 0.

### Processus encore actifs

- frontend Switch : port 4220, PID 14052 ;
- frontend SwitchLab : port 4210, PID 13952 ;
- BFF SwitchLab : port 8090, PID 23416 ;
- backend identité : port 8080, PID 1676 ;
- PostgreSQL : port 5432, PID 22724 ;
- BFF Switch : non démarré, aucun listener sur 8091.

### Premier travail non terminé

La prochaine étape est l'exécution **avec l'utilisateur** des tests du cahier
`RECETTE_FRONTEND_TRANSVERSE.md`. Les 29 cas `CONDITIONNEL`, 6 `LECTURE`,
35 `BLOQUÉ` et 2 `NON EXPOSÉ` du cahier Switch restent hors de la preuve IHM
automatique et ne doivent pas être déclarés réussis.
