# Complement de reprise - lanceurs DMAS / SWAM

## Lanceurs par module

Quatre scripts Git Bash autonomes demarrent le JAR, attendent l'API puis
reforment les cles sous le LMK local :

| Module | Script | Cles / actions |
|---|---|---|
| DMAS membre | `tests/dmas/member/start-and-bootstrap.sh` | KEK, PEK/TPK, MDK |
| DMAS Mastercard | `tests/dmas/mastercard/start-and-bootstrap.sh` | KEK, PEK/TPK Way4, MDK |
| SWAM issuer | `tests/swam/issuer/start-and-bootstrap.sh` | KEK, puis generation ZPK/ZAK |
| SWAM acquereur | `tests/swam/acquirer/start-and-bootstrap.sh` | KEK, sign-on, ZPK/ZAK |

Documentation d'utilisation : `tests/START_MODULES.md`.

Les secrets ne sont pas enregistres dans Git : ils viennent de
l'environnement ou sont demandes sans affichage. Ne jamais copier
`key_under_lmk` entre machines ; rebootstrapper les cles sur la cible.

Pour Way4 vers notre DMAS Mastercard, le script injecte la TPK comme PEK
(`TESTGRP01`, type `PEK`, statut `ACTIVE`) afin d'eliminer le `DE39=96`
avant de poursuivre le diagnostic ARQC CVN 01.
