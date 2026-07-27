# Comment reprendre le projet dans une nouvelle session

Ce fichier est le point d'entrée d'une nouvelle session Codex consacrée au portail
modulaire, au Maker/Checker et à SWAM LIS.

## Priorité absolue de reprise

La reprise doit se faire dans cet ordre, sans passer aux autres modules avant la
validation complète de SWAM :

```text
1. Récupérer la dernière version du dépôt
2. Vérifier et compiler le projet
3. Lancer les scripts de démarrage SWAM Issuer et SWAM Membre
4. Tester le bootstrap et l'échange des clés
5. Passer et contrôler les achats SWAM
6. Lancer les fins de journée et le clearing LIS bilatéral
7. Valider le scénario SWAM complet et ses non-régressions
8. Seulement ensuite reprendre les autres modules et le portail Maker/Checker
```

L'objectif immédiat n'est donc pas de développer DMAS, Mastercard SMS ou un autre
réseau. Il faut d'abord disposer d'une chaîne SWAM reproductible sur un nouveau PC.

## 1. Message de reprise à donner à toute nouvelle session

Ce message peut être transmis à une nouvelle session Codex ou à tout autre
assistant, développeur ou intervenant chargé de reprendre le projet. Il ne suppose
pas que la nouvelle session ait accès à ce PC ou à ses fichiers locaux.

Pour le poste RECETTE, utiliser en priorité le message court et maintenu dans :

```text
MESSAGE_REPRISE_RECETTE.md
```

Copier uniquement le message suivant :

```text
Reprends le projet ScenarioGenerator.

Tu peux ne pas avoir accès au PC ou au répertoire local utilisé par la session
précédente. Commence donc par récupérer le dépôt depuis GitHub et par te placer
sur la branche :
codex/portail-rbac-maker-checker

Dépôt :
https://github.com/ouadieelhadj/ScenarioGenerator.git

Organisation des environnements :
- le poste d'origine est LAB/DEV : développement, corrections et commits ;
- ton poste est RECETTE : récupération des commits et validation fonctionnelle.

Ne développe pas directement une correction en RECETTE. Si tu trouves une
anomalie, fournis le commit testé et des traces assainies afin qu'elle soit
reproduite et corrigée sur LAB/DEV, puis récupère le nouveau commit.

Après le clone ou la mise à jour du dépôt, récupère et lis intégralement le
fichier suivant avant toute analyse, modification ou exécution :
COMMENT_REPRENDRE_NOUVELLE_SESSION.md

Ce fichier constitue la source de reprise. Suis les documents, les scripts et
les étapes qu'il référence dans l'ordre indiqué. La priorité est de valider la
chaîne SWAM complète : démarrage Issuer et Membre, bootstrap et échange des
clés, achats dans les deux sens sur la liaison permanente, EOD et clearing LIS
bilatéral.

Pour cette première validation, travaille en mode LOCAL E2E : SWAM Issuer joue
le rôle du switch et SWAM Membre joue le rôle de la banque membre sur le même
poste. Ne demande pas les paramètres d'un switch externe et ne tente aucune
connexion de production. Une connexion à un switch distant constitue une phase
différente, soumise à validation explicite du responsable.

Vérifie toujours la branche et l'état Git avant toute modification. Ne supprime
pas, ne remplace pas et ne committe pas les fichiers locaux préexistants hors
périmètre. Reprends ensuite la première phase non terminée indiquée dans le
document.
```

Si la session travaille sur un autre poste, elle doit également déterminer si
la base de référence a déjà été transférée. La procédure complète est décrite
dans `deploiement/README.md` et résumée à la section 4.3 ci-dessous.

## 2. Choisir le bon mode avant toute configuration

Deux situations très différentes ne doivent pas être confondues.

### Rôle des deux postes

L'organisation de référence est la suivante :

- **ce poste : LAB/DEV** — laboratoire, conception, développement, corrections,
  tests techniques et création des commits ;
- **autre poste : RECETTE** — récupération des commits validés, restauration
  contrôlée des données de test, exécution des scénarios de recette et production
  des rapports de validation.

Le poste de recette ne doit pas servir à développer directement des corrections.
Une anomalie trouvée en recette est reproduite et corrigée sur LAB/DEV, puis la
correction est commitée, poussée et récupérée en recette.

Les deux postes utilisent des configurations et des secrets distincts. Aucun mot
de passe, fichier HSM, LMK, ZMK, KEK ou certificat privé de LAB/DEV ne doit être
copié dans Git ou réutilisé implicitement en recette.

### Mode A — recette locale complète sur l'autre PC

C'est le mode obligatoire pour la reprise actuelle sur le poste de recette :

- `sg-swam-issuer` simule le switch SWAM ;
- `sg-swam-acquirer` représente le membre bancaire ;
- les deux services et les modules LIS tournent sur le même poste ;
- le code provient exclusivement des commits poussés depuis LAB/DEV ;
- la configuration de référence non sensible provient du dépôt ;
- la base de recette est initialisée par un dump de test contrôlé et assaini ;
- les clés utilisées sont exclusivement des clés de recette injectées hors Git ;
- aucun VPN, certificat ou accès à un switch externe n'est nécessaire.

Dans ce mode, il ne faut pas demander au responsable d'un switch distant son IP,
ses clés ou une fenêtre de certification. Il faut suivre les scripts locaux de
la section 7.

### Promotion LAB/DEV vers RECETTE

Pour chaque livraison :

1. terminer les modifications et tests sur LAB/DEV ;
2. committer et pousser uniquement les fichiers du périmètre ;
3. communiquer à la recette la branche et le hash du commit attendu ;
4. sur RECETTE, vérifier que l'arbre Git est propre puis faire
   `git pull --ff-only` ;
5. transférer un dump uniquement si le schéma ou les données de référence
   l'exigent ;
6. sauvegarder la base de recette avant toute restauration ;
7. injecter les secrets propres à la recette par variables ou canal sécurisé ;
8. paramétrer et valider séparément le switch puis le membre ;
9. compiler et exécuter la chaîne SWAM complète ;
10. conserver les logs et résultats de recette sans les committer s'ils
   contiennent des données sensibles ;
11. retourner les anomalies vers LAB/DEV avec le commit testé et les traces
    assainies.

### Mode B — raccordement de recette à un véritable switch distant

Ce mode n'est pas nécessaire pour valider la reprise sur un autre PC. Il ne doit
commencer qu'après réussite du Mode A et accord explicite du responsable. Il
nécessite alors la fiche de cadrage de la section 8 : environnement autorisé,
réseau, version des spécifications, identité membre, procédure HSM et plan de
certification.

Une cible de production ne doit jamais être configurée ou testée à partir de
cette procédure de LAB/DEV ou de recette.

## 3. Branche de travail

La branche du portail est :

```text
codex/portail-rbac-maker-checker
```

Ne pas poursuivre le portail depuis une autre branche sans fusion explicite.

## 4. Récupérer la dernière version depuis GitHub

### 4.1 Dépôt déjà présent sur le PC

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

### 4.2 Premier téléchargement sur un autre PC

```bash
cd /d/MoneyCore
git clone https://github.com/ouadieelhadj/ScenarioGenerator.git
cd ScenarioGenerator
git fetch origin
git switch --track origin/codex/portail-rbac-maker-checker
```

Si le dépôt est copié ailleurs, les scripts calculent automatiquement `ROOT`
depuis leur propre emplacement.

### 4.3 Initialiser ou remplacer la base de RECETTE

Cette opération détruit le contenu actif de la base de recette. Le script fourni
crée d'abord une sauvegarde de sécurité et exige une confirmation explicite. Le
dump source doit contenir uniquement des données de test autorisées et assainies.
Ne jamais committer le fichier `.dump`, qui peut contenir des données sensibles.

Sur LAB/DEV, après validation du contenu de test à promouvoir :

```bash
cd /d/MoneyCore/ScenarioGenerator
export DB_PASSWORD="<mot-de-passe-postgresql-source>"
bash deploiement/common/database/export-reference-db.sh
```

Transférer de manière sécurisée le fichier créé sous
`runtime/database-transfer` vers le nouveau poste.

Sur RECETTE :

```bash
cd /d/MoneyCore/ScenarioGenerator
bash deploiement/common/runtime/start-platform.sh stop
export DB_PASSWORD="<mot-de-passe-postgresql-cible>"
bash deploiement/common/database/replace-db-from-dump.sh \
  "/chemin/vers/scenariogenerator-reference-AAAAMMJJ-HHMMSS.dump"
```

Le script sauvegarde la base de recette sous `runtime/database-backups`, ferme
ses connexions, la recrée puis restaure la base de référence. Après restauration,
reprendre à la compilation et aux tests SWAM décrits dans la section 7.

## 5. Documents à lire dans cet ordre

### Obligatoires

1. `COMMENT_REPRENDRE_NOUVELLE_SESSION.md`
2. `deploiement/README.md`
3. `deploiement/common/runtime/platform-env.sh`
4. `deploiement/common/runtime/detect-env.sh`
5. `deploiement/common/runtime/platform.env.example`
6. `deploiement/common/runtime/start-platform.sh`
7. `deploiement/common/database/export-reference-db.sh`
8. `deploiement/common/database/replace-db-from-dump.sh`
9. `deploiement/swam/README.md`
10. `deploiement/swam/swam-e2e.sh`
11. `deploiement/swam/swam-lis-e2e.sh`
12. `tests/swam/issuer/start-and-bootstrap.sh`
13. `tests/swam/acquirer/start-and-bootstrap.sh`
14. `conceptions/swam/clearing/CONCEPTION_LIS_AVANT_IMPLEMENTATION.md`

### À lire après la finalisation SWAM

15. `conceptions/frontend/CONCEPTION_PORTAIL_MODULAIRE_RBAC_MAKER_CHECKER.md`
16. `conceptions/frontend/GUIDE_TEST_PORTAIL_MODULAIRE.md`

### Spécifications de référence

17. `documents/specifications/swam/Description_Interface_Switch-SID_V3-20_05012024.pdf`
18. `documents/specifications/swam/Local Interchange Specifications - LIS4 14-CMI.pdf`

Les spécifications PDF peuvent être absentes d'un clone Git si elles sont
volumineuses ou volontairement conservées localement. Dans ce cas, demander leur
copie au responsable avant de modifier les règles métier LIS.

## 6. État actuel à connaître

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

## 7. Chaîne de scripts SWAM à exécuter en premier

Les scripts sont présents dans le dépôt :

```text
deploiement/swam/
├── 01-start-issuer.sh
├── 02-start-member.sh
├── 03a-bootstrap-issuer.sh
├── 03b-bootstrap-member.sh
├── 03c-signon-and-key-exchange.sh
├── 03-bootstrap-keys.sh
├── 04-run-purchases.sh
├── 05-run-lis-clearing.sh
├── 06-stop-swam.sh
├── lib-swam.sh
└── swam-full-e2e.sh
```

### Processus de test détaillé

Après récupération du dépôt :

```bash
cd /d/MoneyCore/ScenarioGenerator
export DB_PASSWORD="<mot-de-passe-postgresql>"
export SWAM_E2E_KEK_CLEAR="<cle-de-test-autorisee>"
```

Compiler les modules :

```bash
bash deploiement/common/runtime/start-platform.sh build
```

Lancer et tester chaque étape séparément :

```bash
bash deploiement/swam/01-start-issuer.sh
bash deploiement/swam/02-start-member.sh
bash deploiement/swam/03a-bootstrap-issuer.sh
bash deploiement/swam/03b-bootstrap-member.sh
bash deploiement/swam/03c-signon-and-key-exchange.sh
bash deploiement/swam/04-run-purchases.sh
bash deploiement/swam/05-run-lis-clearing.sh
bash deploiement/swam/06-stop-swam.sh
```

Ou lancer toute la chaîne :

```bash
bash deploiement/swam/swam-full-e2e.sh
```

Résultat global attendu :

```text
RESULTAT : SWAM FULL E2E PASSED
```

### Responsabilité de chaque script

#### `01-start-issuer.sh`

- compiler si le JAR est absent ;
- lancer `sg-swam-issuer` ;
- utiliser l'interface `SWAM_NETWORK_1` ;
- attendre le health check ;
- écrire PID et log dans `runtime/swam`.

#### `02-start-member.sh`

- lancer `sg-swam-acquirer` ;
- utiliser l'interface `SWAM_MEMBER_A` ;
- conserver la liaison permanente avec le switch ;
- attendre le health check ;
- écrire PID et log dans `runtime/swam`.

#### `03-bootstrap-keys.sh`

- appeler dans l'ordre les trois scripts unitaires ci-dessous pour l'E2E global.

#### `03a-bootstrap-issuer.sh`

- lire la clé de recette depuis une variable ou une saisie masquée ;
- paramétrer uniquement le côté switch/issuer ;
- afficher uniquement son KCV de contrôle, jamais la clé formée ou claire.

#### `03b-bootstrap-member.sh`

- lire séparément la clé de recette ;
- paramétrer uniquement le côté membre/acquirer ;
- afficher uniquement son KCV de contrôle.

#### `03c-signon-and-key-exchange.sh`

- lancer le sign-on seulement après validation unitaire des deux côtés ;
- laisser le switch pousser les clés de session sur la liaison permanente ;
- vérifier la concordance des KCV switch/membre dans la base de recette.

Tout autre paramétrage doit suivre la même règle : configuration, contrôle et
preuve côté switch ; configuration, contrôle et preuve côté membre ; puis test
bilatéral. Une erreur d'un côté ne doit pas être masquée par le script global.

#### `04-run-purchases.sh`

- passer les achats membre vers issuer ;
- passer les achats inverses sur la même liaison permanente ;
- contrôler les réponses ISO et les écritures d'autorisation ;
- produire un résumé succès/échec.

#### `05-run-lis-clearing.sh`

- lancer les EOD membre et switch ;
- alimenter les tables de clearing ;
- générer les deux LIS outgoing ;
- intégrer chaque LIS du côté opposé ;
- exécuter le rapprochement ;
- créer un chargeback de chaque côté ;
- générer et intégrer une représentation ;
- vérifier l'équilibre comptable.

#### `06-stop-swam.sh`

- arrêter uniquement les processus démarrés par les scripts SWAM ;
- préserver les autres services Java du poste.

#### `swam-full-e2e.sh`

- appeler les scripts précédents dans l'ordre ;
- s'arrêter dès une erreur ;
- nettoyer les processus qu'il a démarrés ;
- afficher un bilan final unique.

### Sources historiques conservées

Les scripts numérotés réutilisent les règles et endpoints validés par :

```text
tests/swam/issuer/start-and-bootstrap.sh
tests/swam/acquirer/start-and-bootstrap.sh
deploiement/swam/swam-e2e.sh
deploiement/swam/swam-lis-e2e.sh
```

### Critères de finalisation SWAM

SWAM est considéré finalisé seulement si :

- les scripts fonctionnent depuis une copie fraîche du dépôt ;
- tous les chemins passent par les variables globales ;
- aucun secret n'est committé ;
- issuer et membre utilisent une liaison permanente unique ;
- les achats passent dans les deux sens ;
- les deux tables d'autorisation sont alimentées ;
- les deux EOD réussissent ;
- les LIS sortants sont générés et intégrés en croisé ;
- chargebacks et représentation sont validés ;
- la comptabilité est équilibrée ;
- le scénario automatisé termine sans processus orphelin ;
- les tests SID, LIS et frontend ne régressent pas.

## 8. Questions réservées au raccordement d'un switch externe

Cette section ne s'applique pas à la reprise locale sur un autre PC. Elle sert
uniquement si le responsable confirme explicitement le passage au Mode B.

Avant toute configuration distante, obtenir par un canal professionnel les
informations non sensibles suivantes :

1. environnement autorisé : homologation, certification, recette ou
   préproduction ; ne jamais supposer qu'une cible est la production ;
2. host/IP, port, VPN ou lien dédié, IP source à autoriser et règles firewall ;
3. transport TCP, TLS ou mTLS et autorité de certification attendue ;
4. version exacte des spécifications SID et LIS faisant foi ;
5. framing, longueur du message, ordre des octets, keep-alive `0800`, timeouts,
   jeu de caractères et packager ISO 8583 ;
6. identifiants de certification : BIN/IIN, code membre, NII, terminaux et
   acquéreurs de test ;
7. procédure de cérémonie et d'échange des clés, rôle de chaque HSM et acteur
   qui initie le bootstrap ;
8. plan de certification : opérations, ordre, PAN et montants de test, fenêtres,
   critères de validation, PV attendu et contact technique.

Peuvent être versionnés :

- paramètres non sensibles propres à l'environnement de certification ;
- identifiants techniques de test autorisés ;
- version de spécification et paramètres du packager ;
- modèle de configuration sans valeur secrète.

Ne doivent jamais être transmis dans une conversation ni ajoutés à Git :

- clés, composantes ZMK/KEK/ZPK/ZAK ou LMK ;
- mots de passe ;
- clés privées et fichiers de certificats privés ;
- PAN réels ou données de production.

Les secrets doivent passer par la cérémonie de clés, le HSM, un coffre ou le
canal sécurisé validé par le responsable. Tant que ces réponses et l'autorisation
de l'environnement ne sont pas obtenues, aucune tentative de connexion distante
ne doit être faite.

### 8.1 Tester notre membre avec un switch réel — scénario futur

Objectif : raccorder `sg-swam-acquirer`, qui représente notre membre bancaire, à
un véritable switch SWAM de certification ou d'homologation.

Ce scénario est documenté pour ne pas perdre la démarche, mais il ne doit pas
être exécuté pendant la reprise locale actuelle.

Prérequis obligatoires :

1. réussite complète du Mode A local ;
2. autorisation écrite d'utiliser l'environnement de certification ;
3. coordonnées réseau du switch et ouverture des flux ;
4. version officielle du SID et mapping ISO 8583 confirmés ;
5. identifiants membre et données de test attribués par le switch ;
6. procédure HSM et échange de clés réalisée par les acteurs habilités ;
7. plan de certification et contact technique côté switch ;
8. sauvegarde de la configuration locale avant tout changement.

Démarche prévue :

1. créer une configuration d'environnement dédiée, sans secret dans Git ;
2. désactiver le démarrage de notre `sg-swam-issuer` local pour ce scénario ;
3. configurer `sg-swam-acquirer` vers le host et le port de certification ;
4. vérifier VPN, firewall et éventuellement TLS/mTLS ;
5. effectuer connexion, sign-on et echo test selon la procédure officielle ;
6. réaliser l'échange de clés via le canal HSM autorisé ;
7. exécuter le jeu de certification : achats, annulations, reversals et autres
   opérations imposées ;
8. rapprocher les traces ISO des deux côtés ;
9. exécuter EOD, génération du LIS membre et intégration du LIS switch si cela
   fait partie du périmètre de certification ;
10. produire le rapport ou PV demandé.

Critère de réussite : le switch réel accepte notre membre et le responsable
d'homologation valide les contrôles et le PV.

### 8.2 Tester notre issuer avec un membre réel — scénario futur

Objectif : utiliser `sg-swam-issuer` comme switch de test et connecter un système
membre réel ou représentatif exploité sur un autre poste.

Ce scénario est lui aussi uniquement préparé. Il ne doit pas être lancé pendant
la reprise locale actuelle.

Prérequis obligatoires :

1. réussite complète du Mode A local ;
2. environnement de test isolé et autorisation des deux responsables ;
3. IP source du membre, port d'écoute de notre issuer et règles firewall ;
4. version SID, framing, packager et timeouts communs ;
5. identité du membre créée dans notre base de test ;
6. procédure d'échange de clés de test convenue, sans transmission dans Git ou
   dans une conversation ;
7. cartes, terminaux, montants et scénario de test convenus ;
8. plan de retour arrière et journaux activés des deux côtés.

Démarche prévue :

1. sauvegarder la base et la configuration de référence ;
2. créer l'interface et le membre dans la base de l'environnement de test ;
3. configurer `sg-swam-issuer` pour écouter sur l'adresse et le port autorisés ;
4. vérifier la connectivité depuis le poste du membre ;
5. exécuter connexion, sign-on, echo test et échange de clés ;
6. faire passer les opérations du membre vers notre issuer ;
7. tester les messages initiés par notre issuer sur la même liaison permanente ;
8. comparer requêtes, réponses, MAC, traces et journaux des deux systèmes ;
9. lancer l'EOD switch et produire le LIS outgoing destiné au membre ;
10. intégrer le LIS outgoing du membre, puis contrôler rapprochement, litiges,
    chargebacks, représentations et comptabilité selon le périmètre convenu.

Critère de réussite : le membre réel échange les messages SID et LIS attendus
avec notre issuer, et les deux parties signent le résultat de test.

### 8.3 Décision de lancement

Ces deux scénarios externes sont volontairement différés. Une nouvelle session
doit seulement les préparer ou les relire tant que le responsable n'a pas donné
explicitement :

- le scénario à lancer (`notre membre ↔ switch réel` ou
  `notre issuer ↔ membre réel`) ;
- l'environnement autorisé ;
- la fenêtre de test ;
- les contacts responsables ;
- l'accord pour effectuer les changements réseau et les échanges HSM.

En l'absence de ces éléments, rester en Mode A local et ne réaliser aucun test
externe.

## 9. Variables globales

Les valeurs communes sont définies dans :

```text
deploiement/common/runtime/platform-env.sh
```

### 9.1 Détecter les chemins du poste

Depuis Git Bash et la racine du dépôt :

```bash
bash deploiement/common/runtime/detect-env.sh
```

Ce mode est en lecture seule. Il recherche sur `C:`, `D:` et `F:` :

- la racine Git du dépôt ;
- PostgreSQL et `psql.exe` ;
- le JDK et sa version ;
- Maven autonome ou celui embarqué par IntelliJ ;
- Node.js et npm ;
- les valeurs PostgreSQL locales par défaut.

Il n'affiche et ne recherche aucun mot de passe ou clé.

Pour Java, un candidat n'est accepté que s'il contient à la fois `java` et
`javac`. Les chemins JBR/JRE d'IntelliJ (`jbr`, `idea-*`, `IntelliJ`,
`plugins`) sont rejetés. Une valeur `JAVA_HOME_DIR` déjà exportée reste
prioritaire si elle désigne un vrai JDK conforme.

Le projet compile avec `source=21` et `target=21` :

- JDK 21 est la version de référence supportée sur LAB/DEV et RECETTE ;
- un JDK supérieur, notamment JDK 26, est accepté provisoirement en RECETTE
  seulement si le build Maven complet réussit ;
- un JDK inférieur à 21 ou un simple JRE/JBR est refusé.

Pour Node.js, le détecteur valide la racine contenant `node.exe` et `npm`, et ne
retient pas un sous-répertoire de shims Corepack.

La recherche générique commence par le lecteur du dépôt et progresse par
profondeurs. Sur un poste volumineux, la limiter explicitement :

```bash
export DETECT_DRIVES="/f /d"
export DETECT_MAX_DEPTH=7
bash deploiement/common/runtime/detect-env.sh
```

Ces variables contrôlent uniquement la détection et ne sont pas des chemins
applicatifs.

### 9.2 Créer la configuration locale

Après vérification des chemins proposés :

```bash
bash deploiement/common/runtime/detect-env.sh --write platform.env
```

Le fichier créé à la racine est local et ignoré par Git. Si un chemin n'a pas
été détecté, corriger manuellement la copie de
`deploiement/common/runtime/platform.env.example`.

La même commande génère `platform-path.sh`, également ignoré par Git. Ce script
charge la configuration locale et ajoute au `PATH` :

- `$JAVA_HOME_DIR/bin` ;
- `$MAVEN_HOME/bin` ;
- `$NODE_HOME` ;
- `$POSTGRES_HOME/bin`.

Ne jamais ajouter `DB_PASSWORD`, une clé claire ou un autre secret dans
`platform.env`. Même si quelqu'un y ajoute `DB_PASSWORD` par erreur,
`platform-env.sh` refuse de le charger.

Pour régénérer volontairement un fichier existant :

```bash
export OVERWRITE_PLATFORM_ENV=true
bash deploiement/common/runtime/detect-env.sh --write platform.env
unset OVERWRITE_PLATFORM_ENV
```

### 9.3 Début de chaque session RECETTE

L'opérateur exécute uniquement :

```bash
cd /f/MoneyCore/ScenarioGenerator
source platform-path.sh
export DB_PASSWORD="<mot-de-passe-saisi-par-l'opérateur>"
```

Le chemin `/f/...` est un exemple : utiliser l'emplacement réel du clone.
`platform-path.sh` doit être chargé avec `source` pour modifier le terminal
courant. La commande `bash platform-path.sh` ne convient pas.

Ordre de priorité :

1. variables déjà définies ou exportées dans le terminal ;
2. valeurs non sensibles de `platform.env` ;
3. valeurs par défaut de `platform-env.sh`.

Le mot de passe reste limité à la session du terminal et ne doit être ni écrit
dans un fichier ni envoyé dans une conversation.

### 9.4 Valeurs disponibles

Elles peuvent toujours être surchargées sans modifier les scripts :

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

### 9.5 Autorisation des clés claires de test en RECETTE

Pour les seuls environnements LAB/DEV et RECETTE, l'opérateur RECETTE est
explicitement autorisé à bootstrapper une clé **claire de test** avec
`SWAM_E2E_KEK_CLEAR`, séparément :

```bash
bash deploiement/swam/03a-bootstrap-issuer.sh
bash deploiement/swam/03b-bootstrap-member.sh
bash deploiement/swam/03c-signon-and-key-exchange.sh
```

Cette autorisation est limitée aux clés synthétiques dédiées aux tests. La valeur
doit être saisie ou exportée localement dans le terminal, ne doit jamais être
écrite dans `platform.env`, Git, un rapport ou une conversation, et doit être
remplacée par une cérémonie HSM pour tout raccordement réel.

## 10. Compiler et lancer la plateforme

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

## 11. Lancer le frontend

Dans un autre terminal Git Bash :

```bash
bash deploiement/frontend/start-frontend.sh
```

Puis ouvrir :

```text
http://localhost:4200
```

## 12. Tests à lancer

### 12.1 Tests Maven complets

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

### 12.2 E2E frontend Playwright

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

### 12.3 E2E SWAM LIS

```bash
export SWAM_E2E_KEK_CLEAR="<cle-de-test-autorisee>"
bash deploiement/swam/swam-lis-e2e.sh
```

Résultat attendu :

```text
RESULTAT : PASSED (36 controles)
```

La clé ne doit jamais être inscrite dans un fichier versionné.

### 12.4 E2E SWAM SID

```bash
bash deploiement/swam/swam-e2e.sh
```

## 13. Contrôles avant un commit

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

## 14. Première action obligatoire pour la prochaine session

1. vérifier `git status` et la branche ;
2. récupérer la dernière version depuis GitHub ;
3. lire les documents SWAM et les deux spécifications ;
4. lancer le build global ;
5. exécuter les scripts SWAM numérotés un par un ;
6. corriger toute erreur détectée sans contourner les contrôles ;
7. exécuter `swam-full-e2e.sh` ;
8. valider le clearing LIS complet ;
9. relancer les tests SID, LIS et frontend ;
10. committer et pousser toute correction SWAM ;
11. seulement ensuite reprendre le portail et les autres modules.
