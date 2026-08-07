# Cahier de recette — FuturPayment Switch

Version : 1.0
Date de référence : 6 août 2026
Produit : `FuturPayment Switch`
Frontend : `http://localhost:4220`
BFF exclusif : `http://localhost:8091`

## 1. Objet et règle d'exécution

Ce document couvre toutes les actions actuellement visibles dans le frontend
Switch membre. Les 11 cas marqués `TESTABLE` ont été exécutés automatiquement
le 7 août 2026 au niveau IHM avec des réponses HTTP de contrat contrôlées :
**11/11 réussis**. La preuve détaillée est consignée dans
`PROOF_OF_TEST_SWITCH_IHM_2026-08-07.md`. Ce résultat ne valide ni les cas
connectés, ni une action monétique, ni les tests transverses.

Les contrôles nécessitant Switch et SwitchLab, notamment la séparation des
identités, routes, bundles, données et parcours métier, sont décrits uniquement
dans `tests/frontend/RECETTE_FRONTEND_TRANSVERSE.md`.

| État | Signification |
|---|---|
| `TESTABLE` | Action vérifiable avec le frontend et le BFF. |
| `CONDITIONNEL` | Nécessite un module membre, une configuration, un rôle ou des données autorisées. |
| `LECTURE` | Consultation sans mutation métier. |
| `BLOQUÉ` | Action fermée volontairement car une dépendance manque. |
| `NON EXPOSÉ` | Fonction volontairement absente de l'interface. |

Une fonction dont le backend manque ne doit jamais être déclarée réussie à partir du seul affichage de son cockpit.

## 2. Frontière de sécurité

- Le navigateur appelle uniquement le BFF Switch.
- Le BFF appelle uniquement les modules membre confirmés.
- Aucun simulateur et aucune route du BFF SwitchLab ne doivent être accessibles.
- Aucun PAN, PIN, CVC, OTP, clé ou secret réel ne doit être saisi, affiché ou journalisé.
- Les références de certificat et de clé restent logiques : `vault://...` et `hsm://...`.
- Les actions Maker/Checker restent désactivées tant que l'identité signée et les API correspondantes manquent.

## 3. Prérequis

1. Configurer l'identité/navigation avec `SWITCH_BACKEND_BASE_URL`.
2. Configurer uniquement les modules à tester :
   - `SWITCH_ACQUIRING_BASE_URL`, `SWITCH_WAY_POS_BASE_URL` ;
   - `SWITCH_ISSUING_BASE_URL` ;
   - `SWITCH_DMAS_BASE_URL`, `SWITCH_SMS_BASE_URL`, `SWITCH_SWAM_BASE_URL`, `SWITCH_VISA_ONLINE_BASE_URL` ;
   - `SWITCH_DMCS_BASE_URL`, `SWITCH_SWAM_LIS_BASE_URL`, `SWITCH_VISA_BASE2_BASE_URL` ;
   - `SWITCH_THREE_DS_BASE_URL`.
3. Fournir des comptes Switch distincts de ceux de SwitchLab.
4. Fournir un Maker et un Checker distincts pour les workflows disponibles.
5. Ne jamais substituer une donnée fictive à une API absente.

## 4. Contrôles communs

| ID | Route/écran | Action | Rôle | État | Résultat attendu |
|---|---|---|---|---|---|
| SW-COM-001 | `/login` | Connexion valide | utilisateur Switch | `CONDITIONNEL` | Redirection `/dashboard`, identité Switch et menus membre uniquement. |
| SW-COM-002 | `/login` | Connexion invalide | anonyme | `CONDITIONNEL` | Refus explicite et aucun jeton conservé. |
| SW-COM-003 | barre supérieure | Changer FR/EN/ES | connecté | `TESTABLE` | Langue changée sans bascule produit. |
| SW-COM-004 | barre supérieure | Changer le thème | connecté | `TESTABLE` | Thème appliqué sans perte de session. |
| SW-COM-005 | barre supérieure | Changer la couleur primaire | connecté | `TESTABLE` | Couleur appliquée et interface lisible. |
| SW-COM-006 | barre supérieure | Déconnexion | connecté | `TESTABLE` | Session supprimée, retour à `/login`. |
| SW-COM-008 | URL dynamique non accordée | Tenter un module hors droits | utilisateur limité | `CONDITIONNEL` | Redirection `/forbidden`. |
| SW-COM-009 | menu module | Sélectionner un module membre | connecté | `LECTURE` | Premier écran autorisé du module ouvert. |

## 5. Tableau de bord, transactions et exploitation

| ID | Route | Action | État | Résultat attendu |
|---|---|---|---|---|
| SW-DASH-001 | `/dashboard` | Ouvrir le tableau de bord | `BLOQUÉ` | L'état de fondation est affiché ; aucun faux indicateur opérationnel. |
| SW-TXN-001 | `/product/transactions` | Ouvrir le journal transactionnel | `BLOQUÉ` | Aucune transaction fictive ; SW-007 affiché comme dépendance. |
| SW-OPS-001 | `/product/operations` | Charger les connexions membre | `CONDITIONNEL` | Interfaces réelles seulement ; liste vide si registre absent. |
| SW-OPS-002 | `/product/operations` | Vérifier une connexion | `LECTURE` | État retourné par le registre membre, jamais simulé localement. |

## 6. Registre d'interfaces

Route : `/product/interfaces`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SW-INT-001 | Charger les capacités du registre | `CONDITIONNEL` | `registryAvailable`, `makerCheckerAvailable` et cause exacte. |
| SW-INT-002 | Consulter les interfaces | `CONDITIONNEL` | Code, banque, réseau, protocole, format, host/port, priorité, failover, statuts et références logiques. |
| SW-INT-003 | Vérifier une liste vide | `TESTABLE` | Message « aucune interface réelle », aucune fixture. |
| SW-INT-004 | Renseigner le formulaire | `TESTABLE` | Les champs restent locaux tant qu'aucune soumission n'est autorisée. |
| SW-INT-005 | Renseigner `vault://` et `hsm://` | `TESTABLE` | Références affichées sans résolution dans le navigateur. |
| SW-INT-006 | Soumettre au Maker/Checker | `BLOQUÉ` | Bouton désactivé avec SW-001/SW-002/SW-005 ouverts. |
| SW-INT-007 | Activer/désactiver/basculer une interface | `BLOQUÉ` | Actions retournées visibles mais inactives tant que le backend ne les autorise pas. |

## 7. Acquisition POS et e-commerce

Route : `/product/acquiring`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SW-ACQ-001 | Actualiser le cockpit | `CONDITIONNEL` | États réels de `sg-acquiring` et `sg-way-pos-server`, corrélation renouvelée. |
| SW-ACQ-002 | Vérifier une URL non configurée | `TESTABLE` | État `UNKNOWN` et limitation explicite. |
| SW-ACQ-003 | Vérifier un service inaccessible | `CONDITIONNEL` | État `DOWN`, sans faux catalogue. |
| SW-ACQ-004 | Consulter Produits d'acceptation | `BLOQUÉ` | Catalogue GET absent, SW-004. |
| SW-ACQ-005 | Consulter Commerçants/points de vente | `BLOQUÉ` | Catalogue GET absent. |
| SW-ACQ-006 | Consulter Contrats commerçants | `BLOQUÉ` | Catalogue GET absent. |
| SW-ACQ-007 | Consulter Terminaux/affectations | `BLOQUÉ` | Vue consolidée absente malgré certaines API ServerPOS. |
| SW-ACQ-008 | Consulter Boutiques e-commerce | `BLOQUÉ` | Catalogue GET absent. |
| SW-ACQ-009 | Consulter Profils/routage | `BLOQUÉ` | Catalogue GET et contrat unifié absents. |
| SW-ACQ-010 | Lancer une transaction POS/e-commerce | `BLOQUÉ` | Aucun PAN accepté ; résolveur carte requis, SW-006. |
| SW-ACQ-011 | Consulter 3DS et preuves | `BLOQUÉ` | `threeDS=false` ou catalogue de preuves absent. |
| SW-ACQ-012 | Consulter événements/audit | `BLOQUÉ` | API de consultation absente. |

## 8. Issuing membre

Route : `/product/issuing`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SW-ISS-001 | Actualiser le domaine | `CONDITIONNEL` | Santé/capacités de `sg-card-issuing`, jamais du simulateur. |
| SW-ISS-002 | Consulter Produits cartes | `BLOQUÉ` | Commandes backend détectées mais aucun catalogue GET. |
| SW-ISS-003 | Consulter Contrats porteurs | `BLOQUÉ` | Aucun objet fictif. |
| SW-ISS-004 | Consulter Cartes virtuelles/physiques | `BLOQUÉ` | Aucun PAN ou détail carte affiché. |
| SW-ISS-005 | Consulter Interfaces issuing | `BLOQUÉ` | Registre membre transverse absent. |
| SW-ISS-006 | Lancer autorisation/pré-clearing | `BLOQUÉ` | Référence carte serveur requise ; SW-006. |

## 9. Réseaux temps réel

Route : `/product/networks`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SW-NET-001 | Actualiser le domaine | `CONDITIONNEL` | DMAS membre, SMS acquéreur, SWAM acquéreur et Visa Online membre sondés. |
| SW-NET-002 | Vérifier l'absence de modules issuer simulés | `TESTABLE` | Aucun `sg-mc-sms-issuer`, `sg-swam-issuer` ou simulateur appelé. |
| SW-NET-003 | Consulter sessions réseau | `BLOQUÉ` | Contrat membre unifié absent. |
| SW-NET-004 | Consulter l'état des clés | `BLOQUÉ` | Aucune clé brute relayée ; vue KCV/référence HSM requise. |
| SW-NET-005 | Lancer le routage temps réel | `BLOQUÉ` | Résolveur de référence carte absent. |
| SW-NET-006 | Consulter le journal réseau | `BLOQUÉ` | Journal consolidé absent, SW-007. |

## 10. Clearing, rapprochement et settlement

Route : `/product/clearing`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SW-CLR-001 | Actualiser le domaine | `CONDITIONNEL` | `sg-dmcs-acquirer`, `sg-swam-lis-member` et `sg-visa-base2-member` uniquement. |
| SW-CLR-002 | Vérifier Mastercard Clearing côté acquéreur | `TESTABLE` | Module affiché comme `SG_DMCS_ACQUIRER`, jamais `sg-dmcs-issuer`. |
| SW-CLR-003 | Consulter les fichiers | `BLOQUÉ` | Catalogues hétérogènes non encore normalisés. |
| SW-CLR-004 | Lancer une clôture EOD | `BLOQUÉ` | Maker/Checker transverse non raccordé. |
| SW-CLR-005 | Exécuter le rapprochement | `BLOQUÉ` | Moteur/API consolidé absent, SW-008. |
| SW-CLR-006 | Calculer/valider le settlement | `BLOQUÉ` | API de calcul, validation et comptabilisation absente. |
| SW-CLR-007 | Consulter/traiter un litige | `BLOQUÉ` | Listes, timeline et décisions Maker/Checker absentes. |

## 11. E-commerce et 3DS membre

Route : `/product/ecommerce`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SW-ECO-001 | Actualiser le domaine | `CONDITIONNEL` | `sg-3ds-member` et Visa Online membre sondés. |
| SW-ECO-002 | Vérifier le rôle de `sg-3ds-member` | `LECTURE` | Libellé « ACS et 3DS Server membre ». |
| SW-ECO-003 | Consulter une authentification par liste | `BLOQUÉ` | Aucune liste globale, SW-009. |
| SW-ECO-004 | Consulter les preuves ACS/3DS Server | `BLOQUÉ` | Registre assaini absent. |
| SW-ECO-005 | Lancer une autorisation e-commerce | `BLOQUÉ` | Aucun PAN dans le frontend ; résolveur serveur requis. |
| SW-ECO-006 | Afficher ou saisir un OTP | `NON EXPOSÉ` | Aucun OTP dans le frontend membre. |

## 12. Industrialisation

Route : `/product/industrialization`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SW-IND-001 | Actualiser la disponibilité des modules membre | `CONDITIONNEL` | Neuf modules sondés avec `UP/DOWN/UNKNOWN`. |
| SW-IND-002 | Consulter Déploiements/Licences | `CONDITIONNEL` | Navigation vers les écrans communs via le BFF Switch. |
| SW-IND-003 | Consulter métriques/alertes | `BLOQUÉ` | Santé seulement ; métriques et alertes durables absentes. |
| SW-IND-004 | Consulter l'audit produit | `BLOQUÉ` | Audit consolidé absent. |
| SW-IND-005 | Sauvegarder/restaurer | `BLOQUÉ` | API contrôlée Maker/Checker absente, SW-010. |

## 13. Workspaces dynamiques membre

Route : `/modules/:moduleCode/:screen`.

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| SW-DYN-001 | Ouvrir un écran `MODULE_WORKSPACE` autorisé | `LECTURE` | Contexte module/écran correct et message de fondation. |
| SW-DYN-002 | Ouvrir un écran `VISA_WORKSPACE` | `LECTURE` | Flux Visa membre correspondant, aucune donnée de carte. |
| SW-DYN-003 | Ouvrir un écran `CLEARING_*` | `LECTURE` | Contexte clearing membre, message de fondation sans fausse transaction. |
| SW-DYN-004 | Forcer un `componentKey` non enregistré | `CONDITIONNEL` | Écran indisponible fail-closed. |

## 14. Administration et workflow

| ID | Action | Permission | État | Résultat attendu |
|---|---|---|---|---|
| SW-ADM-001 | Consulter les utilisateurs | `USER_MANAGE` | `CONDITIONNEL` | Liste issue du backend Switch. |
| SW-ADM-002 | Créer un utilisateur | `USER_MANAGE` | `CONDITIONNEL` | Compte créé uniquement dans le produit Switch. |
| SW-ADM-003 | Modifier email/rôle | `USER_MANAGE` | `CONDITIONNEL` | Login non modifiable ; données actualisées. |
| SW-ADM-004 | Activer/désactiver | `USER_MANAGE` | `CONDITIONNEL` | État confirmé par le backend. |
| SW-ADM-005 | Consulter rôles/permissions | `ROLE_MANAGE` | `CONDITIONNEL` | Catalogue Switch uniquement. |
| SW-WF-001 | Ouvrir Mes opérations | connecté | `BLOQUÉ` | Aucune demande fictive si l'API manque. |
| SW-WF-002 | Ouvrir Mes validations | checker | `BLOQUÉ` | Aucune approbation locale ou auto-validation. |
| SW-WF-003 | Soumettre/approuver/rejeter une opération générique | Maker/Checker | `NON EXPOSÉ` | API générique absente, SW-002/SW-005. |

## 15. Déploiements et licences

| ID | Action | Permission | État | Résultat attendu |
|---|---|---|---|---|
| SW-DEP-001 | Créer un client | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Client membre enregistré. |
| SW-DEP-002 | Créer un environnement | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Seuls les modules membre autorisés sont disponibles. |
| SW-DEP-003 | Choisir OS et shell | `DEPLOYMENT_PREPARE` | `TESTABLE` | Liste shell cohérente avec WINDOWS/LINUX. |
| SW-DEP-004 | Référencer le secret DB | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | `secret://...`, jamais mot de passe clair. |
| SW-DEP-005 | Sélectionner le bundle membre | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Bundle membre visible ; bundle simulators non utilisé pour les modules Switch. |
| SW-DEP-006 | Lancer le preflight | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Résultat détaillé et corrélé. |
| SW-DEP-007 | Préparer une licence | `DEPLOYMENT_PREPARE` | `CONDITIONNEL` | Licence en attente. |
| SW-DEP-008 | Approuver une licence | `DEPLOYMENT_APPROVE` | `CONDITIONNEL` | Checker distinct selon la règle backend. |
| SW-DEP-009 | Demander une exécution | `DEPLOYMENT_EXECUTE` | `CONDITIONNEL` | Demande en attente d'approbation. |
| SW-DEP-010 | Approuver une exécution | `DEPLOYMENT_APPROVE` | `CONDITIONNEL` | Statut et identité checker actualisés. |

## 16. Fiche de résultat à dupliquer

```text
ID du cas :
Date/heure :
Testeur :
Environnement :
Version frontend/BFF :
Compte et rôle (sans mot de passe) :
Modules membre démarrés :
Données/références utilisées (sans valeur sensible) :
Résultat : RÉUSSI | ÉCHEC | BLOQUÉ ENVIRONNEMENT | NON EXÉCUTÉ
Résultat obtenu :
Corrélation :
Preuve : capture/rapport/référence d'artefact
Anomalie associée :
```

## 17. Critères de fin de recette Switch

- Tous les cas `TESTABLE` ont un résultat et une preuve.
- Chaque cas `CONDITIONNEL` est exécuté ou possède une cause environnement précise.
- Tous les cas `BLOQUÉ` restent effectivement fermés.
- Aucun secret ou donnée monétique sensible n'apparaît dans l'UI, les traces ou les preuves.
- Les dépendances SW-001, SW-002 et SW-004 à SW-011 ne sont pas présentées comme validées tant que les API backend ne sont pas disponibles.
