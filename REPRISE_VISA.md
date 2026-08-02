# Reprise du chantier Visa Online et Base II

## Etat au 2 aout 2026

Le premier increment fonctionnel sandbox est termine, teste et integre au
frontend et au processus de deploiement sur la branche
`codex/AddingVisaOnlineAndClearing`.

Applications deployables ajoutees :

- `sg-visa-online-member` (8564) ;
- `sg-visa-visanet-simulator` (8565) ;
- `sg-visa-base2-member` (8566) ;
- `sg-visa-base2-network-simulator` (8567).

Bibliotheques communes non deployables :

- `sg-visa-common` ;
- `sg-visa-base2-common`.

## Perimetre realise

- codec jPOS Visa Online avec corps ISO 8583 BCD/EBCDIC ;
- autorisation 0100/0110, reversal/advice et messages reseau du simulateur ;
- correlation transaction/correlation/idempotency, STAN et RRN ;
- DE38, DE39 et references Visa sandbox DE62 ;
- PAN masque dans le journal et aucune donnee sensible dans les logs ;
- refus explicite si le transport ou une donnee membre obligatoire manque ;
- fichier CTF Base II fixe a 168 octets ;
- TC90, TC05/TCR0, TC05/TCR5, TC91 et TC92 ;
- ARN de 23 chiffres avec controle Luhn ;
- validation structurelle, SHA-256, deduplication et acquittement reseau ;
- routage via `sg-visa-mastercard-gateway-simulator` ;
- integration dans `scenario-members-bundle.jar` et
  `scenario-simulators-bundle.jar` ;
- catalogue de deploiement et variables requises ;
- menus frontend Visa Online/Base II et deux ecrans simulateur dans LAB ;
- harnais Git Bash et manuel de test.

## Validation exacte

### Backend

Commande :

```powershell
& 'D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd' -o -nsu -f pom.xml -pl sg-deployment-core,sg-visa-mastercard-gateway-simulator,sg-visa-online-member,sg-visa-visanet-simulator,sg-visa-base2-member,sg-visa-base2-network-simulator -am test '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

Resultat : 83 tests, 0 echec, `BUILD SUCCESS` (dont le test du catalogue de
deploiement Visa ajoute apres la premiere execution agregee).

### Frontend

```powershell
npm.cmd run test:e2e
```

Resultat : build Angular reussi ; Playwright 7 tests reussis, 3 tests
historiques ignores, 0 echec.

### Bundles

```powershell
& 'D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd' -o -nsu -f pom.xml -pl sg-members-bundle,sg-simulators-bundle -am package '-Dmaven.test.skip=true' '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

Resultat : 27 modules construits, les deux bundles `SUCCESS`.

### E2E Git Bash

```bash
bash ./tests/visa/e2e/run-visa-online-base2.sh
```

Resultat : les cinq services sont montes, autorisation Visa Online approuvee
avec RC=00, rejeu Online identique, fichier Base II de 5 records accepte,
rejeu Base II detecte. Aucun PAN complet dans les logs et aucun service encore
actif apres le test.

Guide : `tests/visa/TEST_VISA_ONLINE_BASEII.md`.

## Frontieres de securite et de conformite

- provenance obligatoire `SIMULATED_NETWORK` ;
- les transports Visa sont desactives par defaut ;
- aucune connexion reelle Visa n'est revendiquee ;
- aucun secret, identifiant de recette ou vecteur officiel n'a ete invente ;
- le codec du premier increment couvre le corps ISO, pas l'en-tete ni le
  framing Visa certifie ;
- les journaux applicatifs du premier increment restent en memoire.

## Premier travail non termine

Raccorder un transport Visa reel et une persistence autoritative uniquement
quand les parametres officiels seront disponibles. Ensuite etendre Base II au
circuit certifie complet : ITF 170 si requis, Edit Package, VSS/TC46, VROL,
chargeback, pre-arbitrage et representment.

Ces sujets restent bloques par les specifications et codes Visa absents des
trois PDF disponibles. Ils ne doivent pas etre simules avec des valeurs
inventées puis presentes comme un raccordement reel.

## Git et processus

- branche : `codex/AddingVisaOnlineAndClearing` ;
- base initiale : `93e3898 feat(deployment): add managed deployment process` ;
- les PDF sources n'ont pas ete modifies ;
- les fichiers sensibles ou les chantiers DMAS/SWAM/WayPos hors perimetre
  restent exclus du commit Visa ;
- aucun processus Visa n'est actif a la fin de la validation.
