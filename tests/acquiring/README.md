# Tests Acquisition TPE et e-commerce

Le guide opérateur complet, avec les schémas, la configuration et les commandes
Git Bash, est disponible dans
[`TEST_ACQUISITION_TPE_ET_ECOMMERCE.md`](TEST_ACQUISITION_TPE_ET_ECOMMERCE.md).

Deux recettes Git Bash independantes sont fournies :

- `tests/acquiring/pos-e2e/` pour un achat TPE passant par POS Simulator,
  ServerPOS et Issuing ;
- `tests/acquiring/ecommerce-e2e/` pour un achat e-commerce local Issuing ou
  passant par SWAM/DMAS Mastercard avec une carte confrère distincte.

## Achat TPE de bout en bout

Depuis Git Bash, a la racine du depot :

```bash
bash ./tests/acquiring/pos-e2e/run-all.sh
```

Pour garder les services ouverts et suivre les journaux, lancer separement :

```bash
bash ./tests/acquiring/pos-e2e/00-build.sh
bash ./tests/acquiring/pos-e2e/01-start.sh
bash ./tests/acquiring/pos-e2e/02-provision.sh
bash ./tests/acquiring/pos-e2e/03-purchase.sh
bash ./tests/acquiring/pos-e2e/04-tail-logs.sh
bash ./tests/acquiring/pos-e2e/05-stop.sh
```

La recette du 1er aout 2026 a retourne `RC=00`.

## Achat e-commerce de bout en bout

Carte LanaCash, route locale directe :

```bash
bash ./tests/acquiring/ecommerce-e2e/run-all.sh LOCAL_ISSUING
```

Carte confrere SWAM :

```bash
bash ./tests/acquiring/ecommerce-e2e/run-all.sh SWAM
```

Carte confrere DMAS Mastercard :

```bash
bash ./tests/acquiring/ecommerce-e2e/run-all.sh DMAS_MASTERCARD
```

Les etapes peuvent aussi etre lancees separement avec le nom de route en
argument : `00-build-and-install.sh`, `01-start.sh`, `02-provision.sh`,
`03-purchase.sh`, `04-tail-logs.sh` et `05-stop.sh`.

Les trois routes ont ete validees le 1er aout 2026 avec `RC=00` :
`LOCAL_ISSUING`, `DMAS_MASTERCARD` et `SWAM`. Les routes confreres refusent de
reutiliser la carte locale et lisent respectivement `SWAM_E2E_PAN/EXPIRY` et
`DMAS_E2E_PAN/EXPIRY` dans la configuration locale ignoree par Git.

Le 3-D Secure est volontairement reporte : la recette impose
`authenticationStatus=NOT_PERFORMED` et ne fabrique ni ECI, ni CAVV. La route
Visa est egalement fail-closed jusqu'a son implementation.

## Tests automatises

Depuis la racine du depot, avec le Maven embarque :

```powershell
& 'D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd' `
  -o -nsu -f pom.xml `
  -pl sg-card-issuing,sg-acquiring,sg-way-pos-server -am test `
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

La campagne finale du 1er aout 2026 a execute 133 tests sans echec sur Common,
Issuing, Acquisition, le simulateur e-commerce, DMAS membre/reseau et SWAM
membre/switch. Les validations anterieures restent documentees dans
`REPRISE_ACQUIRING.md`.

## Migrations PostgreSQL

Appliquer dans l'ordre :

1. `sql/issuing/V7__generalize_payment_contract.sql` ;
2. `sql/acquiring/V1__create_acquiring_foundation.sql`.
3. `sql/acquiring/V2__create_ecommerce_transactions.sql`.

La migration V7 conserve les contrats Issuing existants et leur affecte le
type `ISSUING_CARD`. La migration Acquisition ajoute les tables metier sans
dupliquer la table de contrat.

## Recette connectee ServerPOS

La recette doit demarrer ServerPOS sur `8530/8531`, puis Acquisition sur
`8550` avec :

```text
ACQUIRING_SERVER_POS_ENABLED=true
ACQUIRING_SERVER_POS_BASE_URL=http://127.0.0.1:8530
```

Ne jamais mettre les mots de passe ou les cles dans ce document. Ils restent
dans le fichier local ignore par Git
`runtime/issuing-connected-e2e/connected-e2e.env`.

Controles attendus :

- `GET http://127.0.0.1:8530/api/routing/v1/health` retourne `UP` ;
- `GET http://127.0.0.1:8550/api/acquiring/v1/health` retourne `UP` ;
- `GET http://127.0.0.1:8550/api/acquiring/v1/capabilities` annonce
  `serverPosProjection=true` ;
- le provisioning passe le terminal de `ASSIGNED` ou `PROVISIONING` a
  `READY` ;
- l'activation le passe a `ACTIVE` ;
- `pos_terminal_profiles` contient le TID/MID derives du contrat Acquisition.

La recette connectee du 31 juillet 2026 a valide la reprise idempotente d'un
terminal `PROVISIONING` et sa projection finale dans ServerPOS. Les deux
services ont ete arretes a la fin du test.
