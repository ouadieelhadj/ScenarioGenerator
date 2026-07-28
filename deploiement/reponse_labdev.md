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
