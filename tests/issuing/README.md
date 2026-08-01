# Recette connectee Issuing

Ces scripts reproduisent sur une machine de recette la procedure validee
localement avec PostgreSQL et des services Java separes.

## Securite

- Aucun mot de passe, PAN de recette, PIN, bloc PIN ou cle HSM n'est stocke
  dans le depot.
- Les mots de passe sont fournis par l'environnement de la session Git Bash.
- Les logs et PID sont ecrits sous `runtime/`, hors Git.
- L'installation est append-only : une migration dont le marqueur existe est
  ignoree ; une erreur PostgreSQL interrompt immediatement la procedure.

## Installation de la base

Creer d'abord le fichier local unique de configuration :

```bash
mkdir -p runtime/issuing-connected-e2e
cp tests/issuing/connected-e2e.env.example \
  runtime/issuing-connected-e2e/connected-e2e.env
```

Renseigner ensuite uniquement le fichier sous `runtime/`. Il constitue le
document de configuration de la machine RECETTE. Il est ignore par Git et
charge automatiquement par les scripts d'installation, de demarrage et de
provisionnement. Si une adresse, un mot de passe, une carte sandbox ou une
limite memoire change, modifier manuellement cette copie locale avant le
prochain passage. Ne pas modifier le modele pour y mettre les valeurs de la
machine. Un autre emplacement peut etre utilise avec
`ISSUING_E2E_CONFIG_FILE=/chemin/absolu/fichier.env`.

Variables minimales contenues dans ce fichier :

```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=scenariogenerator
DB_USER=postgres
DB_PASSWORD='<mot-de-passe-administrateur>'
CARD_ISSUING_DB_PASSWORD='<mot-de-passe-role-issuing>'
```

Puis :

```bash
bash tests/issuing/install-issuing-db.sh
```

Le résultat attendu est 12 tables `issuing_*`, toutes possedees par
`card_issuing_user`, avec V1 a V6 appliquees.

## Demarrage des services

Construire d'abord les JAR :

```bash
"$MAVEN" -f pom.xml \
  -pl sg-card-issuing,sg-way-pos-server,sg-swam-issuer,sg-mc-dmas-mastercard \
  -am package -DskipTests -Dmaven.repo.local="$MAVEN_REPO"
```

Verifier les variables ServerPOS dans la copie locale non suivie :

```bash
WAY_POS_DB_PASSWORD='<mot-de-passe-waypos>'
WAY_POS_PAN_PEPPER='<pepper-recette>'
WAY_POS_OUTBOX_KEY_HEX='<64-caracteres-hex>'
```

Demarrer et arreter :

```bash
bash tests/issuing/start-connected-services.sh
bash tests/issuing/stop-connected-services.sh
```

Le lanceur attend les endpoints de sante avant de passer au service suivant.
Un `SUCCESS` n'est affiche que si Issuing, ServerPOS, SWAM et DMAS sont tous
demarres. Les ports SWAM et DMAS restent lus depuis leurs tables d'interface.
Chaque tentative HTTP possede un delai de connexion de 2 secondes et un delai
total de 3 secondes afin qu'un port ouvert mais non repondant ne bloque pas la
procedure.

Les processus Java sont limites par defaut a 256 Mio de heap et 192 Mio de
metaspace chacun, afin de permettre leur execution simultanee sur la machine
de recette. La valeur peut etre adaptee sans modifier le depot :

```bash
export ISSUING_E2E_JAVA_TOOL_OPTIONS='-Xms64m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC'
```

Le profil Spring `connected-e2e` ouvre les endpoints HTTP uniquement pour ce
harnais et les lie a `127.0.0.1`. Il ne doit pas etre utilise sur une instance
exposee au reseau.

Par defaut, le test local cree un LMK WayPos dedie sous son repertoire
`runtime`. Une recette utilisant un LMK deja provisionne doit definir
`ISSUING_E2E_WAY_POS_LMK_FILE` et positionner
`ISSUING_E2E_WAY_POS_LMK_REBUILD=false`. Le LMK DMAS n'est jamais reutilise
implicitement par WayPos.

La preparation des donnees et l'execution transactionnelle multi-canal sont
realisees par le harnais E2E du meme repertoire ; elles ne doivent pas etre
remplacees par une approbation fictive si un service est indisponible.

## Provisionnement fonctionnel

Le PAN sandbox n'a aucune valeur dans le modele et n'est jamais affiche. Le
definir dans la copie locale :

```bash
ISSUING_E2E_PAN='<pan-recette>'
ISSUING_E2E_EXPIRY='<AAMM>'
ISSUING_E2E_CURRENCY='504'
ISSUING_E2E_BALANCE_MINOR='100000'
```

Puis executer, sans exporter manuellement chaque variable :

```bash
bash tests/issuing/provision-connected-e2e.sh
```

Le script cree de facon idempotente les produits, contrats, cartes, comptes
Core Banking sandbox et endpoints actifs pour ServerPOS, SWAM et DMAS. Les
ports cibles Issuing sont persistants dans `issuing_interface_endpoint`.
