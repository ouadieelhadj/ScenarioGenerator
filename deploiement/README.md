# Déploiement ScenarioGenerator

Les ressources sont organisées par module. Les scripts utilisés par plusieurs
modules sont placés dans `common`.

```text
deploiement/
├── common/
│   ├── database/   # création, migrations et données communes
│   └── runtime/    # démarrage et E2E transverses
├── frontend/       # Angular, guide et E2E navigateur
├── swam/           # migrations et E2E SWAM SID/LIS
└── README.md
```

## Base commune

Création Windows :

```bat
deploiement\common\database\1_create-data_base.bat
```

Installation complète depuis Git Bash :

```bash
bash deploiement/common/database/install-full-db.sh
```

### Transférer la base de référence vers un autre poste

Le fichier de sauvegarde contient potentiellement des données sensibles. Il ne
doit jamais être ajouté à Git. Le transférer par un canal sécurisé.

Sur le poste qui possède la base de référence :

```bash
export DB_PASSWORD="<mot-de-passe-postgresql-source>"
bash deploiement/common/database/export-reference-db.sh
```

Le script crée un fichier `.dump` sous `runtime/database-transfer`. Copier ce
fichier sur le poste cible.

Sur le poste cible, après avoir arrêté les services applicatifs :

```bash
export DB_PASSWORD="<mot-de-passe-postgresql-cible>"
bash deploiement/common/database/replace-db-from-dump.sh \
  "/chemin/vers/scenariogenerator-reference-AAAAMMJJ-HHMMSS.dump"
```

Avant toute suppression, le script :

1. demande de saisir exactement `REMPLACER scenariogenerator` ;
2. sauvegarde la base cible dans `runtime/database-backups` ;
3. ferme ses connexions, la recrée et restaure le dump ;
4. vérifie que les tables ont été restaurées.

Les variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `POSTGRES_HOME`,
`DB_TRANSFER_DIR` et `DB_BACKUP_DIR` permettent d'adapter la procédure.

Création autonome :

```bash
bash deploiement/common/database/run-create-db.sh
```

Les scripts `build-create-db.sh` et `generate-standalone-sql.sh` régénèrent les
fichiers SQL autonomes dans leur propre répertoire.

## Runtime commun

Toutes les variables globales sont centralisées dans :

```text
deploiement/common/runtime/platform-env.sh
```

Le lanceur calcule automatiquement `ROOT` depuis l'emplacement du dépôt.

```bash
bash deploiement/common/runtime/start-platform.sh start
bash deploiement/common/runtime/start-platform.sh status
bash deploiement/common/runtime/start-platform.sh stop
```

Pour surcharger les chemins sur un autre PC :

```bash
export ROOT=/f/ScenarioGenerator
export JAVA_HOME_DIR=/f/MoneyCore/jdk-21.0.11
export MAVEN_HOME=/f/MoneyCore/apache-maven-3.9.9
export NODE_HOME=/f/MoneyCore/nodejs
export POSTGRES_HOME=/f/MoneyCore/PostgreSQL/18
bash deploiement/common/runtime/start-platform.sh start
```

Les anciens scripts Windows restent disponibles :

```bat
deploiement\common\runtime\2_start-services.bat
deploiement\common\runtime\3_scenario-e2e.bat
```

## SWAM

E2E SID :

```bash
bash deploiement/swam/swam-e2e.sh
```

E2E LIS bilatéral :

```bash
export SWAM_E2E_KEK_CLEAR="0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
bash deploiement/swam/swam-lis-e2e.sh
```

Les migrations et cartes de test propres à SWAM sont regroupées dans
`deploiement/swam`.

## Frontend

Démarrage :

```bash
bash deploiement/frontend/start-frontend.sh
```

E2E navigateur Playwright :

```bash
bash deploiement/frontend/frontend-e2e.sh
```

Guide :

```text
deploiement/frontend/GUIDE_TEST_PORTAIL_MODULAIRE.md
```

## Prérequis locaux

- PostgreSQL 18 : `D:\MoneyCore\PostgreSQL\18`
- JDK 21 : `D:\MoneyCore\jdk-21.0.11`
- Node.js : `D:\MoneyCore\nodejs`
- base et mots de passe locaux tels que configurés dans les scripts

Ces valeurs doivent être externalisées pour les environnements de recette et de
production.
