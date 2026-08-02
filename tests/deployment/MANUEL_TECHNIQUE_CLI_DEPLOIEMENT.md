# Manuel technique — CLI de déploiement

## Principe

`sg-deployment-cli/target/deployment-cli.jar` est l'outil commun aux quatre
enveloppes shell. Les règles de manifeste, licence, validation et processus ne
sont pas dupliquées dans les scripts.

Java 21 minimum est obligatoire. Pour imposer un Java précis, définir
`DEPLOYMENT_JAVA` avant d'appeler le script.

## Scripts

### Git Bash

```bash
export DEPLOYMENT_JAVA=/d/MoneyCore/idea-2026.1.3.win/jbr/bin/java.exe
bash ./deployment/scripts/gitbash/deploy.sh help
```

### Linux Bash

```bash
export DEPLOYMENT_JAVA=/opt/scenariogenerator/jdk-21/bin/java
bash ./deployment/scripts/linux/deploy.sh help
```

### PowerShell

```powershell
$env:DEPLOYMENT_JAVA = 'D:\apps\jdk-21\bin\java.exe'
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\deployment\scripts\powershell\deploy.ps1 help
```

### CMD

```bat
set "DEPLOYMENT_JAVA=D:\apps\jdk-21\bin\java.exe"
cmd.exe /d /s /c deployment\scripts\cmd\deploy.cmd help
```

## Commandes

Toutes les commandes utilisent `--manifest <fichier.yml>` :

| Commande | Effet |
|---|---|
| `validate` | lecture seule, prérequis et licence |
| `plan` | affiche le plan sans installation |
| `install` | copie bundles/licence et écrit un manifeste installé à chemins absolus |
| `start` | démarre les bundles sélectionnés |
| `status` | vérifie les PID des bundles |
| `stop` | arrête le bundle ciblé et tous ses descendants |
| `upgrade` | sauvegarde les artefacts courants puis installe la nouvelle version |
| `rollback` | restaure la sauvegarde la plus récente |
| `logs` | retourne les dernières lignes du bundle membre ou simulateur |

Exemple :

```bash
MANIFEST=./tests/deployment/config/deployment-local-windows.yml
bash ./deployment/scripts/gitbash/deploy.sh validate --manifest "$MANIFEST"
bash ./deployment/scripts/gitbash/deploy.sh install --manifest "$MANIFEST"
bash ./deployment/scripts/gitbash/deploy.sh start --manifest "$MANIFEST"
bash ./deployment/scripts/gitbash/deploy.sh logs --manifest "$MANIFEST" \
  --bundle simulators --lines 100
bash ./deployment/scripts/gitbash/deploy.sh stop --manifest "$MANIFEST"
```

## Manifeste

Le manifeste décrit le client, l'environnement, l'OS, le shell, Java, la base,
les modules, les chemins des bundles et de la licence. Les chemins relatifs sont
résolus depuis le manifeste source ; lors de `install`, le manifeste cible est
réécrit avec des chemins absolus afin qu'un redémarrage ne change pas leur sens.

Les variables sensibles restent des références. Ne jamais mettre un mot de
passe, une LMK, une ZMK, une clé claire ou un PAN dans le YAML ou dans les
arguments de commande.

## Arborescence installée

```text
deployment-root/
|-- artifacts/   bundles installés
|-- config/      manifeste absolu, licence et clé publique
|-- lib/         bibliothèques mutualisées extraites
|-- modules/     JAR minces des modules autorisés
|-- logs/        journaux bundle et module
|-- state/       PID et horodatages
|-- backups/     versions conservées pour rollback
`-- scripts/
```

## Codes de validation

`READY` autorise la suite. `WARNING` signale un point non bloquant, par exemple
une base `NONE`. `BLOCKING` empêche `install`, `start` et `upgrade`.

## Diagnostic

- `Bundle introuvable` : reconstruire ou corriger le chemin ;
- `Java ... minimum 21` : définir un JDK/JBR compatible ;
- `Port disponible` en échec : identifier le service existant sans l'arrêter à l'aveugle ;
- `Licence` en échec : vérifier signature, dates, client, environnement et modules ;
- `logs` : utiliser `--bundle members` ou `--bundle simulators` ;
- PID `STOPPED` : contrôler le journal du module avant un nouveau démarrage.

Les sorties sont forcées en UTF-8. L'arrêt attend le bundle et ses enfants puis
force uniquement les processus encore vivants de cette installation.
