# Verdict validateur — premier import contrôlé WAY4 recette

Date : 2026-08-13

## Verdict

**GO FORMEL pour un premier import unique et contrôlé dans WAY4 recette CARSDB.**

Ce GO porte exclusivement sur le fichier :

- `xadvapl000100_00001.225` ;
- taille : 4 744 octets ;
- SHA-256 : `F36E0C13C72FAA3F1D4EB70F5028DBF0E6464FD0D38921C47531316A8EC7D267`.

Il ne vaut pas pour un autre fichier, un rejeu automatique, la production, une activation terminal ou une transaction réseau.

## Contrôles validés

- validation indépendante contre le XSD officiel : réussie ;
- SHA-256 du XSD principal : `F76E4927B2365B6A7B9FA9B7EE1B0CF28C87313CDE724BD6C6484673D0E8A680` ;
- structure minimale : 1 client, 1 contrat commerçant, 1 adresse et 1 contrat TPE ;
- quatre `Application/RegNumber` uniques ;
- bindings CARSDB confirmés par les exports Oracle ;
- MID `990001000000001`, TID `99000001` et contrat `LCAR00000001` dans les plages temporaires approuvées ;
- aucun identifiant interne WAY4 inventé ;
- allocation PostgreSQL transactionnelle et idempotente prouvée ;
- isolement vis-à-vis des réseaux externes confirmé par l'équipe WAY4/exploitation ;
- sept contrôles Oracle de collision exécutés sur CARSDB : tous à zéro.

## Contrôle Oracle reçu

| Contrôle | Collision |
|---|---:|
| `CONTRACT_LCAR_ACNT` | 0 |
| `CONTRACT_LCAR_APPL` | 0 |
| `MID_ACNT_CONTRACT` | 0 |
| `MID_APPL_CONTRACT` | 0 |
| `MID_DEVICE_REC` | 0 |
| `TID_ACNT_CONTRACT` | 0 |
| `TID_DEVICE_REC` | 0 |

Export original reçu : `D:\dddddd\carsdb.csv`, SHA-256 `BF3ADEEA466B0B62ACECE9501F9AA644E697857C02B5DDB23827CCE9CA089187`.

## Conditions d'exécution

1. positionner `validatedForImport=true` uniquement pour ce candidat CARSDB revu ;
2. vérifier immédiatement avant l'import que le fichier conserve exactement l'empreinte autorisée ;
3. effectuer un seul import manuel sous contrôle de l'opérateur WAY4 ;
4. ne pas activer le TPE et ne lancer aucune transaction ;
5. conserver l'heure, l'opérateur, le canal/répertoire utilisé et l'identifiant du traitement ;
6. conserver les journaux, le statut final et le fichier retour ;
7. rapprocher les objets créés avec les quatre `RegNumber` ;
8. contrôler exactement un client, un contrat commerçant, une adresse, un contrat TPE, le MID et le TID attendus ;
9. en cas de rejet ou de résultat incertain, ne pas resoumettre automatiquement : analyser d'abord le retour et rechercher le `RegNumber` dans WAY4.

## Après l'import

Le GO de recette E2E ne sera pas automatique. Il dépendra de la preuve du résultat WAY4, de l'absence de création partielle ou en double et de la réconciliation des identifiants retournés.
