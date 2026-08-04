# Cartes Mastercard MTIP pour DMAS

Ce rÃ©pertoire prÃ©pare les donnÃ©es de certification du paquet
`EMVCoL3_04_20251113T160852664Z_260428-152429.tsez` pour `mc_dmas_cards`.

Le paquet contient **47 cas sÃ©lectionnÃ©s et rÃ©ussis**, **36 dÃ©finitions de carte**
utilisÃ©es par ces cas et **48 applications/PAN distincts**. Les PAN, PIN, marques
et listes CVM viennent de l'onglet **Card Details** de TSE. Les objectifs,
actions et rÃ©sultats viennent des dÃ©tails des cas de test.

## Fichiers

- `mtip_test_cases.csv` : catalogue des cas, actions, rÃ©sultats attendus,
  cartes/applications et donnÃ©es Card Details ;
- `mtip_expected_results.csv` : dÃ©tail des contrÃ´les attendus, opÃ©rateurs,
  valeurs de rÃ©fÃ©rence et rÃ©sultat rÃ©ellement obtenu par le run ;
- `mtip_card_inventory.csv` : inventaire consolidÃ© des 48 applications carte,
  avec l'origine des donnÃ©es EMV ;
- `01_upsert_mtip_mastercard_cards.sql` : upsert idempotent dans
  `public.mc_dmas_cards` ;
- `02_verify_mtip_mastercard_cards.sql` : contrÃ´les en lecture seule, sans PIN
  et avec PAN masquÃ©s ;
- `load-mtip-mastercard-cards.sh` : exÃ©cution manuelle protÃ©gÃ©e par
  `MTIP_APPLY=YES` ;
- `generate-mtip-mastercard-assets.ps1` : rÃ©gÃ©nÃ©ration des artefacts depuis les
  XML extraits du paquet TSE.

## Origine et valeurs par dÃ©faut

- PIN : toujours lu dans **Card Details** ; aucun PIN inventÃ©.
- EMV observÃ© : 28 PAN enrichis Ã  partir des messages ISO 8583 du paquet.
- EMV absent : 20 PAN utilisent explicitement les valeurs de repli de
  la migration DMAS V4 (`AIP=1B80`, `PSN=00`, version `0002`, IAD et CVM V4).
- Expiration absente : `4912`, valeur commune au jeu de certification.
- Devise absente : `504`.
- Solde : `100000000` unitÃ©s mineures ; bank code : `022905`.
- `emv_atc` dÃ©marre Ã  `0` en base ; l'ATC observÃ© reste dans l'inventaire.

Le catalogue des rÃ©sultats contient 484 contrÃ´les dÃ©finis par les rÃ¨gles :
465 sont `pass`, 4 sont `not tested` et 15 n'ont pas de ligne de rÃ©sultat dans
ce run (`not reported`). Le statut global des 47 cas sÃ©lectionnÃ©s reste
`Passed`, conformÃ©ment Ã  `selected.xml`.

Le PAN finissant par `0026` conserve volontairement l'expiration observÃ©e
`1912`, car il sert Ã  un scÃ©nario de carte expirÃ©e.

## Limite cryptographique

Le paquet contient des ARQC attendus mais pas la MDK claire permettant de les
recalculer. Le script ne crÃ©e donc aucune clÃ© fictive. Pour valider rÃ©ellement
les ARQC FETIAN, la MDK de certification correspondante doit Ãªtre importÃ©e dans
`mc_dmas_mastercard_keys`, chiffrÃ©e sous la LMK locale via le HSM. Sans cette
MDK, les cartes sont chargeables mais une autorisation EMV peut Ãªtre refusÃ©e
avec `RC05`.

## ExÃ©cution manuelle

```bash
cd /d/MoneyCore/ScenarioGenerator
export PGHOST=127.0.0.1
export PGPORT=5432
export PGDATABASE=scenariogenerator
export PGUSER=postgres
export PGPASSWORD='mot-de-passe-local'
export MTIP_APPLY=YES
./tests/dmas/MTIP_Mastercard/load-mtip-mastercard-cards.sh
```

Ne pas versionner `PGPASSWORD` et ne jamais utiliser ces donnÃ©es sur une base
de production.
