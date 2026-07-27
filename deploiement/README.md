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
