# Comment reprendre le projet dans une nouvelle session

Ce fichier est le point d'entrée d'une nouvelle session Codex consacrée au portail
modulaire, au Maker/Checker et à SWAM LIS.

## 1. Message à donner à la nouvelle session

Copier ce message dans la nouvelle tâche :

```text
Travaille dans le dépôt ScenarioGenerator.
Commence par lire intégralement COMMENT_REPRENDRE_NOUVELLE_SESSION.md,
puis les documents qu'il référence, dans l'ordre indiqué.
Vérifie la branche et l'état Git avant toute modification.
Ne committe jamais les fichiers locaux préexistants hors périmètre.
Reprends ensuite la première phase non terminée du plan.
```

## 2. Branche de travail

La branche du portail est :

```text
codex/portail-rbac-maker-checker
```

Ne pas poursuivre le portail depuis une autre branche sans fusion explicite.

## 3. Récupérer la dernière version depuis GitHub

### 3.1 Dépôt déjà présent sur le PC

Dans Git Bash :

```bash
cd /d/MoneyCore/ScenarioGenerator
git status --short
git fetch origin
git switch codex/portail-rbac-maker-checker
git pull --ff-only origin codex/portail-rbac-maker-checker
git log -5 --oneline --decorate
```

Si `git status --short` montre des modifications locales, ne pas les supprimer et
ne pas lancer de reset. Les conserver, puis vérifier qu'elles ne chevauchent pas
les fichiers à modifier.

### 3.2 Premier téléchargement sur un autre PC

```bash
cd /d/MoneyCore
git clone https://github.com/ouadieelhadj/ScenarioGenerator.git
cd ScenarioGenerator
git fetch origin
git switch --track origin/codex/portail-rbac-maker-checker
```

Si le dépôt est copié ailleurs, les scripts calculent automatiquement `ROOT`
depuis leur propre emplacement.

## 4. Documents à lire dans cet ordre

### Obligatoires

1. `COMMENT_REPRENDRE_NOUVELLE_SESSION.md`
2. `conceptions/frontend/CONCEPTION_PORTAIL_MODULAIRE_RBAC_MAKER_CHECKER.md`
3. `conceptions/frontend/GUIDE_TEST_PORTAIL_MODULAIRE.md`
4. `deploiement/README.md`
5. `deploiement/common/runtime/platform-env.sh`
6. `deploiement/common/runtime/start-platform.sh`

### Pour continuer SWAM LIS

7. `conceptions/swam/clearing/CONCEPTION_LIS_AVANT_IMPLEMENTATION.md`
8. `deploiement/swam/README.md`
9. `deploiement/swam/swam-lis-e2e.sh`

### Spécifications de référence

10. `documents/specifications/swam/Description_Interface_Switch-SID_V3-20_05012024.pdf`
11. `documents/specifications/swam/Local Interchange Specifications - LIS4 14-CMI.pdf`

Les spécifications PDF peuvent être absentes d'un clone Git si elles sont
volumineuses ou volontairement conservées localement. Dans ce cas, demander leur
copie au responsable avant de modifier les règles métier LIS.

## 5. État actuel à connaître

### Terminé

- modules `sg-swam-lis-common`, `sg-swam-lis-member` et
  `sg-swam-lis-switch` ;
- flux SWAM LIS bilatéral ;
- EOD membre et switch ;
- génération et import croisé des LIS ;
- rapprochement ;
- chargebacks dans les deux sens ;
- représentation ;
- comptabilité équilibrée ;
- E2E SWAM LIS validé avec 36 contrôles ;
- conception du portail modulaire ;
- schéma SQL initial modules, navigation, profils multiples, équipes,
  Maker/Checker, SLA, calendrier et notifications ;
- API `/api/me/navigation` ;
- menu Angular dynamique par module ;
- écran clearing commun chargé avec un contexte de module ;
- E2E navigateur Playwright validé avec 4 scénarios ;
- organisation de `deploiement` par module ;
- lanceur global avec variables portables.

### Non terminé

- moteur Maker/Checker opérationnel complet ;
- administration des profils multiples et des droits individuels ;
- écrans d'administration de l'arborescence ;
- gestion opérationnelle des équipes et affectations ;
- changement de Maker/Checker via API et frontend ;
- délégations ;
- calcul effectif des SLA sur calendrier bancaire marocain ;
- notifications internes et emails ;
- API `GET` paginées complètes de SWAM LIS ;
- écrans métier détaillés transactions, fichiers, rapprochement, litiges,
  EOD et comptabilité.

Une nouvelle session ne doit donc pas considérer que tout le portail est finalisé.

## 6. Variables globales

Les valeurs communes sont définies dans :

```text
deploiement/common/runtime/platform-env.sh
```

Elles peuvent être surchargées sans modifier les scripts :

```bash
export ROOT=/d/MoneyCore/ScenarioGenerator
export JAVA_HOME_DIR=/d/MoneyCore/jdk-21.0.11
export MAVEN_HOME=/d/MoneyCore/idea-2026.1.3.win/plugins/maven/lib/maven3
export NODE_HOME=/d/MoneyCore/nodejs
export POSTGRES_HOME=/d/MoneyCore/PostgreSQL/18
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=scenariogenerator
export DB_USER=postgres
export DB_PASSWORD="<mot-de-passe-local>"
```

Ne jamais committer de mot de passe réel, de clé claire, de LMK, de ZMK ou de KEK.

## 7. Compiler et lancer la plateforme

### Compiler tous les modules

```bash
bash deploiement/common/runtime/start-platform.sh build
```

### Démarrer les backends

```bash
bash deploiement/common/runtime/start-platform.sh start
```

### Vérifier les processus

```bash
bash deploiement/common/runtime/start-platform.sh status
```

### Arrêter les processus démarrés par le lanceur

```bash
bash deploiement/common/runtime/start-platform.sh stop
```

Les logs sont écrits dans :

```text
runtime/platform/logs/
```

## 8. Lancer le frontend

Dans un autre terminal Git Bash :

```bash
bash deploiement/frontend/start-frontend.sh
```

Puis ouvrir :

```text
http://localhost:4200
```

## 9. Tests à lancer

### 9.1 Tests Maven complets

```bash
bash deploiement/common/runtime/start-platform.sh build
```

Pour exécuter les tests unitaires au lieu du build avec tests ignorés :

```bash
source deploiement/common/runtime/platform-env.sh
"$MAVEN" -f "$ROOT/pom.xml" verify \
  -Dmaven.repo.local="$MAVEN_REPO"
```

Résultat attendu :

```text
BUILD SUCCESS
```

### 9.2 E2E frontend Playwright

```bash
export DB_PASSWORD="<mot-de-passe-postgresql>"
export E2E_LOGIN="admin"
export E2E_PASSWORD="<mot-de-passe-utilisateur>"
bash deploiement/frontend/frontend-e2e.sh
```

Résultat attendu :

```text
4 passed
RESULTAT : E2E FRONTEND PASSED
```

### 9.3 E2E SWAM LIS

```bash
export SWAM_E2E_KEK_CLEAR="<cle-de-test-autorisee>"
bash deploiement/swam/swam-lis-e2e.sh
```

Résultat attendu :

```text
RESULTAT : PASSED (36 controles)
```

La clé ne doit jamais être inscrite dans un fichier versionné.

### 9.4 E2E SWAM SID

```bash
bash deploiement/swam/swam-e2e.sh
```

## 10. Contrôles avant un commit

```bash
git status --short
git diff --check
git diff --stat
```

Indexer uniquement les fichiers du périmètre :

```bash
git add -- <liste-explicite-des-fichiers>
```

Ne pas inclure automatiquement :

- `runtime/` ;
- `tmp/` ;
- traces et captures ;
- spécifications locales ;
- sauvegardes de base ;
- modifications préexistantes sans rapport.

Après les tests :

```bash
git commit -m "<message précis>"
git push origin codex/portail-rbac-maker-checker
```

## 11. Première action recommandée pour la prochaine session

1. vérifier `git status` et la branche ;
2. lire la conception complète ;
3. lancer le build global ;
4. lancer l'E2E frontend ;
5. implémenter la prochaine tranche verticale du Maker/Checker :
   affectation Maker vers Checker, création d'une demande, soumission, validation,
   rejet et interdiction d'auto-validation ;
6. ajouter les tests backend et Playwright correspondants ;
7. seulement ensuite poursuivre les SLA, délégations et notifications.
