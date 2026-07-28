# Résultat de recette — ScenarioGenerator

Poste : RECETTE
Branche : codex/portail-rbac-maker-checker
Date : 2026-07-28

## Résumé de la reprise

| Étape | Statut |
|-------|--------|
| Récupération Git (dernier commit `8e568f5`) | OK |
| Détection environnement (`detect-env.sh`) | OK — JDK 26 retenu, Node, Maven, PostgreSQL |
| Configuration locale (`platform.env` + `platform-path.sh`) | OK |
| Build + tests complets `mvn verify` sous JDK 26 | OK — `BUILD SUCCESS` |
| Démarrage PostgreSQL (`check-postgres.sh --start`, PGDATA portable) | OK — écoute + connexion authentifiée |
| Restauration du dump (`replace-db-from-dump.sh`) | **ÉCHEC — anomalie ci-dessous** |
| Chaîne SWAM | Non atteinte (bloquée par la restauration) |

## Historique des correctifs validés en RECETTE

- `4f7bca6` — Mockito 5.23.0 / Byte Buddy 1.18.7 : corrige l'échec `mvn verify`
  sous JDK 26 (`Mockito cannot mock this class`). **Vérifié OK** : build et tests
  complets réussis sous JDK 26.0.1.
- `8e568f5` — vérification et démarrage automatique de PostgreSQL avant la
  restauration (`check-postgres.sh`, via `POSTGRES_SERVICE_NAME` ou `PGDATA`).
  **Vérifié OK** : PostgreSQL portable démarré automatiquement avec
  `PGDATA=/f/MoneyCore/pgsql/data`, `[OK] écoute` + `[OK] connexion authentifiée`.

## Anomalie ouverte

### replace-db-from-dump.sh — variables psql `:'db_name'` non substituées

Commit testé : `8e568f5`

**Symptôme**

À l'exécution de `replace-db-from-dump.sh`, après la confirmation
`REMPLACER scenariogenerator`, échec immédiat :

```
ERROR:  syntax error at or near ":"
LIGNE 1 : SELECT 1 FROM pg_database WHERE datname = :'db_name'
```

Aucune donnée touchée : l'échec survient sur la première requête (test
d'existence de la base), avant la sauvegarde de sécurité et avant tout DROP.
La base de recette est restée intacte.

**Cause**

La substitution de variable psql `:'db_name'` n'est pas appliquée :

- Lignes 52-54 : `psql` appelé avec `--command="... :'db_name'"` et
  `--set=db_name=...` n'effectue pas la substitution ; le littéral `:'db_name'`
  est transmis au serveur.
- Lignes 66-74 : même motif dans le heredoc quoté `<<'SQL'`, combiné à `\gexec`
  pour `DROP DATABASE` / `CREATE DATABASE` — même risque à l'étape suivante.

**Correctifs possibles (au choix de LAB/DEV)**

Option A — injecter directement `$DB_NAME` (déjà validé `^[A-Za-z0-9_]+$` en
amont du script), sans variable psql :

```
--command="SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'"
```

et dans le bloc SQL, remplacer `:'db_name'` par `$DB_NAME` (heredoc non quoté
`<<SQL`, ou construction de la chaîne avant l'appel).

Option B — conserver `:'db_name'` mais garantir un contexte où psql applique la
substitution (ex. `-v db_name="$DB_NAME"` et vérifier le mode d'entrée), à
tester.

Le contrôle `DB_NAME =~ ^[A-Za-z0-9_]+$` présent en début de script rend
l'option A sûre vis-à-vis de l'injection SQL.

**Point à vérifier au passage**

Ligne 84 : le comptage final des tables utilise `--command` sans variable psql,
donc a priori non affecté. À confirmer par LAB/DEV pour éviter un aller-retour
supplémentaire.

## Note

Aucune correction n'a été développée en RECETTE. Les anomalies sont remontées à
LAB/DEV pour correction et re-livraison, conformément à la procédure.
