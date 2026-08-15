# Proof of Test - Fraud Monitoring increment 1

**Date :** 15 aout 2026
**Perimetre :** contrats, BFF et integration Angular Switch/SwitchLab.

## Controles realises

| Controle | Resultat |
|---|---|
| Compilation contrats Switch et SwitchLab | Reussi |
| Compilation BFF Switch et SwitchLab | Reussi |
| Test fail-closed BFF Switch | Reussi |
| Test fail-closed BFF SwitchLab | Reussi |
| Build Angular FuturPayment Switch | Reussi |
| Build Angular FuturPayment SwitchLab | Reussi |
| Chunk partage Fraud Monitoring dans Switch | Present |
| Chunk partage Fraud Monitoring dans SwitchLab | Present |

## Commandes

```text
mvn.cmd -o -nsu -f pom.xml -pl sg-switch-bff,sg-switchlab-bff -am test
npm.cmd run build:switch -- --configuration development
npm.cmd run build:switchlab -- --configuration development
```

## Resultats exacts

- Maven : `BUILD SUCCESS`, 2 tests executes, 0 echec, 0 erreur, 0 ignore ;
- Switch : application bundle genere dans
  `sg-frontend/dist/futurpayment-switch` ;
- SwitchLab : application bundle genere dans
  `sg-frontend/dist/futurpayment-switchlab` ;
- les deux sorties contiennent le lazy chunk
  `fraud-workspace-component`.

## Ce que cette preuve ne valide pas

- aucun moteur de scoring ou modele IA n'a ete execute ;
- aucune connexion PostgreSQL n'a ete requise ;
- aucun test Playwright ou test connecte n'a ete execute ;
- aucun volume laboratoire n'a ete injecte ;
- aucune transaction n'a ete bloquee ou routee ;
- aucune performance n'est revendiquee.
