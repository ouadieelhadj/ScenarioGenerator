# Cadrage frontend final — ScenarioGenerator

## 1. Statut

| Élément | Valeur |
|---|---|
| Version | 1.1 |
| Date | 2 août 2026 |
| Statut | Validé pour démarrage du développement |
| Frontend | `sg-frontend` — Angular 18 standalone |
| Tests E2E | Playwright |
| Sources | Cadrages global, Issuing et Visa, code Angular, backend, SQL RBAC/workflow et décisions utilisateur |

Ce document consolide et améliore les deux cadrages V0.1 après vérification du
code réel. Il fixe l'organisation du portail avant développement.

## 2. Décisions validées

1. Le portail comporte une partie commune permanente et des menus métier
   distincts.
2. Les modules visibles de premier niveau sont :
   - ServerPOS ;
   - Acquisition POS et e-commerce ;
   - Issuing ;
   - SWAM Membre ;
   - DMAS Membre ;
   - DMCS Membre ;
   - VisaNet / Visa Online Membre ;
   - Visa Base II Membre ;
   - Recette et simulateurs.
3. Utilisateurs et Rôles restent dans l'Administration commune.
4. Maker/Checker est un moteur transverse unique. Il n'est pas dupliqué dans
   chaque module.
5. Le simulateur de site marchand local et international appartient
   exclusivement au menu « Recette et simulateurs ».
6. Playwright est l'outil officiel des tests E2E Angular.
7. Les fonctions absentes du backend restent fail-closed et sont affichées
   comme indisponibles ; aucun succès fictif n'est autorisé.
8. Les APIs machine-à-machine financières ne sont jamais exposées directement
   comme formulaires métier du navigateur.

## 3. Résultat de l'audit du code existant

### 3.1 Fonctionnel et réutilisable

- Angular 18 standalone, PrimeNG, RxJS et signaux Angular ;
- authentification JWT et interceptor Bearer ;
- guards et directive de permissions ;
- thèmes clair/sombre et personnalisation par tokens CSS ;
- traductions française, anglaise et espagnole ;
- campagnes, orchestration, exécutions, DMAS, utilisateurs, configuration,
  profil et aide ;
- navigation dynamique via `GET /api/me/navigation` ;
- modèle SQL de modules, écrans, navigation, profils multiples, équipes,
  Maker/Checker, workflow, SLA, notifications et audit ;
- Playwright déjà installé et configuré.

### 3.2 Écarts confirmés

- le menu dynamique remplace actuellement le menu commun au lieu de coexister
  avec lui ;
- les sous-menus sont aplatis dans la barre latérale ;
- `componentKey` est reçu mais n'est pas résolu par un registre Angular ;
- toutes les routes dynamiques chargent le même placeholder clearing ;
- les permissions Angular utilisent une logique « au moins une » seulement ;
- le JWT expose encore un rôle historique unique ;
- Maker/Checker possède des tables et une conception, mais aucun contrôleur,
  service ou écran opérationnel ;
- Utilisateurs est opérationnel, mais Rôles ne possède ni API dédiée ni écran ;
- les rôles sont encore codés en dur dans Angular ;
- la configuration API ne connaît que `orchestrator`, `acquirer` et `issuer` ;
- la cible `issuer` correspond au DMAS issuer historique et non à
  `sg-card-issuing` ;
- des fixtures monétiques de test sont intégrées à `api.config.ts` ; elles
  doivent être retirées du bundle de production ;
- aucun runner de tests unitaires Angular n'est configuré ;
- plusieurs domaines ne proposent que des commandes et manquent de listes GET
  paginées nécessaires à une IHM opérationnelle.

## 4. Architecture de navigation cible

```text
ScenarioGenerator
├── Commun
│   ├── Tableau de bord
│   ├── Campagnes
│   │   ├── Génération
│   │   ├── Orchestration
│   │   └── Exécutions
│   ├── Mes opérations
│   │   ├── Mes demandes
│   │   ├── Mes validations
│   │   ├── Risques SLA
│   │   └── Notifications
│   ├── Supervision
│   ├── Administration
│   │   ├── Utilisateurs
│   │   ├── Rôles et permissions
│   │   ├── Profils et affectations
│   │   ├── Équipes
│   │   ├── Affectations Maker/Checker
│   │   ├── Politiques workflow et SLA
│   │   ├── Catalogue et navigation
│   │   ├── Configuration
│   │   └── Audit
│   ├── Profil
│   └── Aide
├── ServerPOS
├── Acquisition POS et e-commerce
├── Issuing
├── SWAM Membre
├── DMAS Membre
├── DMCS Membre
├── VisaNet / Visa Online Membre
├── Visa Base II Membre
└── Recette et simulateurs
```

Le menu commun reste toujours disponible. La sélection d'un module métier
change uniquement l'arborescence métier affichée, sans faire disparaître les
fonctions transverses.

## 5. Catalogue des modules

| Code stable | Libellé | Responsabilité |
|---|---|---|
| `CORE_PORTAL` | Commun | Accueil, campagnes, workflow, supervision et administration |
| `SERVER_POS` | ServerPOS | Projection technique TPE, routage et RKI |
| `ACQUIRING` | Acquisition POS et e-commerce | Commerçants, contrats, terminaux et acceptation e-commerce |
| `CARD_ISSUING` | Issuing | Produits, contrats, cartes, interfaces et exploitation issuing |
| `SWAM_MEMBER` | SWAM Membre | SID temps réel et LIS clearing du membre |
| `DMAS_MEMBER` | DMAS Membre | Connexion Mastercard DMAS côté membre |
| `DMCS_MEMBER` | DMCS Membre | Clearing Mastercard côté membre |
| `VISA_ONLINE_MEMBER` | VisaNet / Visa Online Membre | Autorisation Visa côté banque membre |
| `VISA_BASE2_MEMBER` | Visa Base II Membre | Clearing, règlement et litiges Visa côté membre |
| `LAB_SIMULATORS` | Recette et simulateurs | Simulateurs et scénarios réservés LAB/DEV |

Les bibliothèques communes, modules E2E internes et composants techniques ne
deviennent pas des menus utilisateur.

## 6. Menus détaillés

### 6.1 ServerPOS

- tableau de bord et santé ;
- profils terminaux ;
- projections commerçant/terminal provenant d'Acquiring ;
- routes BIN/IIN ;
- état de provisioning ;
- inventaire des clés par référence, type, version, état et KCV ;
- rotations et changements de clés soumis à politique ;
- journal des transactions et événements masqués ;
- capacités et dépendances.

ServerPOS ne crée pas un second référentiel de commerçants ou terminaux.

### 6.2 Acquisition POS et e-commerce

- tableau de bord ;
- produits d'acceptation ;
- commerçants et points de vente ;
- contrats commerçants ;
- terminaux et affectations ;
- boutiques e-commerce ;
- profils d'acceptation et routage ;
- transactions POS et e-commerce ;
- authentifications 3DS et preuves associées ;
- dépendances, événements et audit.

Le site marchand simulé n'apparaît pas ici.

### 6.3 Issuing

- tableau de bord ;
- programmes, produits, services et limites ;
- porteurs, contrats et comptes supports ;
- recherche cartes, émissions et actes carte ;
- production et personnalisation ;
- prépayé ;
- journal des autorisations, holds, SAF et pré-clearing ;
- interfaces par émetteur, capacités et santé ;
- audit et événements.

Le code stable du module est `CARD_ISSUING`. La cible API doit être distincte
de la clé historique DMAS `issuer`.

### 6.4 SWAM Membre

- dashboard et santé ;
- session SID, sign-on, sign-off et echo ;
- inventaire des clés et état des échanges ;
- opérations temps réel en consultation masquée ;
- clearing LIS : transactions, fichiers, rapprochement, litiges, EOD et
  comptabilisation ;
- événements et audit.

Les commandes financières de test restent dans le module LAB.

### 6.5 DMAS Membre

- dashboard et état réseau ;
- sessions, sign-on, sign-off et echo ;
- inventaire des clés et rotations ;
- autorisations, advices et reversals en consultation ;
- capacités, dépendances, alertes et audit.

Le réseau Mastercard DMAS simulé et les cartes de recette appartiennent au LAB.

### 6.6 DMCS Membre

- dashboard clearing ;
- fichiers entrants et sortants ;
- transactions et records ;
- rapprochement ;
- chargebacks et secondes présentations ;
- EOD ;
- comptabilisation et settlement lorsque disponibles ;
- audit.

L'IHM ne fabrique jamais un DE31/ARN absent.

### 6.7 VisaNet / Visa Online Membre

- tableau de bord, santé et dépendances ;
- interfaces et sessions réseau ;
- sign-on, sign-off et echo ;
- autorisations et réponses en consultation masquée ;
- reversals, advices et repeats ;
- journal d'autorisation et références ACI/TID/code de validation ;
- configuration, capacités, événements et audit.

Le menu membre ne permet jamais de modifier les règles de décision du
simulateur VisaNet.

### 6.8 Visa Base II Membre

- tableau de bord clearing ;
- journées métier et EOD ;
- projections et transactions clearing ;
- fichiers, lots, TCR et erreurs ;
- rapprochement et écarts ;
- litiges, réponses et reversals ;
- frais, change, règlement et comptabilisation ;
- catalogues versionnés, dépendances et audit.

L'envoi de fichier, le rapprochement manuel, les litiges, les réponses et la
comptabilisation sont soumis aux politiques Maker/Checker applicables. Aucun
code Visa absent n'est fabriqué par l'IHM.

### 6.9 Recette et simulateurs

Visible uniquement dans les environnements et profils autorisés :

- catalogue et exécution des scénarios ;
- POS Simulator ;
- Merchant Site Simulator, avec site local et site international ;
- simulateur réseau 3DS ;
- simulateur gateway Visa/Mastercard ;
- simulateur réseau DMAS Mastercard ;
- simulateur switch SWAM ;
- simulateurs Mastercard SMS disponibles ;
- simulateur réseau VisaNet Online : sessions, issuer externe, décisions,
  timeouts/repeats et traces ISO masquées ;
- simulateur réseau Visa Base II : réception/livraison de fichiers, contrôles,
  rejets, litiges et positions simulées ;
- progression, résultats, corrélation, preuves et nettoyage.

Les backends Visa sandbox sont disponibles, mais les écrans restent explicites
sur leur statut non certifié. Aucun secret, PAN complet, CAVV, buffer ISO brut
ou donnée HSM n'est persisté dans le navigateur.

## 7. Utilisateurs, rôles et profils

### 7.1 État actuel

- l'écran Utilisateurs et les API CRUD/toggle existent ;
- les entités `Role` et `Permission`, leurs repositories et la relation
  `role_permissions` existent ;
- `user_profiles` prépare plusieurs profils par utilisateur ;
- le frontend utilise encore trois rôles codés en dur et un champ `role`
  unique.

### 7.2 Cible

- conserver immédiatement l'écran Utilisateurs sans régression ;
- ajouter une page Rôles et permissions en lecture, puis en administration ;
- ajouter profils multiples et affectations utilisateur ;
- distinguer profil de sécurité et rôle d'équipe
  `MAKER/CHECKER/SUPERVISOR` ;
- ne jamais donner automatiquement accès aux données métier à un
  administrateur technique ;
- faire calculer les droits effectifs par le backend : union des profils,
  autorisations individuelles et priorité aux interdictions.

API minimales :

```text
GET /api/admin/roles
GET /api/admin/permissions
GET /api/admin/users/{id}/profiles
PUT /api/admin/users/{id}/profiles
GET /api/me
GET /api/me/permissions
GET /api/me/assignments
```

## 8. Maker/Checker transverse

### 8.1 Règles

- politique définie par `moduleCode + screenCode + actionCode` ;
- un Maker ne voit que ses demandes et son périmètre ;
- un Checker ne voit que les demandes de ses Makers autorisés ;
- un Superviseur peut réaffecter dans son équipe ;
- auto-validation interdite par service et contrainte base ;
- motif obligatoire pour rejet et réaffectation ;
- échéances conservées lors des réaffectations ;
- idempotence et contrôle de version sur chaque décision ;
- exécution finale par le service métier via `DIRECT`, `API_IMMEDIATE`,
  `BATCH` ou `MANUAL` ;
- aucune approbation UI ne modifie directement une table métier.

### 8.2 Écrans communs

- Mes demandes ;
- Mes validations ;
- détail de demande ;
- comparaison avant/après masquée ;
- timeline ;
- risques SLA ;
- notifications ;
- administration des équipes, affectations, délégations et politiques.

### 8.3 API requises

```text
GET  /api/workflow/my-operations
GET  /api/workflow/my-approvals
GET  /api/workflow/{id}
POST /api/workflow/{id}/submit
POST /api/workflow/{id}/approve
POST /api/workflow/{id}/reject
POST /api/workflow/{id}/change-checker
POST /api/workflow/{id}/reassign-maker
POST /api/workflow/{id}/retry
```

Ces API n'existent pas encore. Les écrans frontend doivent donc rester
fail-closed jusqu'à leur disponibilité.

### 8.4 Action pilote

La première intégration bout en bout sera l'approbation d'un produit Issuing :

```text
Maker crée ou modifie le produit
→ soumet la demande
→ Checker distinct compare et décide
→ moteur workflow appelle le service Issuing
→ résultat réel et audit sont restitués
```

## 9. Architecture Angular cible

```text
src/app/
├── core/
│   ├── api/
│   ├── auth/
│   ├── navigation/
│   ├── module-context/
│   ├── permissions/
│   ├── workflow/
│   ├── notifications/
│   └── observability/
├── layout/
│   ├── common-navigation/
│   ├── module-selector/
│   ├── dynamic-navigation/
│   └── breadcrumbs/
├── shared/
│   ├── data-grid/
│   ├── filter-panel/
│   ├── status-badge/
│   ├── action-toolbar/
│   ├── workflow-timeline/
│   ├── audit-viewer/
│   └── empty-error-loading/
└── features/
    ├── core-portal/
    ├── administration/
    ├── workflow/
    ├── server-pos/
    ├── acquiring/
    ├── issuing/
    ├── swam-member/
    ├── dmas-member/
    ├── dmcs-member/
    ├── visa-online-member/
    ├── visa-base2-member/
    └── lab-simulators/
```

Un registre local versionné associe chaque `componentKey` à un composant
lazy-loaded connu. Une clé inconnue affiche un écran indisponible et génère une
trace nettoyée. La base ne fournit jamais une URL ou un composant arbitraire.

Routes communes :

```text
/dashboard
/campaigns/generation
/campaigns/orchestration
/executions
/workflow/my-operations
/workflow/my-approvals
/administration/users
/administration/roles
/administration/:screenCode
/modules/:moduleCode/:screenCode
/lab/:screenCode
```

## 10. Accès API

La cible recommandée est :

```text
Navigateur → Gateway/BFF portail → services métier
```

Le frontend ne doit pas gérer librement tous les ports des microservices. En
attendant la gateway, les cibles logiques restent explicitement nommées et
aucune clé ambiguë comme `issuer` n'est réutilisée pour deux services.

Toutes les listes utilisent pagination, tri et filtres serveur. Toutes les
commandes utilisent corrélation, idempotence, version optimiste et un format
d'erreur commun.

## 11. Sécurité

- retirer les fixtures PAN, PIN et clés de `api.config.ts` du build de
  production ;
- ne jamais stocker PAN complet, PIN, CVC, PIN block, cryptogramme, clé claire,
  commande HSM ou secret dans le navigateur ;
- séparer strictement production et LAB ;
- masquer les identifiants sensibles dans listes, audit et notifications ;
- recalculer permissions et périmètre côté backend ;
- refuser les URLs de service et identités métier saisies librement ;
- afficher clairement les erreurs `403`, `409`, `422`, `429` et `503` ;
- conserver la corrélation sans exposer les détails techniques sensibles.

## 12. Stratégie de développement

### Lot 0 — Socle portail

- navigation commune permanente ;
- menus métier hiérarchiques ;
- registre de composants ;
- guards module/écran/action ;
- routes communes normalisées ;
- retrait des fixtures sensibles du bundle de production ;
- composants communs de statut, erreur et indisponibilité.

### Lot 1 — Administration et Maker/Checker

- Utilisateurs sans régression ;
- Rôles et permissions ;
- profils multiples ;
- équipes et affectations ;
- Mes demandes et Mes validations ;
- API workflow, timeline, décision et audit ;
- action pilote produit Issuing.

### Lot 2 — Modules métier prioritaires

- Issuing Phase 1 ;
- Acquisition POS/e-commerce ;
- ServerPOS.

### Lot 3 — Réseaux et clearing

- SWAM Membre ;
- DMAS Membre ;
- DMCS Membre ;
- VisaNet / Visa Online Membre ;
- Visa Base II Membre.

### Lot 4 — Recette et simulateurs

- catalogue de scénarios ;
- POS Simulator ;
- Merchant Site Simulator local/international ;
- simulateurs 3DS et réseaux ;
- simulateur réseau VisaNet Online ;
- simulateur réseau Visa Base II ;
- résultats et preuves de recette.

## 13. Stratégie de tests

### 13.1 Build et contrôles statiques

- `npm run build` ;
- cohérence des traductions FR/EN/ES ;
- détection de secrets et PAN complets dans le bundle ;
- contrôle des routes et du registre de composants.

### 13.2 Playwright

1. utilisateur non authentifié redirigé vers login ;
2. menu commun toujours visible après chargement des modules ;
3. menus ServerPOS, Acquisition, Issuing, SWAM Membre, DMAS Membre,
   DMCS Membre, VisaNet Membre, Visa Base II Membre et Simulateurs visibles
   selon droits ;
4. sous-menus hiérarchiques non aplatis ;
5. accès direct interdit redirigé vers forbidden ;
6. Utilisateurs reste fonctionnel ;
7. Rôles et permissions sont consultables ;
8. Maker limité à ses demandes ;
9. Checker limité à ses Makers ;
10. auto-validation refusée ;
11. soumission, approbation, rejet et conflit concurrent ;
12. Merchant Site Simulator présent uniquement dans Simulateurs ;
13. absence du menu Simulateurs hors environnement autorisé ;
14. dépendance absente affichée indisponible sans faux succès ;
15. absence de secrets et PAN complet dans DOM, URL et stockage ;
16. non-régression campagnes, exécutions, DMAS, thèmes et langues.
17. simulateurs VisaNet et Base II visibles uniquement dans le LAB ;
18. impossibilité pour un profil membre de modifier une décision du simulateur ;
19. actions Base II sensibles soumises au Maker/Checker.

Les tests connectés nécessitant des backends indisponibles utilisent des mocks
contractuels Playwright clairement identifiés. Ils ne remplacent pas les E2E
réels, qui restent séparés dans les rapports.

## 14. Critères d'acceptation

- la partie commune et les modules métier coexistent dans la navigation ;
- chaque module possède son propre menu hiérarchique ;
- Utilisateurs et Rôles sont présents dans l'Administration ;
- Maker/Checker est transverse et interdit l'auto-validation ;
- le Merchant Site Simulator est uniquement dans Simulateurs ;
- VisaNet Membre et Visa Base II Membre possèdent chacun un menu métier ;
- les deux simulateurs réseau Visa restent uniquement dans Simulateurs ;
- les routes directes respectent le RBAC ;
- une clé de composant inconnue reste fail-closed ;
- aucune donnée monétique interdite n'est exposée ;
- les fonctions manquantes restent explicitement indisponibles ;
- le build Angular et les tests Playwright passent ;
- les écrans historiques ne régressent pas.

## 15. Premier travail de développement

Le développement commence par le Lot 0, dans cet ordre :

1. rendre le menu commun permanent ;
2. rendre la navigation métier hiérarchique ;
3. ajouter le catalogue des neuf modules métier validés ;
4. introduire le registre de composants et l'écran indisponible fail-closed ;
5. créer les routes Administration Utilisateurs/Rôles et Workflow ;
6. retirer les fixtures sensibles de la configuration de production ;
7. adapter Playwright et valider la non-régression.

## 16. État d'implémentation initial

Le Lot 0 est implémenté au 2 août 2026. Le build Angular passe, les trois tests
Playwright contractuels passent et l'orchestrateur compilant l'API de lecture
des rôles est validé avec la non-régression de 69 tests `sg-common`.

Le test connecté au catalogue SQL 19 reste à exécuter sur une base de recette.
Les API Maker/Checker ne sont pas encore implémentées : le frontend reste donc
volontairement fermé et n'affiche aucune approbation fictive.

L'extension V1.1 ajoute VisaNet Membre, Visa Base II Membre et les deux
simulateurs réseau. Les menus, le composant de domaine et le contrôle
Playwright sont implémentés ; les actions certifiées de clearing/litige restent
fermées tant que les contrats officiels correspondants ne sont pas disponibles.
