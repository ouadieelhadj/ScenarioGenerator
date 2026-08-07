# Cahier de recette — FuturPayment SwitchLab

Version : 1.0
Date de référence : 6 août 2026
Produit : `FuturPayment SwitchLab`
Frontend : `http://localhost:4210`
BFF exclusif : `http://localhost:8090`

## 1. Objet et règle d'exécution

Ce document couvre les actions réellement visibles dans le frontend SwitchLab.
Les 26 cas marqués `TESTABLE` ont été exécutés le 7 août 2026 au niveau IHM avec
des réponses HTTP de contrat contrôlées : **26/26 réussis**. La preuve détaillée
est consignée dans `PROOF_OF_TEST_SWITCHLAB_IHM_2026-08-07.md`. Ce résultat ne
valide ni les cas conditionnels connectés ni une action monétique.

Les contrôles nécessitant SwitchLab et Switch, notamment la séparation des
identités, routes, bundles, données et parcours métier, sont décrits uniquement
dans `tests/frontend/RECETTE_FRONTEND_TRANSVERSE.md`.

États utilisés :

| État | Signification |
|---|---|
| `TESTABLE` | L'action peut être vérifiée avec le frontend et le BFF démarrés. |
| `CONDITIONNEL` | L'action exige un backend, un simulateur, une configuration ou des données de recette autorisées. |
| `LECTURE` | Consultation ou sélection sans mutation métier. |
| `BLOQUÉ` | L'interface ferme volontairement l'action à cause d'une dépendance connue. |
| `NON EXPOSÉ` | La fonction n'existe volontairement pas dans l'interface. |

Un cas `CONDITIONNEL` non exécutable dans l'environnement doit être noté `BLOQUÉ ENVIRONNEMENT`, jamais `RÉUSSI`.

## 2. Sécurité des données de recette

- Ne jamais utiliser de PAN, PIN, CVC, OTP, clé ou secret réel.
- Utiliser uniquement les cartes et clés de certification explicitement fournies pour la recette.
- Ne jamais copier les valeurs sensibles dans une capture, un rapport ou ce document.
- Les références acceptables sont `secret://`, `vault://`, `env://` et `artifact://`.
- Vérifier que les historiques masquent le PAN et ne conservent jamais le PIN.
- Vérifier qu'aucune réponse brute contenant une clé n'est affichée.

## 3. Prérequis

1. Démarrer uniquement les services prévus par la session de recette.
2. Configurer `SWITCHLAB_BACKEND_BASE_URL` pour l'identité et la navigation.
3. Configurer selon les lots testés :
   - `SWITCHLAB_POS_BASE_URL` ;
   - `SWITCHLAB_ONLINE_DMAS_BASE_URL`, `SWITCHLAB_ONLINE_VISA_BASE_URL`, `SWITCHLAB_ONLINE_SWAM_BASE_URL` ;
   - `SWITCHLAB_CLEARING_VISA_BASE_URL`, `SWITCHLAB_CLEARING_SWAM_BASE_URL`, `SWITCHLAB_CLEARING_DMCS_BASE_URL` ;
   - `SWITCHLAB_ECOMMERCE_MERCHANT_BASE_URL`, `SWITCHLAB_ECOMMERCE_GATEWAY_BASE_URL`, `SWITCHLAB_ECOMMERCE_3DS_BASE_URL`.
4. Fournir deux comptes distincts pour les contrôles Maker/Checker lorsqu'ils sont disponibles.
5. Ne pas démarrer le frontend Switch membre sur le port 4210.

## 4. Contrôles communs

| ID | Route/écran | Action | Rôle | État | Résultat attendu |
|---|---|---|---|---|---|
| SL-COM-001 | `/login` | Connexion valide | utilisateur SwitchLab | `CONDITIONNEL` | Redirection vers `/dashboard`, identité SwitchLab et menu LAB uniquement. |
| SL-COM-002 | `/login` | Connexion invalide | anonyme | `CONDITIONNEL` | Refus explicite, aucun jeton conservé. |
| SL-COM-003 | barre supérieure | Changer FR/EN/ES | connecté | `TESTABLE` | Libellés actualisés sans changer de produit. |
| SL-COM-004 | barre supérieure | Changer le thème | connecté | `TESTABLE` | Thème appliqué sans perte de session. |
| SL-COM-005 | barre supérieure | Changer la couleur primaire | connecté | `TESTABLE` | Couleur appliquée, contraste encore lisible. |
| SL-COM-006 | barre supérieure | Déconnexion | connecté | `TESTABLE` | Session locale supprimée et retour à `/login`. |
| SL-COM-008 | URL dynamique non accordée | Tenter un module absent des droits | utilisateur limité | `CONDITIONNEL` | Redirection vers `/forbidden`. |

## 5. Tableau de bord et exploitation

| ID | Route | Action | État | Résultat attendu |
|---|---|---|---|---|
| SL-DASH-001 | `/dashboard` | Charger les environnements | `CONDITIONNEL` | Seuls les environnements SwitchLab actifs sont proposés. |
| SL-DASH-002 | `/dashboard` | Changer d'environnement | `CONDITIONNEL` | L'agrégat est rechargé pour l'identifiant sélectionné. |
| SL-DASH-003 | `/dashboard` | Actualiser | `CONDITIONNEL` | États `UP/DOWN/DEGRADED/UNKNOWN`, date et corrélation actualisés. |
| SL-DASH-004 | `/dashboard` | Vérifier une sonde non configurée | `TESTABLE` | État `UNKNOWN`, jamais un faux `UP`. |
| SL-OPS-001 | `/product/operations` | Actualiser l'exploitation | `CONDITIONNEL` | Connexions/composants et traces corrélées rechargés. |
| SL-OPS-002 | `/product/operations` | Examiner les traces | `LECTURE` | Méthode, route, statut, durée et corrélation uniquement ; aucun corps, jeton ou secret. |

## 6. TPE et POS

Route : `/lab/pos`.

| ID | Action | État | Prérequis et résultat attendu |
|---|---|---|---|
| SL-POS-001 | Changer d'onglet Transaction/Field-map/Repeat/RKI/MTIP/Historique | `TESTABLE` | L'onglet sélectionné s'affiche sans perdre les données non sensibles autorisées. |
| SL-POS-002 | Envoyer une transaction ISO | `CONDITIONNEL` | WayPOS Simulator, route, terminal et données de certification disponibles ; réponse, durée et corrélation affichées. |
| SL-POS-003 | Envoyer un field-map | `CONDITIONNEL` | JSON valide ; champs texte/binaires/suppressions appliqués par le backend. |
| SL-POS-004 | Envoyer un field-map invalide | `TESTABLE` | Validation explicite, aucune requête dangereuse exécutée. |
| SL-POS-005 | Répéter la dernière transaction | `CONDITIONNEL` | Terminal connu ; repeat corrélé à la transaction précédente. |
| SL-POS-006 | Demander un changement RKI | `CONDITIONNEL` | Aucun matériel de clé clair saisi ou affiché. |
| SL-POS-007 | Confirmer les statuts RKI | `CONDITIONNEL` | Résultat des statuts affiché sans clé claire. |
| SL-POS-008 | Exécuter RKI avec confirmation | `CONDITIONNEL` | Les deux phases sont corrélées et le verdict est explicite. |
| SL-POS-009 | Exécuter `MCD01.Test.01.Scenario.01` | `CONDITIONNEL` | Carte/terminal/clés de certification fournis ; aucun résultat `PASSED` avant contrôle réel. |
| SL-POS-010 | Effacement après envoi | `CONDITIONNEL` | PAN et PIN disparaissent du formulaire après l'appel. |
| SL-POS-011 | Actualiser l'historique | `TESTABLE` | Historique BFF borné ; PAN masqué et PIN absent. |
| SL-POS-012 | Sélectionner une exécution historique | `LECTURE` | Détail, attendu, obtenu, verdict et corrélation affichés. |

## 7. Test Center

Route : `/lab/test-center`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SL-TC-001 | Changer d'onglet Catalogue/Campagnes/Rapports/Preuves | `TESTABLE` | Contenu correspondant affiché. |
| SL-TC-002 | Filtrer le catalogue par réseau | `TESTABLE` | Seuls les contrôles du réseau choisi restent visibles. |
| SL-TC-003 | Créer une campagne FUNCTIONAL | `TESTABLE` | Campagne persistée avec tests, SLA et références opaques. |
| SL-TC-004 | Créer LOAD/STRESS/ENDURANCE/SPIKE | `CONDITIONNEL` | Bornes durée/TPS/concurrence appliquées ; voir SWLAB-001 pour la portée réelle. |
| SL-TC-005 | Saisir une référence `secret://`/`vault://`/`env://`/`artifact://` | `TESTABLE` | Référence acceptée sans résolution dans le navigateur. |
| SL-TC-006 | Saisir une valeur sensible en clair | `TESTABLE` | Rejet explicite, rien de sensible persisté. |
| SL-TC-007 | Exécuter une campagne | `CONDITIONNEL` | Environnement actif ; rapport avec disponibilité, erreurs, p95 et corrélation. |
| SL-TC-008 | Consulter un rapport | `LECTURE` | Attendu/obtenu/verdict présents pour chaque contrôle. |
| SL-TC-009 | Exporter un rapport PDF | `TESTABLE` | Téléchargement PDF associé au bon identifiant. |
| SL-TC-010 | Exporter un rapport XLSX | `TESTABLE` | Téléchargement XLSX associé au bon identifiant. |
| SL-TC-011 | Analyser un manifeste valide | `TESTABLE` | Analyse sans exécution de commande locale. |
| SL-TC-012 | Analyser un manifeste contenant PAN/PIN/clé/secret | `TESTABLE` | Rejet explicite. |
| SL-TC-013 | Importer des métadonnées JUnit | `TESTABLE` | Compteurs et référence seulement. |
| SL-TC-014 | Importer des métadonnées Playwright | `TESTABLE` | Aucun navigateur ou test déclenché par l'import. |
| SL-TC-015 | Importer une preuve certification | `TESTABLE` | Verdict calculé depuis les compteurs, référence opaque conservée. |

## 8. Réseaux Online simulés

Route : `/lab/online`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SL-ONL-001 | Sélectionner DMAS/SMS/Visa/SWAM | `LECTURE` | Session, capacité et limitations du réseau sélectionné. |
| SL-ONL-002 | Lire la session réseau | `CONDITIONNEL` | État normalisé, sans donnée monétique. |
| SL-ONL-003 | Lire l'état de clé DMAS | `CONDITIONNEL` | Type, état, KCV et référence logique seulement. |
| SL-ONL-004 | Exécuter l'echo DMAS | `CONDITIONNEL` | Résultat, RC et corrélation ; aucun PAN requis. |
| SL-ONL-005 | Exécuter un scénario financier DMAS | `BLOQUÉ` | Bouton désactivé tant que la route financière manque. |
| SL-ONL-006 | Exécuter Mastercard SMS | `BLOQUÉ` | Réponse brute de clé jamais relayée ; SWLAB-003/006. |
| SL-ONL-007 | Exécuter SWAM financier | `BLOQUÉ` | Aucun PAN par défaut utilisé ; SWLAB-004/006. |
| SL-ONL-008 | Exécuter Visa Online brut | `BLOQUÉ` | Aucune enveloppe ISO Base64 construite dans le navigateur. |

## 9. Clearing simulé

Route : `/lab/clearing`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SL-CLR-001 | Sélectionner Visa Base II/SWAM LIS/DMCS | `LECTURE` | Capacités upload/EOD/litiges et limitations visibles. |
| SL-CLR-002 | Charger un fichier SWAM LIS autorisé | `CONDITIONNEL` | Extension et taille validées ; artefact retourné par référence opaque. |
| SL-CLR-003 | Charger une extension ou taille interdite | `TESTABLE` | Rejet avant traitement. |
| SL-CLR-004 | Lancer EOD SWAM LIS | `CONDITIONNEL` | Date métier validée, résultat et preuve opaque affichés. |
| SL-CLR-005 | Lancer EOD DMCS | `CONDITIONNEL` | URL DMCS configurée ; exécution corrélée. |
| SL-CLR-006 | Importer DMCS par chemin serveur | `NON EXPOSÉ` | Aucun champ chemin local disponible ; SWLAB-007. |
| SL-CLR-007 | Envoyer une enveloppe Visa brute | `BLOQUÉ` | Aucun contenu clearing brut transmis ; SWLAB-008. |
| SL-CLR-008 | Consulter les artefacts | `LECTURE` | Métadonnées non sensibles et références `artifact://`. |

## 10. E-commerce et 3DS simulés

Route : `/lab/ecommerce`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SL-ECO-001 | Consulter les composants | `LECTURE` | Site marchand, gateway et réseau 3DS avec états réels/`UNKNOWN`. |
| SL-ECO-002 | Consulter les scénarios frictionless/challenge | `LECTURE` | Programme, flux et cause de blocage visibles. |
| SL-ECO-003 | Exécuter frictionless | `BLOQUÉ` | Bouton désactivé sans résolveur serveur de carte. |
| SL-ECO-004 | Exécuter challenge | `BLOQUÉ` | Aucun OTP affiché ou demandé ; SWLAB-009/010. |
| SL-ECO-005 | Ouvrir la route sandbox OTP | `NON EXPOSÉ` | Route jamais relayée par le BFF. |

## 11. Industrialisation

Route : `/lab/industrialization`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SL-IND-001 | Consulter la readiness | `LECTURE` | Installation, sauvegarde, audit, licences, sécurité et observabilité classés sans faux succès. |
| SL-IND-002 | Télécharger la sauvegarde | `TESTABLE` | Fichier `switchlab-configuration-backup.json`, `containsSecrets=false`. |
| SL-IND-003 | Rechercher un secret dans la sauvegarde | `TESTABLE` | Aucun secret, clé ou donnée monétique. |
| SL-IND-004 | Restaurer une sauvegarde | `NON EXPOSÉ` | Aucun bouton ni endpoint sans Maker/Checker ; SWLAB-011. |

## 12. Campagnes historiques et exécutions

Routes : `/campaign-generation`, `/campaign-orchestration` et `/executions`.

Ces écrans historiques restent distincts du Test Center SwitchLab. Le BFF interdit leur lancement lorsqu'il basculerait vers le moteur DMAS membre.

| ID | Action | Permission | État | Résultat attendu |
|---|---|---|---|---|
| SL-CAMP-001 | Consulter les campagnes | `CAMPAIGN_VIEW` | `CONDITIONNEL` | Campagnes retournées par le backend SwitchLab, sans contenu membre. |
| SL-CAMP-002 | Ouvrir la création | `CAMPAIGN_CREATE` | `TESTABLE` | Formulaire affiché. |
| SL-CAMP-003 | Ajouter/supprimer une étape | `CAMPAIGN_CREATE` | `TESTABLE` | Ordre et contenu du formulaire actualisés localement. |
| SL-CAMP-004 | Enregistrer une campagne | `CAMPAIGN_CREATE` | `CONDITIONNEL` | CRUD SwitchLab autorisé et campagne rechargée. |
| SL-CAMP-005 | Modifier une campagne | `CAMPAIGN_CREATE` | `CONDITIONNEL` | Modification persistée sans données sensibles. |
| SL-CAMP-006 | Supprimer une campagne | `CAMPAIGN_CREATE` | `CONDITIONNEL` | Suppression confirmée par le backend. |
| SL-CAMP-007 | Lancer depuis l'orchestrateur historique | `TPS_RUN`/`CAMPAIGN_REPLAY` | `BLOQUÉ` | Le BFF refuse le moteur DMAS membre ; utiliser `/lab/test-center`. |
| SL-CAMP-008 | Arrêter le suivi visuel | connecté | `TESTABLE` | Polling UI arrêté, aucune exécution backend annulée implicitement. |
| SL-CAMP-009 | Actualiser l'orchestration | connecté | `CONDITIONNEL` | État réel rechargé. |
| SL-CAMP-010 | Consulter les exécutions | `EXECUTION_VIEW` | `CONDITIONNEL` | Liste réelle uniquement. |
| SL-CAMP-011 | Ouvrir/fermer le détail d'exécution | `EXECUTION_VIEW` | `LECTURE` | Détail sélectionné puis dialogue fermé sans mutation. |

## 13. Administration, workflow et déploiement

| ID | Action | Rôle/permission | État | Résultat attendu |
|---|---|---|---|---|
| SL-ADM-001 | Consulter les utilisateurs | `USER_MANAGE` | `CONDITIONNEL` | Liste issue du backend SwitchLab séparé. |
| SL-ADM-002 | Créer un utilisateur | `USER_MANAGE` | `CONDITIONNEL` | Utilisateur SwitchLab uniquement ; mot de passe jamais réaffiché. |
| SL-ADM-003 | Modifier email/rôle | `USER_MANAGE` | `CONDITIONNEL` | Login non modifiable en édition. |
| SL-ADM-004 | Activer/désactiver un utilisateur | `USER_MANAGE` | `CONDITIONNEL` | État actualisé après confirmation backend. |
| SL-ADM-005 | Consulter rôles/permissions | `ROLE_MANAGE` | `CONDITIONNEL` | Catalogue SwitchLab séparé. |
| SL-WF-001 | Ouvrir Mes opérations | connecté | `BLOQUÉ` | Message d'indisponibilité si API workflow absente, aucune ligne fictive. |
| SL-WF-002 | Ouvrir Mes validations | checker | `BLOQUÉ` | Aucune approbation locale ou auto-validation. |
| SL-DEP-001 | Créer un client | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Client enregistré via le BFF SwitchLab. |
| SL-DEP-002 | Créer un environnement | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Seuls modules/bundle simulateurs visibles ; modules membre absents. |
| SL-DEP-003 | Référencer le secret DB | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Référence `secret://`, jamais mot de passe clair. |
| SL-DEP-004 | Lancer le preflight | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Contrôles et erreurs détaillés, aucun faux succès. |
| SL-DEP-005 | Préparer une licence | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Statut en attente d'approbation. |
| SL-DEP-006 | Approuver une licence | `DEPLOYMENT_APPROVE` | `CONDITIONNEL` | Checker distinct selon règle backend. |
| SL-DEP-007 | Demander une exécution | `DEPLOYMENT_EXECUTE` | `CONDITIONNEL` | Demande `PENDING_APPROVAL`. |
| SL-DEP-008 | Approuver une exécution | `DEPLOYMENT_APPROVE` | `CONDITIONNEL` | Identité checker et statut actualisés. |

## 14. Route dynamique LAB

Route : `/lab/:moduleCode/:screen`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SL-DYN-001 | Ouvrir un écran `MODULE_WORKSPACE` autorisé | `LECTURE` | Contexte LAB et libellé issus de la navigation. |

## 15. Fiche de résultat à dupliquer

```text
ID du cas :
Date/heure :
Testeur :
Environnement :
Version frontend/BFF :
Compte et rôle (sans mot de passe) :
Données/références utilisées (sans valeur sensible) :
Résultat : RÉUSSI | ÉCHEC | BLOQUÉ ENVIRONNEMENT | NON EXÉCUTÉ
Résultat obtenu :
Corrélation :
Preuve : capture/rapport/référence d'artefact
Anomalie associée :
```

## 16. Critères de fin de recette SwitchLab

- Tous les cas `TESTABLE` sont exécutés et documentés.
- Chaque cas `CONDITIONNEL` est exécuté ou possède un blocage environnement précis.
- Les cas `BLOQUÉ` restent effectivement inactifs.
- Aucun secret ou donnée monétique sensible n'apparaît dans l'UI, les traces ou les preuves.
- Aucun lot n'est déclaré accepté tant que les anomalies critiques et les critères convenus avec l'utilisateur ne sont pas clos.
