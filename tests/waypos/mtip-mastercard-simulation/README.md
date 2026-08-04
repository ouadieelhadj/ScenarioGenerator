# Simulation MTIP Mastercard via wayPosSimulator

Ce harnais rejoue la partie **hote ISO/ServerPOS** des 47 cas selectionnes du
paquet TSE Mastercard. Il ne remplace pas les controles physiques du kernel
EMV, de l'interface du TPE, des APDU ou de la selection d'application.

## Contenu

- `requests/` : une definition JSON par cas TSE, avec les messages ISO simulables ;
- `expected/` : controles classes `HOST_ISO`, `HOST_CRYPTO` ou `TPE_ONLY` ;
- `manifest.json` et `coverage.csv` : couverture et classification ;
- `prepare-campaign.sh` : charge les cartes SQL, cree les routes BIN locales et prepare le RKI ;
- `run-one.sh`, `run-all.sh` : execution Git Bash ;
- `reports/` : rapports assainis generes localement et ignores par Git ;
- `generate-scenarios.ps1` : regeneration depuis le `.tsez` source.

Les JSON contiennent des PAN et des DE55 de certification. Ils sont strictement
reserves au LAB/RECETTE et ne doivent jamais etre utilises en production.

## Prerequis

1. PostgreSQL et les composants de routage/DMAS necessaires sont demarres.
2. Les 48 applications carte sont chargees avec `load-cards.sh`.
3. ServerPOS repond sur `8530/8531` et wayPosSimulator sur `8532`.
4. Les TAMK/TPMK de test deja utilises par le TPE sont fournis au simulateur
   par `WAY_POS_TAMK_HEX` et `WAY_POS_TPMK_HEX` ; aucune valeur n'est stockee
   dans ce repertoire.
5. `prepare-rki.sh` importe et confirme une TAK/TPK locale. La TPK active sert
   a reconstruire un PIN block ISO-0 propre au PAN, sans rejouer le DE52 TSE.
6. La cle de test Visa utilisee pour la personnalisation FETIAN est installee
   comme MDK de certification cote DMAS, sous LMK. Sa valeur claire ne doit
   jamais etre ajoutee aux JSON ou au depot.
7. En mode bootstrap local uniquement, `start-serverpos.sh` protege la
   `PEK_CLEAR` de l'environnement sous son propre LMK et l'associe a
   `DMAS_MEMBER`. Aucun endpoint HTTP n'accepte cette cle claire.

Sans MDK FETIAN, les scenarios contenant un DE55 reel peuvent etre refuses et
ne doivent pas etre presentes comme valides.

## Execution Git Bash

```bash
cd /d/MoneyCore/ScenarioGenerator
export MTIP_APPLY=YES
./tests/waypos/mtip-mastercard-simulation/prepare-campaign.sh

./tests/waypos/mtip-mastercard-simulation/run-one.sh MCD01.Test.01.Scenario.01
./tests/waypos/mtip-mastercard-simulation/run-all.sh
```

`prepare-campaign.sh` ne lance aucun test. Il route uniquement les BIN des
cartes MTIP vers `DMAS_MEMBER`, sans afficher les PAN, et desactive pour ces
BIN toute ancienne route vers une autre interface. `run-all.sh` reste
l'unique commande qui lance la campagne complete et doit etre executee
manuellement.

Le diagnostic structurel sans MAC est possible avec
`export MTIP_MAC_ENABLED=false`, mais il ne constitue pas un resultat de
recette.

## Regeneration

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File tests/waypos/mtip-mastercard-simulation/generate-scenarios.ps1 `
  -ArchivePath "D:\LanaCash\OpenWay\Certification\MTIP\MasterCard\FETIAN\EMVCoL3_04_20251113T160852664Z_260428-152429.tsez"
```

Le runner n'affiche ni PAN complet, ni PIN, ni DE55. Il ecrit un rapport JSON
assaini dans `reports/`.

Par defaut, la campagne de simulation utilise le terminal logique `TERM0001`,
distinct du F20 physique `12488881`. Si un autre identifiant est configure, la
meme valeur `WAY_POS_TERMINAL_ID` doit etre utilisee au demarrage du simulateur
et lors de l'execution des runners, sinon le MAC sera refuse avec RC63.
