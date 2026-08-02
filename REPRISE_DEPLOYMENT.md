# Reprise du chantier Déploiement

## Situation de départ au 2 août 2026

Le frontend global est sauvegardé sur la branche
`codex/AddingGlobalFrontrend`, commit `8e8a22d`. Le module Déploiement n'est pas
encore développé. Les exigences ont été définies avec l'utilisateur et doivent
être implémentées puis testées d'abord sur la machine locale, avant toute
promotion vers la recette.

Le chantier Déploiement prolonge le portail Angular existant. Il sera placé sous
`Administration > Déploiements` et respectera le RBAC ainsi que la séparation
Maker/Checker.

## État des fichiers sensibles et travaux exclus

Les éléments suivants sont présents dans le worktree mais ne font pas partie du
commit frontend `8e8a22d` :

| Élément | État observé | Décision |
|---|---|---|
| `keys/dmas-lmk.lmk` | fichier Git suivi et modifié localement | conserver sur disque, ne jamais ajouter à un commit frontend ou déploiement sans traitement de sécurité explicite |
| `ZMK SWAM.txt` | fichier local non suivi | ne jamais versionner ; conserver hors des bundles, licences, journaux et documents |
| `documents/specifications/mastercard/` | répertoire local non suivi | travail documentaire propriétaire, hors périmètre du commit frontend |
| `documents/specifications/swam/` | répertoire local non suivi | travail documentaire propriétaire, hors périmètre du commit frontend |
| `documents/specifications/visa/` | répertoire local non suivi | travail documentaire propriétaire, hors périmètre du commit frontend |
| `documents/specifications/waypos/` | répertoire local non suivi | travail documentaire propriétaire, hors périmètre du commit frontend |
| `tests/dmas/MembreReelWayVersDmasMastercard/` | travail de test DMAS local non suivi | conserver et auditer séparément avant un éventuel commit dédié |
| `tests/swam/problemeMacSwamMembreNotre SwamIssuer/` | travail de diagnostic SWAM local non suivi | conserver et auditer séparément avant un éventuel commit dédié |
| `tests/database-rebuild/backup-2026-07-25/` | sauvegarde locale non suivie | ne pas intégrer à Git ni aux bundles |
| `tmp/` et `__pycache__/` | fichiers temporaires non suivis | hors périmètre et non versionnés |

Aucun de ces éléments n'a été supprimé. Ils restent présents sur la machine.
Le module Déploiement ne devra jamais copier automatiquement une LMK, une ZMK,
un mot de passe, un PAN ou une autre donnée secrète dans Git, un PDF de licence,
un artefact JAR ou un journal.

## Périmètre fonctionnel convenu

Le menu cible est :

```text
Administration
└── Déploiements
    ├── Clients banques / Membres
    ├── Installations
    ├── Catalogue des modules
    ├── Environnements
    ├── Système et shell
    ├── Bases de données
    ├── Variables d'environnement
    ├── Secrets
    ├── Licences
    ├── Bundles Membres
    ├── Bundles Simulateurs
    ├── Demandes et validations
    ├── Exécutions et journaux
    └── Rollback
```

Permissions prévues :

- `DEPLOYMENT_VIEW` ;
- `DEPLOYMENT_PREPARE` ;
- `DEPLOYMENT_APPROVE` ;
- `DEPLOYMENT_EXECUTE` ;
- `DEPLOYMENT_ROLLBACK`.

Un Maker prépare le déploiement ou la licence. Un Checker différent doit
l'approuver. L'auto-approbation reste interdite.

## Clients banques, modules et licences

Chaque installation est rattachée à un client banque/membre : code client,
raison sociale, pays, identifiants réseau, contacts, environnements autorisés,
base de données et période de licence.

Modules côté membre sélectionnables :

- ServerPOS ;
- Acquisition POS/e-commerce ;
- Card Issuing ;
- 3DS Member ;
- SWAM Member ;
- DMAS Member ;
- DMCS Member.

Modules côté simulateur sélectionnables :

- POS Simulator ;
- Merchant Site Simulator national/international ;
- 3DS Network Simulator ;
- simulateur réseau Visa/Mastercard ;
- DMAS Mastercard Simulator ;
- SWAM Switch Simulator.

Deux licences sont produites après validation :

- `license.pdf`, document lisible avec identité de la banque, modules,
  environnement, validité, versions, empreinte et signature ;
- `license.json.sig`, licence technique signée et vérifiable par les bundles.

Le PDF ne contient aucun secret. Pour le test local, il porte le filigrane
`TEST LOCAL - NON CONTRACTUEL`.

## Bundles prévus

Deux artefacts exécutables standardisés doivent être produits :

```text
scenario-members-bundle.jar
scenario-simulators-bundle.jar
```

La licence et le manifeste de déploiement activent seulement les modules
achetés ou choisis par la banque. Les bundles ne contiennent ni mot de passe,
ni clé réelle, ni PAN de recette.

Le lanceur interne doit conserver un port, une configuration, un journal et un
health-check séparés pour chaque module. Il ne doit pas fusionner toutes les
applications Spring dans un contexte unique.

## Systèmes, shells et répertoires

Types d'exécution à prendre en charge :

- `GIT_BASH` ;
- `POWERSHELL` ;
- `CMD_WINDOWS` pour les scripts `.cmd` et `.bat` ;
- `BASH_LINUX`.

Le premier déploiement local utilisera :

```text
Windows  : D:\MoneyCore\ScenarioGenerator\runtime\deployment\local
Git Bash : /d/MoneyCore/ScenarioGenerator/runtime/deployment/local
```

Arborescence cible :

```text
local/
├── artifacts/
├── config/
├── logs/
├── backups/
└── scripts/
    ├── gitbash/
    ├── powershell/
    ├── cmd/
    └── linux/
```

Les commandes seront choisies dans un catalogue contrôlé. Le frontend ne doit
pas permettre l'exécution d'une commande shell libre.

## Deux modes de déploiement obligatoires

Le même moteur de déploiement doit être utilisable de deux manières.

### Mode 1 - menu Administration

Le mode graphique est destiné aux administrateurs, exploitants, Makers et
Checkers. Il guide l'utilisateur pour :

- sélectionner la banque, l'installation et l'environnement ;
- sélectionner le bundle et sa version ;
- sélectionner les modules membres et simulateurs autorisés par la licence ;
- contrôler les variables, la base et les répertoires sans révéler les secrets ;
- générer un plan de déploiement ;
- soumettre et approuver la demande ;
- exécuter, suivre les étapes et consulter les journaux filtrés ;
- vérifier les health-checks ;
- préparer une mise à niveau ou un rollback.

Le menu n'exécute pas directement une chaîne de commande fournie par
l'utilisateur. Il appelle les API du moteur avec un manifeste validé.

### Mode 2 - ligne de commande pour les équipes techniques

Un outil CLI commun et des enveloppes par shell doivent permettre les mêmes
opérations sans utiliser Angular :

```text
deployment-cli.jar
scripts/gitbash/deploy.sh
scripts/powershell/deploy.ps1
scripts/cmd/deploy.cmd
scripts/linux/deploy.sh
```

Commandes fonctionnelles prévues :

```text
validate   vérifier manifeste, licence, chemins, Java, base et ports
plan       afficher les actions sans modifier la machine
install    installer les artefacts et la configuration
start      démarrer les modules sélectionnés
status     afficher PID et health-checks
stop       arrêter uniquement les processus de l'installation ciblée
upgrade    promouvoir une version validée
rollback   restaurer une version précédente
logs       afficher les journaux filtrés par module
```

Les scripts Git Bash, PowerShell, CMD Windows et Bash Linux appellent le même
CLI. Ils ne réimplémentent pas les règles de licence, de sécurité ou de
déploiement.

Le CLI doit produire un identifiant d'exécution et la même piste d'audit que le
menu. En mode connecté, il appelle les API avec une identité technique ou
utilisateur autorisée. Aucun mot de passe ne doit être passé en clair dans les
arguments, car il serait visible dans l'historique du shell et la liste des
processus.

### Contrat commun entre menu et CLI

Les deux modes partagent obligatoirement :

- le manifeste de déploiement versionné sans secrets ;
- la licence technique signée ;
- le catalogue de modules et de commandes autorisées ;
- la validation des répertoires ;
- les références de secrets ;
- l'idempotence et les verrous d'exécution ;
- les contrôles avant/après ;
- l'historique, les preuves et les règles de rollback.

Une opération préparée depuis le CLI peut ainsi être visible dans le menu, et
une opération préparée dans le menu peut être diagnostiquée avec le CLI.

## Choix du shell et validation des prérequis

La fiche Environnement du menu doit permettre de choisir le shell d'exécution :

- Windows : `GIT_BASH`, `POWERSHELL` ou `CMD_WINDOWS` ;
- Linux : `BASH_LINUX`.

Le système propose automatiquement les shells compatibles avec l'OS choisi et
demande le chemin de l'exécutable si celui-ci n'est pas détecté. Un shell
incompatible avec le système cible ne peut pas être enregistré.

Deux moyens équivalents permettent de valider la machine avant déploiement :

1. bouton `Vérifier les prérequis` depuis le menu ;
2. commande technique :

```bash
java -jar deployment-cli.jar validate --manifest deployment-manifest.yml
```

Les scripts par shell exposent aussi cette action :

```bash
bash ./scripts/gitbash/deploy.sh validate
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/powershell/deploy.ps1 validate
cmd.exe /d /s /c scripts\cmd\deploy.cmd validate
bash ./scripts/linux/deploy.sh validate
```

La validation est en lecture seule et contrôle au minimum :

- compatibilité OS/shell et présence de l'exécutable ;
- version et chemin de Java ;
- existence et droits des répertoires artifacts/config/logs/backups ;
- espace disque disponible ;
- disponibilité des ports ;
- présence et empreinte des deux bundles demandés ;
- signature, validité, client, environnement et modules de la licence ;
- présence de toutes les variables obligatoires du `.env` ;
- résolution des références de secrets sans afficher leurs valeurs ;
- connectivité PostgreSQL ou Oracle et droits nécessaires ;
- disponibilité des migrations attendues ;
- accessibilité des endpoints et dépendances externes configurés ;
- absence d'un autre déploiement actif sur la même installation.

Chaque contrôle retourne `OK`, `WARNING` ou `BLOCKING`, avec un détail sans
secret. Le rapport porte un identifiant, une date, le client, l'environnement,
le shell et les empreintes d'artefacts. Un résultat `BLOCKING` interdit le
bouton Exécuter et la commande `install`.

Le rapport produit par le CLI doit être consultable dans le menu. Inversement,
le rapport déclenché depuis le menu doit pouvoir être exporté en JSON ou en
texte pour l'équipe technique.

## Base de données et variables d'environnement

Moteurs prévus :

- PostgreSQL ;
- Oracle, avec Service Name ou SID, TNS/Wallet facultatif et schéma ;
- aucune base pour un module qui n'en utilise pas.

Paramètres : hôte, port, base/service, schéma, utilisateur, secret du mot de
passe, SSL/TLS, URL JDBC calculée, test de connexion et migrations.

Le menu importe ou génère le `.env` à partir d'un catalogue typé. Il couvre les
variables déjà utilisées par les modules, notamment les groupes suivants :

- connexion base de données ;
- mots de passe propres aux modules ;
- ports et URL ;
- options Java/JVM ;
- paramètres de recette Issuing, WayPos, DMAS, SWAM, Acquisition et 3DS ;
- références vers les secrets cryptographiques.

Les valeurs sensibles sont `write-only`, masquées, absentes des API de lecture,
des différences, des journaux et du PDF. Le `.env` cible doit recevoir une ACL
Windows restrictive ou le mode `0600` sous Linux.

## Phases de réalisation et de test

### Phase 1 - machine locale

1. cadrer le modèle de données et les API ;
2. développer le menu Angular Administration > Déploiements ;
3. développer le catalogue modules/clients/environnements ;
4. produire les deux bundles ;
5. développer le manifeste et la licence technique signée ;
6. générer le PDF de licence test ;
7. générer les scripts Git Bash, PowerShell, CMD et Linux ;
8. installer dans `runtime/deployment/local` ;
9. tester démarrage, health-checks, journaux, arrêt et redémarrage ;
10. tester mise à niveau et rollback ;
11. tester RBAC et Maker/Checker ;
12. exécuter les tests Playwright et la non-régression Maven ;
13. arrêter tous les processus démarrés par le harnais.

Les scripts Linux seront validés statiquement sur Windows. Leur exécution réelle
attendra un hôte Linux.

### Phase 2 - machine de recette

Après validation locale, créer l'environnement `RECETTE` avec l'adresse, le
système, le shell, les répertoires, PostgreSQL ou Oracle, les ports, les
health-checks et les références de secrets réels. La méthode distante sera un
agent local, SSH ou WinRM selon la machine cible.

Le même artefact validé localement doit être promu en recette sans
reconstruction du JAR.

## Livrables documentaires prévus

Après développement et tests :

- `tests/deployment/MANUEL_TEST_DEPLOIEMENT_BUNDLES_ET_LICENCES.md` ;
- `tests/deployment/MANUEL_OPERATEUR_MENU_DEPLOIEMENT.md` ;
- `tests/deployment/MANUEL_TECHNIQUE_CLI_DEPLOIEMENT.md` ;
- schémas d'architecture et de séquence ;
- scripts exacts Git Bash, PowerShell, CMD et Linux ;
- manuel technique CLI couvrant `validate`, `plan`, `install`, `start`,
  `status`, `stop`, `upgrade`, `rollback` et `logs` ;
- manuel opérateur du menu Administration > Déploiements ;
- preuves des tests réellement exécutés ;
- résultats attendus, limites et procédure de promotion en recette ;
- mise à jour du présent document de reprise.

## Premier travail non terminé

Le premier travail est l'inventaire technique des modules et de leurs modes de
packaging actuels, puis la conception du manifeste commun sans modifier les
applications métier existantes. Aucun bundle, menu Déploiement, modèle client
banque ou licence n'est encore implémenté à cette date.

## État Git et processus

- branche active : `codex/AddingGlobalFrontrend` ;
- dernier commit poussé : `8e8a22d` ;
- les fichiers sensibles listés plus haut restent hors commit ;
- aucun processus de déploiement n'est actif.

## Situation réalisée au 2 août 2026

Le module Déploiement local est désormais implémenté et validé sur la branche
`codex/AddingDeploimentProcess`. Le périmètre réalisé comprend :

- cœur Java commun, manifeste YAML, préflight et licence RSA/PDF ;
- CLI autonome et scripts Git Bash, PowerShell, CMD Windows et Bash Linux ;
- deux bundles exécutables : 8 modules membres et 6 simulateurs ;
- bibliothèques mutualisées une seule fois par bundle, avec inventaire Maven
  versionné dans `bundle-libraries.txt` (115 JAR membres, 114 simulateurs) ;
- API d'administration et tables SQL client, environnement, licence et exécution ;
- séparation Maker/Checker pour licences et opérations sensibles ;
- menu Angular Administration > Déploiements, RBAC et traductions FR/EN/ES ;
- manuels opérateur, technique et test sous `tests/deployment/`.

Le test local de référence utilise uniquement `THREE_DS_NETWORK_SIMULATOR` et
le manifeste `tests/deployment/config/deployment-local-windows.yml`. Résultats :

- non-régression Maven finale : 75 tests (69 communs, 5 Deployment et 1
  simulateur réseau 3DS), 0 échec, 0 erreur ;
- build des 22 projets nécessaires aux deux bundles : succès ;
- préflight : `READY`, Java 25, port 8561 et licence valides ;
- endpoint `/api/3ds/network/v1/health` : HTTP 200, Visa et Mastercard ;
- `status` et `logs` : succès, UTF-8 validé ;
- `stop` : bundle et enfant arrêtés, port 8561 libéré ;
- Playwright : 2 scénarios Deployment réussis.

Les défauts rencontrés et corrigés pendant l'E2E étaient la résolution relative
du manifeste installé, le wildcard de classpath Windows, la lecture de logs
Windows et l'arrêt du processus enfant. Aucun processus Deployment/3DS issu du
test ne reste actif.

## Premier travail non terminé après le test local

Le périmètre Deployment est inventorié, testé et versionné sur sa branche
dédiée. Conserver notamment `keys/dmas-lmk.lmk`, `ZMK SWAM.txt`,
`tmp/pdfs/deployment-local-private.pem` et les travaux propriétaires hors de
toute sélection Git.

La promotion vers une machine de recette reste volontairement non commencée :
elle attend les paramètres réels de la banque, de l'OS, de PostgreSQL/Oracle, des
ports, des chemins et des références de secrets. Le Bash Linux doit également
être exécuté sur un véritable hôte Linux.

Les fichiers sensibles et travaux DMAS/SWAM/Way4 listés plus haut restent
strictement hors du périmètre et n'ont été ni supprimés ni ajoutés aux bundles.
