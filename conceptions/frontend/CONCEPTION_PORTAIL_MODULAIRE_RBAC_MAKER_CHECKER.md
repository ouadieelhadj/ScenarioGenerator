# Conception du portail modulaire, RBAC dynamique et Maker/Checker

## 1. Statut du document

- Statut : proposition technique à valider avant implémentation
- Projet : ScenarioGenerator / plateforme monétique bancaire
- Frontend concerné : `sg-frontend`
- Modules initiaux concernés : SWAM SID, SWAM LIS, DMAS Mastercard et Mastercard SMS
- Date : 27 juillet 2026

Ce document consolide les décisions fonctionnelles prises avec le responsable du
projet. Il décrit l'architecture cible sans démarrer le développement.

## 2. Objectifs

Le portail doit permettre à un utilisateur :

1. d'accéder uniquement aux modules qui lui sont autorisés ;
2. de naviguer dans une arborescence paramétrable de menus, sous-menus et écrans ;
3. d'utiliser des écrans partagés lorsque les métiers et cycles de vie sont
   réellement identiques ;
4. d'exécuter des actions contrôlées par des permissions fines ;
5. de soumettre certaines actions à un circuit Maker/Checker configurable ;
6. de suivre les échéances métier et internes selon le calendrier bancaire
   marocain ;
7. de recevoir des notifications internes et des emails ;
8. de garantir la séparation des responsabilités, la traçabilité et l'audit.

## 3. État actuel

Le frontend actuel repose sur :

- Angular 18 ;
- des composants standalone ;
- PrimeNG ;
- `@ngx-translate` pour le français, l'anglais et l'espagnol ;
- un JWT contenant un rôle et une liste de permissions ;
- des guards Angular d'authentification et de permission ;
- un menu global codé en dur dans `layout/menu.ts` ;
- des routes codées en dur dans `app.routes.ts` ;
- un utilisateur associé à un seul rôle stocké sous forme de texte.

Le socle est exploitable, mais il ne couvre pas encore :

- les modules et menus dynamiques ;
- les profils multiples ;
- les droits individuels ;
- la sécurité par périmètre de données ;
- les équipes ;
- le Maker/Checker ;
- les SLA ;
- les notifications métier ;
- les listes de consultation SWAM LIS.

Les API SWAM LIS actuelles offrent essentiellement des opérations `POST`. Des API
de recherche et consultation paginées devront être ajoutées pour alimenter le
frontend.

## 4. Principes d'architecture

### 4.1 Séparation des responsabilités

Le système sépare quatre dimensions :

| Dimension | Responsabilité |
|---|---|
| Module et navigation | Détermine les modules et écrans visibles |
| Permissions | Détermine les actions autorisées |
| Équipes et affectations | Détermine les données visibles et traitables |
| Workflow Maker/Checker | Détermine le circuit de décision et d'exécution |

La visibilité d'un bouton dans Angular n'est jamais une mesure de sécurité. Toutes
les règles sont recalculées et contrôlées côté backend.

### 4.2 Codes fonctionnels stables

Chaque module, écran et action possède un code stable, indépendant du libellé :

```text
Module : SWAM_LIS_MEMBER
Écran  : CLEARING_CHARGEBACKS
Action : CHARGEBACK_CREATE
```

Les libellés, icônes, positions et traductions restent paramétrables.

### 4.3 Écrans partagés

Un composant Angular peut être partagé entre plusieurs modules si :

- le concept métier est identique ;
- le cycle de vie est identique ;
- les différences sont exprimables par configuration ;
- le composant ne devient pas une succession de conditions propres aux réseaux.

Exemple :

```text
/modules/SWAM_LIS_MEMBER/CLEARING_TRANSACTIONS
/modules/SWAM_LIS_SWITCH/CLEARING_TRANSACTIONS
```

Les deux routes peuvent charger le même composant avec des contextes différents.

Lorsque les règles métier divergent réellement, des écrans spécialisés sont
conservés. La mutualisation du code technique ne doit pas effacer les frontières
métier.

## 5. Navigation dynamique

### 5.1 Arborescence

La profondeur de navigation n'est pas limitée :

```text
Module
└── Menu
    └── Sous-menu
        └── Sous-menu
            └── Écran
```

Exemple SWAM LIS Membre :

```text
SWAM LIS Membre
├── Exploitation
│   ├── Transactions
│   ├── Rapprochement
│   └── Opérations douteuses
├── Échanges LIS
│   ├── Fichiers entrants
│   └── Fichiers sortants
├── Litiges
│   ├── Chargebacks
│   └── Représentations
└── Fin de journée
    ├── Exécution EOD
    └── Comptabilisation
```

### 5.2 Catalogue technique et configuration fonctionnelle

L'administrateur ne saisit pas librement un nom de composant Angular. Les écrans
disponibles sont livrés dans un catalogue technique. L'administrateur peut :

- activer ou désactiver un nœud ;
- choisir son parent ;
- modifier son ordre ;
- choisir son libellé, son icône et ses traductions ;
- l'associer à un module et à un contexte ;
- l'affecter à des profils ou utilisateurs.

Le système empêche :

- les cycles parent/enfant ;
- les routes vers des composants inconnus ;
- la suppression physique d'un nœud utilisé dans l'audit ;
- l'accès à un enfant sans rendre ses parents visibles.

### 5.3 Tables proposées

#### `app_module`

| Colonne | Description |
|---|---|
| `id` | Identifiant |
| `code` | Code stable et unique |
| `label_key` | Clé de traduction |
| `icon` | Icône |
| `display_order` | Ordre |
| `active` | État |
| `config_json` | Configuration non sensible |

#### `navigation_node`

| Colonne | Description |
|---|---|
| `id` | Identifiant |
| `module_id` | Module |
| `parent_id` | Parent nullable |
| `node_type` | `MENU`, `SUBMENU`, `SCREEN` |
| `code` | Code stable dans le module |
| `label_key` | Clé de traduction |
| `icon` | Icône |
| `screen_definition_id` | Écran technique pour un nœud `SCREEN` |
| `display_order` | Ordre |
| `active` | État |
| `context_json` | Paramètres du module et de l'écran |

#### `screen_definition`

| Colonne | Description |
|---|---|
| `id` | Identifiant |
| `code` | Code technique du composant |
| `route_template` | Route Angular générique |
| `component_key` | Clé connue du registre Angular |
| `shared_screen` | Écran mutualisable |
| `active` | État |

## 6. Profils, utilisateurs et autorisations

### 6.1 Profils multiples

Un utilisateur peut appartenir à plusieurs profils simultanément.

```text
Utilisateur
├── OPERATEUR_CLEARING
├── CHECKER_LITIGES
└── droits individuels
```

Le modèle actuel `users.role` devra être remplacé progressivement par une relation
plusieurs-à-plusieurs. Une migration préservera les rôles existants.

Tables principales :

- `security_profile` ;
- `user_profile` ;
- `permission` ;
- `profile_permission` ;
- `user_permission_override`.

### 6.2 Calcul des droits effectifs

```text
union des autorisations des profils
+ autorisations individuelles
- interdictions individuelles
= droits effectifs
```

Une interdiction individuelle est prioritaire.

### 6.3 Droit de navigation et droit d'action

Le droit de voir un écran est distinct du droit d'y agir.

Actions génériques possibles :

- `VIEW` ;
- `CREATE` ;
- `UPDATE` ;
- `DELETE` ;
- `IMPORT` ;
- `EXPORT` ;
- `GENERATE` ;
- `SUBMIT` ;
- `APPROVE` ;
- `REJECT` ;
- `EXECUTE` ;
- `RETRY` ;
- `REASSIGN` ;
- `SUPERVISE`.

Chaque écran peut compléter ce catalogue avec des actions métier stables.

### 6.4 Affectations de navigation

Les tables suivantes permettent les affectations aux profils et aux utilisateurs :

- `profile_navigation_grant` ;
- `user_navigation_override`.

Si un utilisateur possède l'accès à un écran enfant, ses parents sont renvoyés par
l'API de navigation afin que l'arborescence reste utilisable. Cela ne lui donne pas
accès aux écrans frères.

## 7. Équipes et périmètre de données

### 7.1 Appartenances multiples et contextualisées

Un utilisateur peut appartenir à plusieurs équipes selon le module ou l'opération :

```text
Utilisateur A
├── SWAM LIS Membre / Chargeback : Maker
├── SWAM LIS Switch / Chargeback : Checker
└── DMAS / Gestion des clés : Superviseur
```

Les profils autorisent une fonction. Les équipes déterminent sur quelles données
l'utilisateur peut l'exercer.

### 7.2 Rôles d'équipe

- `MAKER` : traite uniquement ses propres opérations ;
- `CHECKER` : décide uniquement sur les demandes des Makers qui lui sont rattachés ;
- `SUPERVISOR` : voit le périmètre de son équipe et peut réaffecter ;
- `ADMINISTRATOR` : paramètre le système, sans accès automatique aux données métier.

Un administrateur doit posséder une permission explicite de supervision pour voir
les données métier.

### 7.3 Tables proposées

- `business_team` ;
- `business_team_membership` ;
- `team_operation_scope` ;
- `operation_assignment`.

Une appartenance contient :

- le module ;
- l'opération ;
- le rôle d'équipe ;
- la date de début ;
- la date de fin ;
- l'état ;
- la personne ayant créé ou modifié l'affectation.

## 8. Maker/Checker transverse

### 8.1 Principe

Le Maker/Checker est un moteur transverse applicable à toute action sensible :

- émission d'un chargeback ;
- représentation ;
- lancement EOD ;
- comptabilisation ;
- import d'un fichier ;
- échange ou modification d'une clé ;
- opération future d'un autre module.

Il est configuré par :

```text
module + écran + action
```

Une action peut être :

- directe, sans validation ;
- soumise à Maker/Checker ;
- exécutée en batch ;
- exécutée immédiatement par API ;
- exécutée manuellement par un opérateur autorisé.

### 8.2 Affectation Maker vers Checker

Pour une opération donnée :

- chaque Maker possède un Checker principal ;
- un Checker peut être responsable de plusieurs Makers ;
- un Checker remplaçant peut être défini ;
- les affectations sont datées et historisées.

La même personne ne peut jamais être Maker et Checker de la même demande :

```text
maker_user_id != checker_user_id
```

Cette règle est contrôlée dans le service métier et protégée au niveau de la base.

### 8.3 Changement du Checker par le Maker

Le Maker peut changer le Checker de son opération :

- motif obligatoire ;
- choix parmi les Checkers autorisés ;
- impossibilité de se sélectionner ;
- notification de l'ancien et du nouveau Checker ;
- historique complet ;
- aucun report automatique des échéances.

### 8.4 Changement du Maker par le Superviseur

Le Superviseur peut réaffecter une opération à un autre Maker autorisé :

- motif obligatoire ;
- contrôle du périmètre d'équipe ;
- transfert propre d'un éventuel verrou ;
- conservation des échéances ;
- notification des utilisateurs concernés ;
- audit avant/après.

### 8.5 Délégation et remplacement

Une délégation comporte :

- Checker principal ;
- Checker remplaçant ;
- période de validité ;
- motif ;
- autorité ayant validé la délégation ;
- règle applicable aux demandes existantes ;
- trace d'audit.

### 8.6 Cycle de vie

Statuts génériques proposés :

```text
DRAFT
PENDING_APPROVAL
APPROVED
REJECTED
CANCELLED
APPROVED_PENDING_BATCH
SENDING
INCLUDED_IN_OUTGOING
SENT
ACKNOWLEDGED
EXECUTION_FAILED
DELIVERY_FAILED
COMPLETED
```

Les statuts génériques n'effacent pas les statuts métier. Une demande conserve :

- son statut de workflow ;
- son statut d'exécution ;
- son statut métier ;
- son statut de délai.

### 8.7 Données de la demande

La demande conserve :

- module, écran et action ;
- référence de l'objet métier ;
- données proposées ;
- empreinte des données ;
- Maker ;
- Checker prévu et Checker effectif ;
- commentaires ;
- justificatifs ;
- décisions ;
- dates et SLA ;
- mode d'exécution ;
- clé d'idempotence ;
- historique complet.

Les données sensibles ne sont jamais stockées en clair dans un JSON d'audit si la
politique de sécurité impose leur masquage ou chiffrement.

## 9. Exécution après décision du Checker

### 9.1 Responsabilité du Checker

Le Checker prend la décision métier. Le moteur d'exécution applique ensuite le
canal configuré.

### 9.2 Mode `BATCH`

Exemple : chargeback SWAM LIS.

```text
Maker soumet
→ Checker valide
→ APPROVED_PENDING_BATCH
→ le batch LIS sélectionne la demande
→ INCLUDED_IN_OUTGOING
→ fichier outgoing généré
→ SENT puis ACKNOWLEDGED selon le retour
```

Une demande ne peut être intégrée qu'une seule fois dans un fichier outgoing actif.

### 9.3 Mode `API_IMMEDIATE`

```text
Maker soumet
→ Checker valide
→ SENDING
→ appel du connecteur de l'entité
→ SENT ou DELIVERY_FAILED
```

L'appel est idempotent. Les tentatives et réponses techniques sont historisées.

### 9.4 Mode `MANUAL`

Ce mode reste disponible pour une entité sans batch ni API, mais doit être limité
aux cas justifiés. L'opérateur confirme l'exécution avec une preuve.

### 9.5 Outbox transactionnelle

Une table d'outbox est recommandée afin que la décision et la demande d'exécution
soient enregistrées dans la même transaction. Un worker :

- récupère les événements ;
- choisit le connecteur ;
- applique les relances ;
- garantit l'idempotence ;
- journalise les erreurs ;
- dirige les échecs définitifs vers une file de traitement.

## 10. Création des opérations

### 10.1 Cas du chargeback

Le système affiche les transactions éligibles. Il ne crée pas automatiquement une
demande Maker/Checker pour chaque transaction.

La demande est créée uniquement lorsque le Maker :

1. sélectionne une transaction éligible ;
2. choisit l'action de chargeback ;
3. complète les informations requises ;
4. enregistre ou soumet sa demande.

Cela évite de créer des demandes inutiles.

### 10.2 Affectation automatique

Les transactions éligibles sont automatiquement affectées à un Maker selon des
règles configurables :

- type d'opération ;
- module ou entité ;
- montant ;
- niveau de risque ;
- portefeuille ;
- disponibilité ;
- équilibrage de charge.

Le Maker voit uniquement les opérations qui lui sont affectées.

### 10.3 Concurrence

La sélection d'une transaction et la création de la demande utilisent un verrou
optimiste ou atomique. Deux Makers ne peuvent pas créer deux demandes actives pour
la même transaction et la même action.

## 11. SLA et calendrier bancaire marocain

### 11.1 Deux familles d'échéances

Le système gère :

1. l'échéance métier externe de la transaction ;
2. les échéances internes de traitement.

Les échéances internes comprennent :

- échéance Maker ;
- échéance Checker ;
- échéance d'exécution technique ;
- échéance globale.

Toutes sont calculées au moment de la génération de l'opération et enregistrées
sur celle-ci.

### 11.2 Échéance métier avant création d'une demande

La transaction éligible conserve sa propre échéance métier. Même si aucun Maker
n'a encore créé de demande :

- l'approche de l'échéance est signalée ;
- le dépassement est affiché en rouge ;
- des notifications sont envoyées selon la configuration.

### 11.3 Calendrier

Le calcul utilise :

- le fuseau `Africa/Casablanca` ;
- les jours ouvrés bancaires marocains ;
- les week-ends ;
- les jours fériés nationaux ;
- les fêtes religieuses actualisables ;
- les fermetures exceptionnelles ;
- les journées exceptionnellement ouvrées ;
- les heures d'ouverture et de clôture.

Une opération créée après l'heure limite commence son délai au prochain jour
ouvré.

### 11.4 Tables proposées

- `business_calendar` ;
- `business_calendar_day` ;
- `sla_policy` ;
- `sla_escalation_rule` ;
- `operation_deadline`.

### 11.5 Conservation des échéances

Une modification future du SLA ne modifie pas les demandes déjà créées. Une
prolongation exceptionnelle :

- nécessite une permission ;
- exige un motif ;
- conserve les anciennes dates ;
- produit un événement d'audit ;
- ne doit pas être implicite lors d'une réaffectation.

### 11.6 Présentation visuelle

| Couleur | Signification |
|---|---|
| Vert | Dans les délais |
| Orange | Échéance proche |
| Rouge | Échéance dépassée |
| Gris | Étape terminée ou non applicable |

Le statut de délai ne remplace pas le statut métier :

```text
workflow_status = PENDING_APPROVAL
deadline_status = OVERDUE
```

## 12. Notifications

### 12.1 Canaux

- notification interne systématique ;
- email paramétrable par module, écran et action.

### 12.2 Événements

Les événements initiaux comprennent :

- demande soumise ;
- demande affectée ;
- Checker modifié ;
- Maker modifié ;
- validation ;
- rejet ;
- échéance proche ;
- échéance dépassée ;
- relance ;
- escalade ;
- inclusion dans un batch ;
- succès ou échec d'exécution.

### 12.3 Règle validée pour les retards

Lorsqu'une opération du Maker dépasse son échéance :

- elle est affichée en rouge ;
- un email est envoyé au Maker ;
- le Checker est mis en copie ;
- l'événement est historisé.

Un mécanisme de déduplication empêche les emails répétés involontairement.

### 12.4 Modèles et journal

Les modèles d'email sont versionnés et traduisibles. Les tables proposées sont :

- `notification_template` ;
- `notification_preference` ;
- `notification_event` ;
- `notification_delivery`.

Le journal conserve le canal, les destinataires, le modèle, la tentative, le
résultat et l'erreur technique, sans exposer inutilement le contenu sensible.

## 13. Audit

Toutes les actions sensibles produisent une trace append-only :

- connexion et échec de connexion ;
- modification des profils et permissions ;
- modification de navigation ;
- modification d'équipes ;
- changement Maker/Checker ;
- délégation ;
- création et modification de demande ;
- décision ;
- exécution ;
- prolongation de SLA ;
- relance et échec.

Une entrée contient :

- acteur ;
- date et heure ;
- action ;
- module ;
- objet et identifiant ;
- ancienne et nouvelle valeur masquées si nécessaire ;
- motif ;
- adresse et contexte technique pertinents ;
- identifiant de corrélation.

L'audit n'est pas modifiable par les utilisateurs fonctionnels.

## 14. API cibles

### 14.1 Session utilisateur

```text
POST /auth/login
GET  /api/me
GET  /api/me/navigation
GET  /api/me/permissions
GET  /api/me/assignments
```

Le JWT doit rester compact. L'arborescence complète ne doit pas être placée dans
le token. Le backend renvoie les droits détaillés via `/api/me`.

### 14.2 Administration

```text
/api/admin/modules
/api/admin/navigation
/api/admin/screens
/api/admin/profiles
/api/admin/users/{id}/profiles
/api/admin/users/{id}/overrides
/api/admin/teams
/api/admin/maker-checker-rules
/api/admin/assignments
/api/admin/calendars
/api/admin/sla-policies
/api/admin/notification-policies
```

### 14.3 Workflow

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

### 14.4 Consultation SWAM LIS

Les API suivantes devront être ajoutées côté membre et switch :

```text
GET /api/clearing/transactions
GET /api/clearing/transactions/{id}
GET /api/clearing/files
GET /api/clearing/files/{id}
GET /api/clearing/reconciliation
GET /api/clearing/suspicious
GET /api/clearing/chargebacks
GET /api/clearing/chargebacks/{id}
GET /api/clearing/batches
GET /api/clearing/accounting/entries
GET /api/clearing/dashboard
```

Les listes doivent être paginées, filtrables et triables côté serveur.

## 15. Sécurité des API

Les services LIS permettent actuellement les accès `/api/clearing/**` sans
authentification. La cible doit :

- valider le JWT ou une identité transmise par une gateway de confiance ;
- contrôler la permission d'action ;
- contrôler le module ;
- contrôler l'affectation et le périmètre de données ;
- refuser l'accès direct aux opérations d'un autre Maker ;
- vérifier la relation Maker/Checker ;
- journaliser les refus sensibles.

Les appels de batch entre services utilisent une identité technique distincte des
utilisateurs humains.

## 16. Architecture Angular cible

Organisation proposée :

```text
src/app/
├── core/
│   ├── auth/
│   ├── navigation/
│   ├── permissions/
│   ├── module-context/
│   ├── workflow/
│   ├── notifications/
│   └── api/
├── layout/
│   ├── module-selector/
│   ├── dynamic-navigation/
│   └── notification-center/
├── shared/
│   ├── data-grid/
│   ├── deadline-badge/
│   ├── workflow-timeline/
│   ├── audit-viewer/
│   └── action-toolbar/
└── features/
    ├── administration/
    ├── clearing/
    ├── network/
    ├── security-keys/
    ├── cards/
    └── module-specific/
```

### 16.1 Contexte de module

Le contexte fournit :

- code module ;
- code écran ;
- configuration ;
- endpoint logique ;
- capacités ;
- permissions ;
- règles d'affichage.

Le frontend ne choisit pas une URL arbitraire transmise par la base. Il résout un
code d'API connu via un registre local ou une gateway afin d'éviter les appels vers
des destinations non fiables.

### 16.2 Registre des écrans

Un registre Angular associe une `component_key` connue à un composant lazy-loaded.
La base ne contient que cette clé validée.

### 16.3 Routes

Route générique proposée :

```text
/modules/:moduleCode/:screenCode
```

Un resolver charge le contexte autorisé avant d'activer le composant.

### 16.4 Guards

- `authGuard` : session valide ;
- `moduleGuard` : module autorisé ;
- `screenGuard` : écran autorisé ;
- `actionGuard` ou directive : action visible ;
- les contrôles backend restent la référence.

## 17. Écrans communs initiaux SWAM LIS

Les modules `SWAM_LIS_MEMBER` et `SWAM_LIS_SWITCH` peuvent partager le socle des
écrans suivants :

- tableau de bord clearing ;
- transactions ;
- rapprochement ;
- opérations douteuses ;
- fichiers entrants et sortants ;
- chargebacks ;
- représentations ;
- EOD ;
- comptabilité ;
- historique et audit.

Le contexte détermine l'API membre ou switch, les colonnes, les actions et les
libellés.

Une différence de cycle de vie découverte pendant l'implémentation entraînera une
extension spécialisée, pas une multiplication de conditions opaques.

## 18. Écrans d'administration nécessaires

1. Catalogue des modules ;
2. Constructeur d'arborescence ;
3. Catalogue des écrans ;
4. Profils et permissions ;
5. Affectations utilisateur ;
6. Équipes ;
7. Affectations Maker vers Checker ;
8. Délégations ;
9. Règles Maker/Checker ;
10. SLA ;
11. Calendrier bancaire marocain ;
12. Notifications ;
13. Audit.

Les modifications de sécurité importantes peuvent elles-mêmes être soumises à
Maker/Checker dans une phase ultérieure.

## 19. Contraintes non fonctionnelles

### 19.1 Concurrence et idempotence

- verrouillage optimiste des demandes ;
- version technique sur les entités ;
- clé d'idempotence pour l'exécution ;
- prévention du double clic et du double traitement ;
- traitements batch atomiques.

### 19.2 Performance

- pagination serveur ;
- index sur module, statut, Maker, Checker, équipe et échéance ;
- cache court de navigation et permissions ;
- invalidation du cache après modification administrative ;
- compteurs de dashboard préagrégés si nécessaire.

### 19.3 Observabilité

- identifiant de corrélation de bout en bout ;
- métriques de délai par étape ;
- volume en attente par Maker et Checker ;
- taux de rejet ;
- échecs techniques ;
- opérations dépassant le SLA ;
- journalisation sans données carte sensibles.

### 19.4 Données sensibles

- PAN masqué ;
- aucune clé cryptographique claire dans les logs ou le frontend ;
- pièces jointes contrôlées et analysées ;
- chiffrement adapté des données sensibles ;
- contrôle des exports.

## 20. Migration proposée

### Phase 1 — Socle de sécurité

- profils multiples ;
- catalogue module/écran/action ;
- navigation dynamique ;
- droits profil/utilisateur ;
- adaptation de `/api/me`.

### Phase 2 — Équipes et périmètre

- équipes ;
- rôles Maker, Checker et Superviseur ;
- affectations ;
- contrôles de visibilité des données.

### Phase 3 — Workflow transverse

- demandes ;
- décisions ;
- changements Maker/Checker ;
- délégations ;
- audit ;
- outbox.

### Phase 4 — SLA et notifications

- calendrier bancaire marocain ;
- politiques SLA ;
- échéances ;
- notifications internes ;
- emails et escalades.

### Phase 5 — Consultation SWAM LIS

- API `GET` paginées ;
- écrans communs membre/switch ;
- dashboards ;
- transactions, fichiers, rapprochement, litiges et comptabilité.

### Phase 6 — Actions SWAM LIS

- EOD ;
- génération/import LIS ;
- chargeback ;
- représentation ;
- comptabilisation ;
- intégration au Maker/Checker.

## 21. Tests attendus

### 21.1 Sécurité

- cumul de plusieurs profils ;
- priorité des interdictions individuelles ;
- parents visibles sans accès aux écrans frères ;
- Maker limité à ses opérations ;
- Checker limité à ses Makers ;
- accès Superviseur limité à son équipe ;
- refus de l'auto-validation.

### 21.2 Workflow

- soumission, validation et rejet ;
- changement de Checker ;
- changement de Maker ;
- délégation ;
- concurrence entre décisions ;
- idempotence API et batch ;
- reprise après erreur.

### 21.3 SLA

- week-end ;
- jour férié marocain ;
- fête religieuse ;
- fermeture exceptionnelle ;
- création avant et après cut-off ;
- rappels et dépassements ;
- absence de report lors d'une réaffectation.

### 21.4 Frontend

- navigation dynamique ;
- route interdite ;
- écran partagé avec deux contextes ;
- permissions d'action ;
- couleurs d'échéance ;
- notifications ;
- traduction ;
- pagination et filtres.

### 21.5 Non-régression

Les campagnes, DMAS, l'administration actuelle et les autres modules doivent
continuer à fonctionner pendant la migration.

## 22. Décisions validées

- accès à plusieurs modules ;
- navigation hiérarchique et paramétrable ;
- affectations aux profils et utilisateurs ;
- plusieurs profils par utilisateur ;
- écrans communs pilotés par contexte lorsque le métier le permet ;
- Maker/Checker configurable par écran et action ;
- interdiction de l'auto-validation ;
- un Checker principal par Maker et opération ;
- un Checker peut gérer plusieurs Makers ;
- Checker remplaçant et délégation datée ;
- changement de Checker par le Maker ;
- changement de Maker par le Superviseur ;
- un Maker ne voit que ses propres opérations ;
- utilisateurs membres de plusieurs équipes ;
- exécution après décision par batch ou API selon l'entité ;
- création du workflow uniquement lorsque le Maker choisit l'action ;
- affectation automatique des transactions au Maker ;
- gestion des échéances métier et internes ;
- calendrier des jours ouvrés bancaires marocains ;
- prise en compte du cut-off ;
- calcul initial des échéances Maker, Checker, technique et globale ;
- notification interne et email paramétrable ;
- opération expirée en rouge ;
- email au Maker avec Checker en copie.

## 23. Points à confirmer avant implémentation

Les décisions structurantes sont couvertes. Les paramètres suivants pourront être
définis lors du paramétrage initial sans remettre en cause l'architecture :

- liste initiale des modules ;
- arborescence initiale de chaque module ;
- catalogue exact des actions ;
- SLA par opération ;
- heure de cut-off ;
- règles d'affectation automatique ;
- serveur et politique d'envoi des emails ;
- durée de conservation de l'audit et des notifications ;
- règles précises d'escalade.

