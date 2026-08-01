# Instructions à la RECETTE — du dépôt à la finalisation des tests

## 1. Objet

Ce guide permet à l'agent de la machine RECETTE de :

1. récupérer la version publiée du projet sans écraser un travail local ;
2. contrôler les prérequis techniques et cryptographiques ;
3. exécuter la non-régression locale ;
4. démarrer `WayPosServer` et `wayPosSimulator` ;
5. exécuter l'E2E avec PowerShell ou Git Bash ;
6. diagnostiquer le premier écart réel ;
7. produire un verdict et un compte rendu assaini.

Les deux procédures E2E sont fonctionnellement équivalentes :

- `tests/waypos/Invoke-WayPosE2E.ps1` ;
- `tests/waypos/Invoke-WayPosE2E.sh`.

L'agent RECETTE peut librement effectuer les tests avec **PowerShell ou
Git Bash**. Il choisit la variante disponible sur sa machine, n'en exécute
qu'une par passage E2E et indique obligatoirement son choix dans le compte
rendu. Aucun résultat ne doit dépendre du shell choisi.

Le résultat de référence avant RECETTE est de **129 tests réussis et
0 échec**.

## 2. Règles impératives

- Ne jamais utiliser `git reset --hard`, `git clean` ou une commande
  supprimant les modifications locales.
- Ne jamais inventer une carte, une clé, un KCV, un PIN, un ARQC, un ARPC,
  un ATC ou une réponse d'approbation.
- Ne jamais afficher ou copier dans un compte rendu :
  - un mot de passe ;
  - une clé claire ou une clé sous LMK ;
  - un PAN complet ;
  - un PIN ou PIN block ;
  - un pepper ;
  - un DE55 complet.
- Ne pas placer les secrets dans le dépôt, un script, l'historique du shell
  ou les arguments d'une commande.
- Afficher uniquement `PRESENT` ou `ABSENT` pendant le contrôle des secrets.
- Arrêter les tests au premier échec non expliqué.
- Ne déclarer `E2E RECETTE VALIDE` qu'après réussite de toutes les étapes.
- Ne committe et ne pousse aucune correction sans autorisation explicite.

## 3. Récupérer la version du dépôt

Le dépôt est normalement déjà présent sur la machine RECETTE. Ne pas le
recloner automatiquement.

Depuis la racine du dépôt :

```bash
git status --short --branch
git remote -v
git fetch origin
```

Branche de référence :

```text
codex/portail-rbac-maker-checker
```

Si le worktree est propre :

```bash
git switch codex/portail-rbac-maker-checker
git pull --ff-only origin codex/portail-rbac-maker-checker
git log -5 --oneline --decorate
```

Si `git status` affiche des modifications :

1. ne rien supprimer ;
2. lister les fichiers concernés ;
3. vérifier à qui ils appartiennent ;
4. demander une décision avant le `switch` ou le `pull`.

Après la mise à jour, vérifier la présence de :

```text
AGENTS.md
POS_REPRISE.md
documents/design/waypos/VALIDATION_MATRIX.md
tests/waypos/README.md
tests/waypos/Invoke-WayPosE2E.ps1
tests/waypos/Invoke-WayPosE2E.sh
```

## 4. Documents à lire avant tout test

Lire intégralement, dans cet ordre :

1. `AGENTS.md` ;
2. `POS_REPRISE.md` ;
3. `documents/design/waypos/VALIDATION_MATRIX.md` ;
4. `tests/waypos/README.md` ;
5. le script choisi :
   - `tests/waypos/Invoke-WayPosE2E.ps1`, ou
   - `tests/waypos/Invoke-WayPosE2E.sh`.

Ne pas modifier le code pendant cette lecture.

## 5. Préparer la machine RECETTE

### 5.1 Logiciels

Vérifier :

- Java 21 ou une version compatible validée par le projet ;
- Maven et son cache de dépendances ;
- PostgreSQL ;
- PowerShell pour la procédure `.ps1` ;
- Git Bash, `curl`, `timeout` et Python 3 pour la procédure `.sh`.

Commandes de contrôle :

```bash
git --version
java -version
```

PowerShell :

```powershell
& $MavenCmd -version
```

Git Bash :

```bash
"$MAVEN_CMD" -version
command -v curl
command -v timeout
command -v python
```

Adapter `MavenCmd` ou `MAVEN_CMD` au chemin réel de la machine. Sur le poste
de développement, la référence était :

```text
D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd
```

### 5.2 Services et ports

Ports attendus :

| Service | Port |
|---|---:|
| PostgreSQL | 5432 |
| WayPosServer REST | 8530 |
| WayPosServer ISO/jPOS | 8531 |
| wayPosSimulator REST | 8532 |

Avant le démarrage des applications, PostgreSQL doit répondre sur `5432`.
Les ports `8530`, `8531` et `8532` doivent être libres ou appartenir aux
processus WayPos attendus.

### 5.3 Base de données

Contrôler sans afficher le mot de passe :

- URL JDBC ;
- utilisateur ;
- base `scenariogenerator`, sauf configuration RECETTE différente ;
- présence du schéma WayPos créé par
  `sql/waypos/V1__create_way_pos.sql` ;
- droits de lecture, insertion, mise à jour et verrouillage ;
- absence d'erreur de validation JPA au démarrage.

Ne pas relancer une migration destructive sur une base partagée sans
validation de l'administrateur.

### 5.4 Configuration connectée Issuing / ServerPOS / SWAM / DMAS

La RECETTE conserve un document local unique de configuration :

```text
runtime/issuing-connected-e2e/connected-e2e.env
```

Lors de la première installation, le créer depuis le modèle versionné :

```bash
mkdir -p runtime/issuing-connected-e2e
cp tests/issuing/connected-e2e.env.example \
  runtime/issuing-connected-e2e/connected-e2e.env
```

Règles d'exploitation :

1. le modèle versionné contient les noms et les valeurs non sensibles ;
2. la copie `runtime` contient les valeurs propres à la machine RECETTE ;
3. les scripts DB, démarrage et provisionnement chargent cette copie
   automatiquement ;
4. si une information change, modifier manuellement uniquement la copie
   locale avant le prochain test ;
5. ne jamais committer, afficher dans un compte rendu ou transmettre cette
   copie renseignée ;
6. vérifier sa présence et ses droits d'accès avant chaque campagne.

Procédure connectée :

```bash
bash tests/issuing/install-issuing-db.sh
bash tests/issuing/start-connected-services.sh
bash tests/issuing/provision-connected-e2e.sh
```

Arrêt contrôlé :

```bash
bash tests/issuing/stop-connected-services.sh
```

## 6. Charger l'environnement contrôlé

Les 43 variables obligatoires sont définies dans les deux scripts. Elles
doivent être chargées dans le même environnement que celui utilisé pour
démarrer le serveur, le simulateur et le harnais.

### 6.1 Infrastructure et protection

```text
WAY_POS_DB_PASSWORD
WAY_POS_LMK_FILE
WAY_POS_OUTBOX_KEY_HEX
WAY_POS_PAN_PEPPER
```

### 6.2 Identité du TPE et mode MAC

```text
WAY_POS_TERMINAL_ID
WAY_POS_MERCHANT_ID
WAY_POS_CURRENCY
WAY_POS_MAC_MODE
```

### 6.3 Clés initiales du simulateur

```text
WAY_POS_TAK_HEX
WAY_POS_MASTER_KEY_ID
WAY_POS_MASTER_KEY_TYPE
WAY_POS_MASTER_KEY_HEX
```

### 6.4 TAK/TPK sous le LMK WayPos

```text
WAY_POS_E2E_TAK_UNDER_LMK
WAY_POS_E2E_TAK_KCV
WAY_POS_E2E_TAK_LENGTH
WAY_POS_E2E_TPK_UNDER_LMK
WAY_POS_E2E_TPK_KCV
WAY_POS_E2E_TPK_LENGTH
```

### 6.5 PVK et données PIN

```text
WAY_POS_E2E_PVK_A_UNDER_LMK
WAY_POS_E2E_PVK_A_KCV
WAY_POS_E2E_PVK_B_UNDER_LMK
WAY_POS_E2E_PVK_B_KCV
WAY_POS_E2E_PIN_BLOCK_HEX
WAY_POS_E2E_PIN_PVV
WAY_POS_E2E_PIN_PVKI
```

### 6.6 Carte locale et MDK

```text
WAY_POS_E2E_PAN
WAY_POS_E2E_EXPIRY
WAY_POS_E2E_AMOUNT
WAY_POS_E2E_AVAILABLE_BALANCE
WAY_POS_E2E_MDK_UNDER_LMK
WAY_POS_E2E_MDK_KCV
WAY_POS_E2E_MDK_LENGTH
WAY_POS_E2E_PAN_SEQUENCE
WAY_POS_E2E_ARPC_ARC_HEX
```

### 6.7 Quatre vecteurs EMV distincts

```text
WAY_POS_E2E_EMV_EOD_HEX
WAY_POS_E2E_EMV_REPEAT_HEX
WAY_POS_E2E_EMV_REVERSAL_HEX
WAY_POS_E2E_EMV_ADVICE_HEX
```

Les quatre vecteurs doivent être cohérents avec la même carte et posséder
des ATC chronologiquement croissants. La réutilisation d'un ARQC doit être
refusée par la protection anti-rejeu.

### 6.8 Prochaine TAK pour ANSI X9.17

```text
WAY_POS_E2E_NEXT_TAK_ID
WAY_POS_E2E_NEXT_TAK_X917_BLOCK_HEX
WAY_POS_E2E_NEXT_TAK_UNDER_LMK
WAY_POS_E2E_NEXT_TAK_KCV
WAY_POS_E2E_NEXT_TAK_LENGTH
```

Contrôler uniquement la présence :

PowerShell :

```powershell
$required = Select-String `
  -Path .\tests\waypos\Invoke-WayPosE2E.ps1 `
  -Pattern '"WAY_POS_[A-Z0-9_]+"' -AllMatches |
  ForEach-Object { $_.Matches.Value.Trim('"') } |
  Sort-Object -Unique

foreach ($name in $required) {
    $state = if ([string]::IsNullOrWhiteSpace(
        [Environment]::GetEnvironmentVariable($name))) {
        "ABSENT"
    } else {
        "PRESENT"
    }
    "$name=$state"
}
```

Git Bash :

```bash
while IFS= read -r name; do
  if [[ -n "${!name-}" ]]; then
    printf '%s=PRESENT\n' "$name"
  else
    printf '%s=ABSENT\n' "$name"
  fi
done < <(
  sed -n '/^required_names=(/,/^)/p' \
    tests/waypos/Invoke-WayPosE2E.sh |
  grep -E '^[[:space:]]*WAY_POS_[A-Z0-9_]+[[:space:]]*$' |
  tr -d '[:space:]'
)
```

## 7. Exécuter la non-régression

Depuis la racine du dépôt :

```powershell
& $MavenCmd -o -nsu -f pom.xml `
  -pl sg-way-pos-server,sg-way-pos-simulator,sg-mc-dmas-member,sg-swam-acquirer,sg-mc-sms-acquirer,sg-mc-sms-issuer `
  -am test `
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

Équivalent Git Bash :

```bash
"$MAVEN_CMD" -o -nsu -f pom.xml \
  -pl sg-way-pos-server,sg-way-pos-simulator,sg-mc-dmas-member,sg-swam-acquirer,sg-mc-sms-acquirer,sg-mc-sms-issuer \
  -am test \
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

Résultat attendu :

| Module | Tests attendus |
|---|---:|
| sg-common | 63 |
| sg-mc-dmas-member | 3 |
| sg-swam-acquirer | 11 |
| sg-mc-sms-acquirer | 3 |
| sg-way-pos-server | 34 |
| sg-way-pos-simulator | 15 |
| Total | **129** |

`sg-mc-sms-issuer` doit compiler ; il ne possède pas encore de test propre.

Si un test échoue :

1. ne pas lancer l'E2E ;
2. identifier le premier module et le premier test en échec ;
3. distinguer erreur fonctionnelle, dépendance Maven absente et problème
   d'environnement ;
4. fournir un extrait de log assaini ;
5. demander validation avant toute correction.

## 8. Construire les applications

PowerShell :

```powershell
& $MavenCmd -o -nsu -f pom.xml `
  -pl sg-way-pos-server,sg-way-pos-simulator `
  -am package -DskipTests `
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

Git Bash :

```bash
"$MAVEN_CMD" -o -nsu -f pom.xml \
  -pl sg-way-pos-server,sg-way-pos-simulator \
  -am package -DskipTests \
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

JAR attendus :

```text
sg-way-pos-server/target/sg-way-pos-server-1.0.0-SNAPSHOT.jar
sg-way-pos-simulator/target/sg-way-pos-simulator-1.0.0-SNAPSHOT.jar
```

## 9. Démarrer les services

Les deux applications doivent hériter du même environnement contrôlé.

Terminal 1 :

```bash
java -jar sg-way-pos-server/target/sg-way-pos-server-1.0.0-SNAPSHOT.jar
```

Attendre :

- connexion PostgreSQL réussie ;
- validation JPA réussie ;
- initialisation du LMK sans reconstruction non autorisée ;
- REST sur `8530` ;
- serveur ISO/jPOS sur `8531`.

Terminal 2 :

```bash
java -jar sg-way-pos-simulator/target/sg-way-pos-simulator-1.0.0-SNAPSHOT.jar
```

Attendre le REST sur `8532`.

Ne jamais activer `WAY_POS_LMK_REBUILD=true` sur une LMK RECETTE existante
sans autorisation de l'officier sécurité.

## 10. Contrôler la santé

PowerShell :

```powershell
Invoke-RestMethod http://localhost:8530/api/routing/v1/health
Invoke-RestMethod http://localhost:8532/api/simulator/v1/health
```

Git Bash :

```bash
curl -fsS http://localhost:8530/api/routing/v1/health
curl -fsS http://localhost:8532/api/simulator/v1/health
```

Les deux réponses doivent contenir `status=UP`. Ne poursuivre que si les
ports `8531` et `5432` répondent également.

## 11. Exécuter l'E2E

Choisir une seule des deux variantes pour un passage donné.

### 11.1 Variante PowerShell

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\tests\waypos\Invoke-WayPosE2E.ps1
```

Avec URLs personnalisées :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\tests\waypos\Invoke-WayPosE2E.ps1 `
  -ServerBaseUrl "http://localhost:8530" `
  -SimulatorBaseUrl "http://localhost:8532"
```

### 11.2 Variante Git Bash

Depuis une vraie session Git Bash :

```bash
./tests/waypos/Invoke-WayPosE2E.sh
```

Si le droit d'exécution Windows n'est pas disponible :

```bash
bash ./tests/waypos/Invoke-WayPosE2E.sh
```

Avec URLs personnalisées :

```bash
./tests/waypos/Invoke-WayPosE2E.sh \
  --server-base-url "http://localhost:8530" \
  --simulator-base-url "http://localhost:8532"
```

Ne pas lancer `bash.exe` directement depuis PowerShell. Ouvrir Git Bash ou
utiliser `git-bash.exe` afin d'initialiser correctement les chemins POSIX.

### 11.3 Provisioning déjà présent

Utiliser cette option uniquement si le dataset déjà provisionné a été
contrôlé et si HTTP 409 est attendu pour le terminal ou la carte :

PowerShell :

```powershell
& .\tests\waypos\Invoke-WayPosE2E.ps1 -AllowExistingProvisioning
```

Git Bash :

```bash
./tests/waypos/Invoke-WayPosE2E.sh --allow-existing-provisioning
```

## 12. Ordre des contrôles E2E

Le harnais réalise :

1. validation des 43 variables et de leurs formats ;
2. contrôle du fichier LMK ;
3. contrôle PostgreSQL ;
4. contrôle des health checks ;
5. provisioning du profil terminal ;
6. bootstrap TAK/TPK déjà chiffrées sous LMK ;
7. recalcul et comparaison des KCV par le HSM ;
8. provisioning PVK et carte/MDK ;
9. création de la route BIN locale `00000` ;
10. provisioning de la prochaine TAK ;
11. échange dynamique ANSI X9.17 ;
12. confirmation de toutes les clés importées ;
13. vérification du MAC de réponse et du MAC de confirmation ;
14. scénario `PURCHASE_EOD` ;
15. vérification PIN, ARQC et ARPC ;
16. reconciliation/EOD ;
17. scénario `PURCHASE_REPEAT` ;
18. contrôle du repeat exact et de l'idempotence ;
19. scénario `PURCHASE_REVERSAL` ;
20. contrôle du reversal ;
21. scénario `AUTHORIZATION_FINAL_ADVICE` ;
22. contrôle du final advice.

## 13. Critères PASS/FAIL

### PASS

- Les 129 tests locaux réussissent.
- Les applications démarrent sans reconstruction inattendue de LMK.
- Les deux health checks sont `UP`.
- Le key change retourne `00`.
- Le MAC de réponse est validé.
- La confirmation de clés est envoyée et acceptée.
- Tous les statuts de clés importées valent `0`.
- Le PIN est validé par le HSM.
- L'ARQC est accepté et l'ARPC retourné.
- Les quatre ATC sont cohérents et croissants.
- Repeat, reversal, advice et EOD terminent avec les codes attendus.

### FAIL

Un seul des événements suivants suffit :

- variable ou fichier LMK absent ;
- KCV incohérent ;
- service ou port indisponible ;
- réponse non corrélée ;
- MAC invalide ou absent ;
- PIN/ARQC/ARPC non vérifiable ;
- protection anti-rejeu déclenchée sur un vecteur supposé nouveau ;
- repeat non identique ;
- reversal/advice non idempotent ;
- reconciliation ou EOD incohérent ;
- valeur fictive nécessaire pour continuer.

## 14. Diagnostic au premier échec

Fournir uniquement :

1. date et heure ;
2. branche et commit ;
3. variante utilisée : PowerShell ou Git Bash ;
4. étape précise ;
5. commande exécutée sans secret ;
6. statut HTTP ou code ISO ;
7. message d'erreur assaini ;
8. dix à trente lignes utiles de logs assainies ;
9. diagnostic ;
10. correction proposée ;
11. décision : relancer, corriger ou escalader.

Ne pas relancer aveuglément une transaction financière incertaine. Appliquer
la stratégie repeat/reversal/advice prévue par le scénario.

## 15. Compte rendu final

Utiliser ce format :

```text
RECETTE WAYPOS

Branche :
Commit :
Date :
Machine :
Variante : PowerShell | Git Bash

Git update : PASS | FAIL
Prérequis : PASS | FAIL
Non-régression 129 tests : PASS | FAIL
Build WayPosServer : PASS | FAIL
Build wayPosSimulator : PASS | FAIL
PostgreSQL : PASS | FAIL
WayPosServer REST/ISO : PASS | FAIL
wayPosSimulator REST : PASS | FAIL
Bootstrap TAK/TPK et KCV : PASS | FAIL
ANSI X9.17 et confirmation : PASS | FAIL
MAC requête/réponse : PASS | FAIL
PIN : PASS | FAIL
ARQC/ARPC : PASS | FAIL
Anti-rejeu ATC : PASS | FAIL
Repeat : PASS | FAIL
Reversal : PASS | FAIL
Final advice : PASS | FAIL
Reconciliation/EOD : PASS | FAIL

Premier échec :
Diagnostic :
Action suivante :

Verdict : E2E RECETTE VALIDE | E2E RECETTE NON VALIDE
```

Le compte rendu ne doit contenir aucun secret ni PAN complet.

## 16. Finalisation

Si tout réussit :

1. arrêter proprement le simulateur puis le serveur si la fenêtre de test
   est terminée ;
2. conserver le compte rendu assaini ;
3. consigner le commit réellement testé ;
4. déclarer `E2E RECETTE VALIDE` ;
5. transmettre le compte rendu pour mise à jour de `POS_REPRISE.md`.

Si une étape échoue :

1. conserver les services nécessaires au diagnostic, selon les règles de la
   machine RECETTE ;
2. ne pas modifier les données sans validation ;
3. déclarer `E2E RECETTE NON VALIDE` ;
4. transmettre le premier échec et les preuves assainies ;
5. attendre l'autorisation avant correction, commit ou push.
