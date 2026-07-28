# Déploiement SWAM

- `swam-e2e.sh` : scénario E2E SWAM SID.
- `swam-lis-e2e.sh` : scénario E2E SWAM LIS bilatéral.
- `migration_v1.3.0_*` : structure initiale SWAM.
- `migration_v1.4.0_*` : interfaces, traces et transactions SID.
- `migration_v1.5.0_swam_cards_by_owner.sql` : séparation physique des cartes
  du switch (`issuer_swam_cards`) et du membre (`acquirer_swam_cards`).
- `swam_cartes_test.sql` : cartes de test SWAM.

Lancer les scripts depuis la racine du projet afin de conserver les chemins par
défaut.

## Test par étapes

```bash
export DB_PASSWORD="<mot-de-passe-postgresql>"
export SWAM_E2E_KEK_CLEAR="<cle-de-test-autorisee>"

bash deploiement/swam/01-start-issuer.sh
bash deploiement/swam/02-start-member.sh
bash deploiement/swam/03a-bootstrap-issuer.sh
bash deploiement/swam/03b-bootstrap-member.sh
bash deploiement/swam/03c-signon-and-key-exchange.sh
bash deploiement/swam/04-run-purchases.sh
bash deploiement/swam/05-run-lis-clearing.sh
bash deploiement/swam/06-stop-swam.sh
```

Le bootstrap est volontairement séparé : paramétrage du switch, paramétrage du
membre, puis seulement sign-on et échange des clés. Le script
`03-bootstrap-keys.sh` enchaîne ces trois contrôles pour l'E2E automatisé.

## Test complet

```bash
bash deploiement/swam/swam-full-e2e.sh
```

Les PID et logs des scripts numérotés sont placés dans `runtime/swam`.

Les fichiers LIS E2E sont créés sous
`$ROOT/runtime/e2e/<identifiant-execution>`. Le chemin est converti avec
`cygpath` sous Git Bash : un dépôt installé sur `F:` produit donc des chemins
`F:/...`, sans valeur `D:/` codée dans le script.

`06-stop-swam.sh` utilise d'abord les PID enregistrés, puis contrôle les ports
SID et LIS (`8510`, `8511`, `8094`, `8521`, `8522` par défaut). Il échoue si un
port reste occupé. Dans ce cas, le relancer depuis un terminal disposant des
mêmes droits Windows que ceux utilisés pour démarrer les services.

Pour un contrôle isolé, la liste des ports peut être remplacée :

```bash
export SWAM_STOP_PORTS="18510 18511 18094 18521 18522"
bash deploiement/swam/06-stop-swam.sh
```

Le scénario LIS peut réutiliser des services LIS déjà lancés, notamment pour
une qualification sur des ports isolés :

```bash
export SWAM_LIS_MANAGE_SERVICES=false
bash deploiement/swam/05-run-lis-clearing.sh
```
