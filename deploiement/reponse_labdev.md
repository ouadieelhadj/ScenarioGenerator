# Réponse LAB/DEV au résultat de recette

Date : 2026-07-28

Rapport analysé : `deploiement/resultat_recette.md`

Branche de correction : `codex/portail-rbac-maker-checker`

## Anomalie analysée

RECETTE a signalé l'échec suivant dans `replace-db-from-dump.sh` :

```text
ERROR: syntax error at or near ":"
SELECT 1 FROM pg_database WHERE datname = :'db_name'
```

Le diagnostic RECETTE est confirmé. La substitution des variables `psql` n'est
pas appliquée dans la commande `--command` utilisée par le script.

## Correction LAB/DEV

La correction retenue utilise directement `DB_NAME` après sa validation stricte
par l'expression :

```text
^[A-Za-z0-9_]+$
```

Les opérations corrigées sont :

- contrôle d'existence de la base ;
- fermeture des connexions ;
- suppression de la base cible ;
- recréation de la base ;
- restauration du dump ;
- contrôle final du nombre de tables.

La correction conserve les protections existantes :

- vérification/démarrage de PostgreSQL avant restauration ;
- confirmation exacte `REMPLACER <DB_NAME>` ;
- sauvegarde de la base existante ;
- arrêt immédiat avant toute suppression en cas d'erreur.

## Validation LAB/DEV

La restauration complète a été exécutée sur une base temporaire dédiée :

```text
DB_NAME=sg_restore_validation
```

Résultat :

```text
[OK] PostgreSQL écoute sur localhost:5432
[OK] Connexion PostgreSQL authentifiée
DROP DATABASE
CREATE DATABASE
sg_restore_validation: 78 tables
[OK] Base 'sg_restore_validation' remplacée depuis le dump
```

La base temporaire a ensuite été supprimée. La base LAB/DEV
`scenariogenerator` n'a pas été remplacée par ce test.

## Revalidation demandée à RECETTE

Après récupération du commit de correction :

```bash
cd /f/MoneyCore/ScenarioGenerator
git fetch origin
git switch codex/portail-rbac-maker-checker
git pull --ff-only origin codex/portail-rbac-maker-checker
source platform-path.sh
export DB_PASSWORD="<mot-de-passe-postgresql-recette>"
```

Vérifier/démarrer PostgreSQL :

```bash
bash deploiement/common/database/check-postgres.sh --start
```

Relancer ensuite la restauration avec le dump transféré :

```bash
bash deploiement/common/database/replace-db-from-dump.sh \
  "<chemin-vers-le-dump>"
```

Résultat attendu :

```text
[OK] PostgreSQL écoute sur localhost:5432
[OK] Connexion PostgreSQL authentifiée
DROP DATABASE
CREATE DATABASE
scenariogenerator: <nombre> tables
[OK] Base 'scenariogenerator' remplacée depuis : <dump>
```

Après réussite, poursuivre la chaîne SWAM décrite dans
`COMMENT_REPRENDRE_NOUVELLE_SESSION.md`.
## 2026-07-28 — Reconstruction SQL sans dump

L'anomalie de droits après restauration a conduit à remplacer le dump comme
méthode principale de préparation de RECETTE.

La commande officielle est désormais :

```bash
source deploiement/common/runtime/platform-env.sh
export DB_PASSWORD="<mot-de-passe-postgresql-recette>"
bash deploiement/common/database/install-full-db.sh
```

Le script recrée la base depuis les migrations versionnées, charge les données
de paramétrage SWAM, rejoue les droits et exécute des contrôles bloquants.

Validation LAB/DEV effectuée sur une seconde base isolée
`scenariogeneratorqualif` :

- reconstruction complète : OK ;
- tables publiques : 76 ;
- interfaces SWAM : 2 ;
- cartes SWAM de recette : 3 ;
- modules portail : 4 ;
- droits `swam_issuer_user`, `swam_acquirer_user`,
  `swam_lis_member_user` et `swam_lis_switch_user` : OK.

La base principale `scenariogenerator` n'a pas été modifiée pendant ce test.
## 2026-07-28 — Séparation JPA par propriétaire métier

L'anomalie `missing table [mc_dmas_cards]` au démarrage de SWAM Issuer est
corrigée à la source.

Cause : les applications scannaient globalement toutes les entités et tous les
repositories de `sg-common`. Hibernate SWAM validait donc aussi les tables
Mastercard et DMAS.

Correction :

- suppression des scans JPA globaux dans toutes les applications ;
- configurations explicites dans
  `com.staging.sg.common.persistence`, activées par
  `sg.persistence.module` ;
- séparation SWAM Issuer/Membre, LIS Membre/Switch, DMCS, Mastercard DMAS,
  Mastercard SMS et orchestrateur ;
- aucun filtrage par préfixe de table ;
- validation Hibernate conservée sur les seules entités appartenant au module.

Validations LAB/DEV :

- compilation des 14 modules : `BUILD SUCCESS` ;
- tests Maven complets : `BUILD SUCCESS` ;
- tests d'architecture de séparation : 4/4 réussis ;
- contexte orchestrateur : démarrage réussi avec 18 repositories explicitement
  autorisés ;
- SWAM Issuer contre `scenariogeneratorqualif` sans table `mc_dmas_*` :
  initialisation JPA réussie, 6 repositories seulement ;
- démarrages SWAM Issuer, Membre, LIS Membre et LIS Switch : réussis ;
- échange de clés et achats bilatéraux : réussis ;
- clearing LIS : chargebacks et représentation présents ;
- comptabilisation : 22 écritures côté membre et 22 côté switch, soldes nuls.

La matrice est documentée dans :

`deploiement/common/PERSISTENCE_MODULE_OWNERSHIP.md`.

## 2026-07-28 — Cartes SWAM séparées entre membre et switch

L'anomalie RECETTE `permission denied for table swam_cards` n'est pas corrigée
par un droit `UPDATE` supplémentaire sur la table partagée. Cette table ne
respectait pas la propriété métier attendue.

Décision d'architecture appliquée :

- `issuer_swam_cards` appartient exclusivement à `swam_issuer_user` ;
- `acquirer_swam_cards` appartient exclusivement à `swam_acquirer_user` ;
- `sg-swam-issuer` utilise `SwamIssuerCardRepository` ;
- `sg-swam-acquirer` utilise `SwamAcquirerCardRepository` ;
- l'ancienne table partagée `swam_cards` est supprimée par la migration ;
- aucun des deux rôles ne peut lire ou modifier la table de l'autre.

La migration
`deploiement/swam/migration_v1.5.0_swam_cards_by_owner.sql` conserve les
anciennes cartes lors d'une mise à niveau, puis sépare définitivement les deux
jeux. Sur une reconstruction neuve, `swam_cartes_test.sql` installe trois cartes
switch et trois cartes membre avec des PAN distincts.

Validations LAB/DEV sur `scenariogeneratorqualif` :

- reconstruction complète depuis les scripts SQL : OK, 77 tables ;
- `issuer_swam_cards` : 3 cartes, propriétaire `swam_issuer_user` ;
- `acquirer_swam_cards` : 3 cartes, propriétaire `swam_acquirer_user` ;
- table `swam_cards` : absente ;
- droits croisés de lecture : absents ;
- bootstrap KEK des deux côtés : KCV `F6EE59` ;
- sign-on et échange des clés de session : OK ;
- cinq achats membre vers issuer : approuvés ;
- cinq achats issuer vers membre : approuvés ;
- solde issuer : `100000 -> 94985` dans `issuer_swam_cards` uniquement ;
- solde membre : `100000 -> 94985` dans `acquirer_swam_cards` uniquement ;
- PAN membre envoyé à l'issuer : refus DE39 `114` ;
- PAN issuer envoyé au membre : refus DE39 `114` ;
- tests Maven complets des 14 modules : `BUILD SUCCESS`.

Après récupération du commit, RECETTE doit arrêter les anciens services,
reconstruire la base avec `install-full-db.sh`, puis reprendre les scripts SWAM
`01` à `04`. Les contrôles de reconstruction sont maintenant bloquants si la
table partagée existe encore ou si un rôle accède aux cartes de l'autre côté.
