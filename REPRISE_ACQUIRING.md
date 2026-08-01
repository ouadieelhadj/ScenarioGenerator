# Reprise du chantier Acquisition TPE et e-commerce

Derniere mise a jour : 1er aout 2026.

## Jalon atteint

Le socle Acquisition est implemente et teste : administration des produits,
commercants, points de vente, contrats, terminaux, affectations, boutiques et
profils e-commerce.

Le modele de contrat est commun avec Issuing dans `payment_contract` et se
distingue par `contract_type` (`ISSUING_CARD`, `ACQUIRING_MERCHANT`,
`ACQUIRING_DEVICE`). Les lignes Issuing existantes ont ete conservees pendant
la migration PostgreSQL.

Acquisition est proprietaire du commercant et du terminal. ServerPOS ne
conserve qu'une projection technique TID/MID/configuration creee pendant le
provisioning.

L'autorisation d'achat e-commerce est implementee avec un simulateur dedie et
trois routes resolues par la meme table BIN que ServerPOS :
`LOCAL_ISSUING`, `DMAS_MASTERCARD` et `SWAM`. Une carte emise localement va
directement vers Issuing ; une carte confrere utilise une carte de test
distincte et le simulateur explicite de sa banque emettrice. Le 3DS et Visa
sont reportes ; le flux courant impose `NOT_PERFORMED`.

## Validation effectuee

- migration `sql/issuing/V7__generalize_payment_contract.sql` appliquee sur
  PostgreSQL : 3 contrats Issuing conserves et types `ISSUING_CARD` ;
- migration `sql/acquiring/V1__create_acquiring_foundation.sql` appliquee ;
- cycle API PostgreSQL valide pour TPE et e-commerce ;
- non-regression Maven : 142 tests, 0 echec, 0 erreur, 0 ignore ;
- package `sg-acquiring` construit avec succes ;
- E2E reel Acquisition vers ServerPOS valide : projection active, reprise
  idempotente depuis `PROVISIONING`, terminal final `ACTIVE`, profil ServerPOS
  present avec TID/MID concordants ;
- ports 8530, 8531 et 8550 libres apres le test.
- campagne cible Common/DMAS/SWAM/Acquisition du 1er aout 2026 :
  88 tests, aucun echec, aucune erreur ;
- campagne finale elargie Common/Issuing/Acquisition/simulateur/DMAS/SWAM :
  133 tests, aucun echec, aucune erreur, aucun test ignore ;
- correction de normalisation du buffer MAC SWAM avant/apres packing,
  couverte par un test de symetrie ;
- achat TPE POS Simulator -> ServerPOS -> Issuing approuve `RC=00` ;
- l'ancien achat e-commerce SWAM avec la carte locale est invalide comme
  preuve metier et n'est plus presente comme E2E cible ;
- achat e-commerce Simulator -> Acquisition -> Issuing local approuve
  `RC=00`, route `LOCAL_ISSUING`, `3DS=NOT_PERFORMED` ;
- achat e-commerce confrere via membre et reseau DMAS approuve `RC=00`, route
  `DMAS_MASTERCARD`, apres sign-on, echange PEK et controle des KCV ;
- achat e-commerce confrere via membre et switch SWAM approuve `RC=00`, route
  `SWAM`, apres sign-on et controle des KCV des cles de session ;
- DMAS membre demarre avec 7 repositories apres ajout explicite de
  `McDmasCardRepository` a son perimetre ;
- `DMAS_ADMIN_PASSWORD` est maintenant present dans le fichier local ignore ;
- scripts DMAS et SWAM corriges pour exiger des cartes confreres distinctes ;
- arret Git Bash DMAS et SWAM corrige pour distinguer les PID POSIX des PID
  Windows ; tous les ports de test ont ete liberes.

Commande de non-regression :

```text
mvn.cmd -o -nsu -f pom.xml -pl sg-card-issuing,sg-acquiring,sg-way-pos-server -am test -Dmaven.repo.local=D:\MoneyCore\.m2\repository
```

## Fichiers du chantier

- nouveau module `sg-acquiring/` ;
- nouveau module `sg-ecommerce-simulator/` ;
- types communs dans `sg-common/.../contract/` ;
- adaptation du domaine Issuing a `payment_contract` ;
- migrations `sql/issuing/V7__generalize_payment_contract.sql` et
  `sql/acquiring/V1__create_acquiring_foundation.sql` et
  `sql/acquiring/V2__create_ecommerce_transactions.sql` ;
- scripts `tests/acquiring/pos-e2e/` et
  `tests/acquiring/ecommerce-e2e/` ;
- architecture `documents/design/acquiring/ARCHITECTURE.md` ;
- procedure `tests/acquiring/README.md`.
- guide illustre et commandes Git Bash dans
  `tests/acquiring/TEST_ACQUISITION_TPE_ET_ECOMMERCE.md`.

## Premier travail non termine

Les parcours d'autorisation Acquisition TPE et e-commerce local, DMAS et SWAM
sont termines et valides. Le prochain jalon fonctionnel est la route Visa,
puis le module 3-D Secure. Le clearing et le settlement restent egalement a
developper dans des jalons separes.

## Processus actifs

Aucun processus Acquisition, POS, SWAM ou DMAS lance pour cette validation ne
reste actif.

## Fichiers modifies dans la derniere reprise

- routage : `sg-common/.../ecommerce/EcommerceNetworkRoute.java`,
  `sg-acquiring/.../service/EcommerceRouteResolver.java`,
  `EcommerceTransactionService.java` et `HttpEcommerceNetworkAdapter.java` ;
- simulation confrere : `McDmasMastercardHandler.java`,
  `SwamJposServer.java` et leurs `application.yml` ;
- migration : `sql/acquiring/V2__create_ecommerce_transactions.sql` ;
- scripts : `tests/acquiring/ecommerce-e2e/` et les deux `run-all.sh` ;
- arret multiplateforme : `deploiement/mastercard/dmas-dmc/lib-dmas-dmc.sh`,
  `deploiement/swam/lib-swam.sh` et ouverture M2M DMAS strictement reservee au
  profil de test ;
- documentation : `documents/design/acquiring/ARCHITECTURE.md`,
  `tests/acquiring/README.md` et
  `tests/acquiring/TEST_ACQUISITION_TPE_ET_ECOMMERCE.md` ;
- tests : `EcommerceRouteResolverTest.java` et adaptation de
  `EcommerceTransactionServiceTest.java`.

Commandes et resultats exacts :

```text
mvn.cmd -o -nsu -f pom.xml -pl sg-acquiring,sg-mc-dmas-mastercard,sg-swam-issuer -am test -Dmaven.repo.local=D:\MoneyCore\.m2\repository
BUILD SUCCESS - 88 tests - 0 echec - 0 erreur

mvn.cmd -o -nsu -f pom.xml -pl sg-card-issuing,sg-acquiring,sg-ecommerce-simulator,sg-mc-dmas-member,sg-mc-dmas-mastercard,sg-swam-acquirer,sg-swam-issuer -am test -Dmaven.repo.local=D:\MoneyCore\.m2\repository
BUILD SUCCESS - 133 tests - 0 echec - 0 erreur - 0 ignore

bash ./tests/acquiring/ecommerce-e2e/run-all.sh LOCAL_ISSUING
ACHAT APPROUVE - route=LOCAL_ISSUING - RC=00 - 3DS=NOT_PERFORMED

bash ./tests/acquiring/ecommerce-e2e/run-all.sh DMAS_MASTERCARD
ACHAT APPROUVE - route=DMAS_MASTERCARD - RC=00 - 3DS=NOT_PERFORMED
Sign-on et echange dynamique PEK/KCV valides

bash ./tests/acquiring/ecommerce-e2e/run-all.sh SWAM
ACHAT APPROUVE - route=SWAM - RC=00 - 3DS=NOT_PERFORMED
Sign-on et KCV des cles de session valides

bash ./deploiement/swam/06-stop-swam.sh
Tous les services SWAM arretes et ports libres
```

Les ports 8084, 8094, 8500, 8510, 8511, 8530, 8531, 8540, 8550 et 8551
ont ete verifies libres apres l'execution.
