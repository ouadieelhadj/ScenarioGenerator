# Cahier de recette transverse — FuturPayment SwitchLab et FuturPayment Switch

Version : 1.0
Date de référence : 6 août 2026
Produits : `FuturPayment SwitchLab` et `FuturPayment Switch`

## 1. Objet

Ce document regroupe exclusivement les contrôles qui nécessitent de comparer
les deux produits ou d'observer un même parcours depuis les deux frontends.
Les tests propres à un seul produit restent dans :

- `tests/frontend/RECETTE_FRONTEND_SWITCHLAB.md` ;
- `tests/frontend/RECETTE_FRONTEND_SWITCH.md`.

Les résultats déjà exécutés sont consignés dans la section 4.1. Un cas bloqué
par une API absente ne doit jamais être déclaré réussi à partir du seul
affichage des deux cockpits.

## 2. États

| État | Signification |
|---|---|
| `TESTABLE` | Vérifiable avec les deux builds ou les quatre processus frontend/BFF. |
| `CONDITIONNEL` | Exige comptes, backends, données ou environnements de recette autorisés. |
| `BLOQUÉ` | Le parcours transverse ne peut pas être achevé à cause d'une dépendance connue. |

## 3. Topologie et prérequis communs

| Produit | Frontend | BFF | Frontière attendue |
|---|---:|---:|---|
| SwitchLab | `http://localhost:4210` | `http://localhost:8090` | simulateurs uniquement |
| Switch | `http://localhost:4220` | `http://localhost:8091` | modules membre uniquement |

Prérequis :

1. Construire ou démarrer les deux applications depuis le même état Git.
2. Utiliser deux contextes de navigateur séparés afin de ne pas partager les
   jetons, cookies ou stockages locaux.
3. Fournir au minimum un compte SwitchLab et un compte Switch distincts.
4. Utiliser uniquement les données et références de certification autorisées.
5. Ne jamais copier PAN, PIN, CVC, OTP, clé ou secret dans les preuves.
6. Relever pour chaque parcours les corrélations visibles des deux côtés.

## 4. Builds et exécution simultanée

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| TR-ARC-001 | Construire `futurpayment-switch` et inspecter ses chunks | `TESTABLE` | Build réussi sans composant `switchlab-*`. |
| TR-ARC-002 | Construire `futurpayment-switchlab` et inspecter ses chunks | `TESTABLE` | Build réussi sans `switch-acquiring`, `switch-interfaces` ou `switch-member-domain`. |
| TR-RUN-001 | Démarrer simultanément les deux frontends et les deux BFF | `TESTABLE` | SwitchLab répond sur 4210/8090 et Switch sur 4220/8091, sans conflit de port ni redirection croisée. |
| TR-BFF-001 | Examiner les appels réseau des deux frontends | `TESTABLE` | SwitchLab appelle exclusivement le BFF 8090 et Switch exclusivement le BFF 8091 ; aucun jeton n'est envoyé au BFF opposé. |

### 4.1. Résultats exécutés le 7 août 2026

| ID | Résultat | Preuve assainie |
|---|---|---|
| TR-ARC-001 | `RÉUSSI` | `npm.cmd run build:switch -- --configuration development` terminé en 10,091 s ; artefact `dist/futurpayment-switch` généré ; recherches ciblées dans les JS/source maps et noms de fichiers sans composant SwitchLab. |
| TR-ARC-002 | `RÉUSSI` | `npm.cmd run build:switchlab -- --configuration development` terminé en 7,202 s ; artefact `dist/futurpayment-switchlab` généré ; recherches ciblées dans les JS/source maps et noms de fichiers sans composant Switch membre, `visa-workspace` ni `clearing-workspace`. |

Les commandes ont été exécutées sous Git Bash avec un seul worker Angular et
une limite Node de 1 536 Mio. Aucun test fonctionnel ou connecté n'a été lancé
pendant ces deux contrôles.

## 5. Identité, routes et navigation croisées

| ID | Action | État | Résultat attendu |
|---|---|---|---|
| TR-AUTH-001 | Utiliser le compte Switch sur la page de connexion SwitchLab | `CONDITIONNEL` | Authentification refusée ; aucune session SwitchLab créée. |
| TR-AUTH-002 | Utiliser le compte SwitchLab sur la page de connexion Switch | `CONDITIONNEL` | Authentification refusée ; aucune session Switch créée. |
| TR-ROUTE-001 | Depuis SwitchLab, forcer `/product/acquiring` ou un composant membre | `TESTABLE` | Route refusée ou redirigée ; aucun composant membre chargé. |
| TR-ROUTE-002 | Depuis Switch, forcer `/lab/pos` | `TESTABLE` | Route refusée ou redirigée ; aucun composant SwitchLab chargé. |
| TR-NAV-001 | Comparer les menus après connexion | `TESTABLE` | SwitchLab présente les espaces LAB sans modules membre ; Switch présente les espaces membre sans `LAB_SIMULATORS`. |
| TR-DATA-001 | Comparer utilisateurs et rôles dans les deux produits | `CONDITIONNEL` | Comptes, rôles et permissions restent propres au produit ; aucune donnée d'administration ne traverse la frontière. |
| TR-DEP-001 | Comparer licences, environnements, déploiements et exécutions | `CONDITIONNEL` | Chaque objet n'est visible et actionnable que dans son produit propriétaire. |

## 6. Parcours métier de bout en bout

Ces parcours commencent côté SwitchLab, qui pilote les simulateurs, et se
terminent côté Switch, qui représente les modules membre. Le même identifiant
de corrélation, ou la paire STAN/RRN lorsqu'elle est autorisée, doit relier les
deux observations.

| ID | Parcours transverse | État | Résultat attendu ou blocage actuel |
|---|---|---|---|
| TR-POS-001 | Envoyer une transaction POS depuis SwitchLab puis la retrouver dans Acquisition/journal Switch | `BLOQUÉ` | L'émission SwitchLab est conditionnelle (`SL-POS-002`), mais le journal Switch est bloqué par SW-007 et l'action d'acquisition par SW-006. |
| TR-ONL-001 | Envoyer un scénario Online DMAS/SMS/Visa/SWAM depuis SwitchLab puis l'observer dans Réseaux Switch | `BLOQUÉ` | L'echo DMAS seul est conditionnel ; les routes financières sûres et le journal consolidé Switch manquent. |
| TR-CLR-001 | Charger un fichier ou lancer EOD côté SwitchLab puis observer clearing, rapprochement et preuve côté Switch | `BLOQUÉ` | Certains uploads/EOD sont conditionnels, mais les adaptateurs d'artefacts et le moteur/API de rapprochement consolidé manquent. |
| TR-ECO-001 | Exécuter authentification 3DS et autorisation e-commerce côté SwitchLab puis retrouver preuve et autorisation côté Switch | `BLOQUÉ` | Le résolveur serveur de carte, le parcours sûr de challenge et les API de consultation consolidée manquent. |

## 7. Procédure commune d'exécution

Pour chaque cas :

1. Ouvrir SwitchLab et Switch dans deux contextes de navigateur séparés.
2. Noter versions frontend/BFF, comptes et rôles sans valeur secrète.
3. Déclencher l'action depuis le produit indiqué.
4. Observer le résultat dans l'autre produit lorsqu'il s'agit d'un parcours
   métier, ou vérifier son refus lorsqu'il s'agit d'un contrôle de séparation.
5. Comparer statut, heure, corrélation et propriétaire de la donnée.
6. Conserver uniquement une preuve assainie.
7. Noter `BLOQUÉ ENVIRONNEMENT` ou `BLOQUÉ PRODUIT` lorsque le résultat ne peut
   pas être établi ; ne jamais le convertir en réussite supposée.

## 8. Fiche de résultat à dupliquer

```text
Cas transverse :
Date/heure :
Versions SwitchLab frontend/BFF :
Versions Switch frontend/BFF :
Compte/rôle SwitchLab (sans mot de passe) :
Compte/rôle Switch (sans mot de passe) :
Services démarrés :
Résultat : RÉUSSI | ÉCHEC | BLOQUÉ ENVIRONNEMENT | BLOQUÉ PRODUIT | NON EXÉCUTÉ
Résultat côté SwitchLab :
Résultat côté Switch :
Corrélation/STAN/RRN assaini :
Preuve : capture/rapport/référence d'artefact
Anomalie ou dépendance associée :
```

## 9. Critères de fin de recette transverse

- Les quatre contrôles de build/exécution ont un résultat et une preuve.
- Les contrôles d'identité, routes, navigation et données sont vérifiés dans
  les deux directions.
- Aucun compte, jeton, composant, licence, environnement ou exécution ne
  traverse la frontière produit sans autorisation explicite.
- Chaque parcours métier est exécuté de bout en bout ou conserve un blocage
  précis lié à une dépendance identifiée.
- Aucun parcours n'est déclaré réussi sans observation corrélée dans les deux
  produits.
- Aucune donnée monétique sensible ni aucun secret n'apparaît dans les preuves.
