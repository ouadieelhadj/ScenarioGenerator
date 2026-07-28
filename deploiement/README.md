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

### Promouvoir une base de test de LAB/DEV vers RECETTE

Le fichier de sauvegarde contient potentiellement des données sensibles. Il ne
doit jamais être ajouté à Git. Avant l'export, vérifier qu'il contient uniquement
des données de test autorisées et assainies, puis le transférer par un canal
sécurisé.

Sur le poste LAB/DEV qui possède la base de référence :

```bash
export DB_PASSWORD="<mot-de-passe-postgresql-source>"
bash deploiement/common/database/export-reference-db.sh
```

Le script crée un fichier `.dump` sous `runtime/database-transfer`. Copier ce
fichier sur le poste cible.

Sur le poste de RECETTE, après avoir arrêté les services applicatifs :

```bash
export DB_PASSWORD="<mot-de-passe-postgresql-cible>"
bash deploiement/common/database/check-postgres.sh --start
bash deploiement/common/database/replace-db-from-dump.sh \
  "/chemin/vers/scenariogenerator-reference-AAAAMMJJ-HHMMSS.dump"
```

`installé` ne signifie pas `démarré`. Avec `--start`, le script vérifie d'abord
le port puis démarre automatiquement PostgreSQL à partir de l'une des variables
locales suivantes :

```text
POSTGRES_SERVICE_NAME=<nom-exact-du-service-Windows>
PGDATA=<répertoire-portable-contenant-PG_VERSION>
```

Une seule des deux est nécessaire. Si aucune n'est renseignée, le script ne
touche à rien et affiche les commandes manuelles suivantes.

Service Windows, depuis PowerShell lancé en administrateur :

```powershell
Get-Service *postgre*
Start-Service -Name <nom-du-service>
```

Installation portable, depuis Git Bash :

```bash
"$POSTGRES_HOME/bin/pg_ctl.exe" -D "<repertoire-data>" \
  -l "<fichier-log>" start
```

Si le serveur utilise un autre port, modifier `DB_PORT` dans `platform.env`,
recharger `source platform-path.sh`, puis relancer
`check-postgres.sh --start`.

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

Détecter les outils et chemins du poste sans rien écrire :

```bash
bash deploiement/common/runtime/detect-env.sh
```

Le détecteur exige un JDK complet contenant `java` et `javac`. Il rejette les
JBR/JRE embarqués dans IntelliJ et les chemins d'IDE. Le projet produit toujours
du bytecode Java 21. La pile de tests a été actualisée avec Mockito `5.23.0` et
Byte Buddy `1.18.7` pour permettre son exécution avec un JDK complet récent,
notamment JDK 26.

La recherche utilise d'abord les variables existantes, le `PATH` et `where.exe`,
puis les lecteurs disponibles. Pour limiter un balayage :

```bash
export DETECT_DRIVES="/f /d"
export DETECT_MAX_DEPTH=7
bash deploiement/common/runtime/detect-env.sh
```

Générer la configuration locale ignorée par Git :

```bash
bash deploiement/common/runtime/detect-env.sh --write platform.env
```

Cette commande génère également `platform-path.sh`, qui charge `platform.env` et
ajoute Java, Maven, Node.js et PostgreSQL au `PATH`.

Au début de chaque session :

```bash
source platform-path.sh
export DB_PASSWORD="<saisie-locale>"
```

Il faut utiliser `source` : exécuter `bash platform-path.sh` ne modifierait que le
`PATH` d'un sous-processus. `platform-env.sh` charge automatiquement
`platform.env`. L'ordre de priorité est
`variables déjà définies > platform.env > valeurs par défaut`. Le mot de passe
PostgreSQL n'est ni généré ni chargé depuis `platform.env`.

Si RECETTE affiche encore `Mockito cannot mock this class` sous Java 26, vérifier
d'abord les dépendances réellement résolues :

```bash
"$MAVEN" -q dependency:tree \
  -Dincludes=org.mockito:mockito-core,net.bytebuddy:byte-buddy,net.bytebuddy:byte-buddy-agent
```

Les versions attendues sont Mockito `5.23.0` et Byte Buddy `1.18.7`. Supprimer
uniquement le cache Maven de ces artefacts s'il contient un téléchargement
incomplet, puis relancer `mvn verify`.

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
