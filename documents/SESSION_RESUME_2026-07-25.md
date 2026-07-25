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

## Validation locale CVN10

Le 25 juillet 2026, la chaine DMAS membre vers DMAS Mastercard a ete
reconstruite depuis les entites JPA puis testee en `ddl-auto=validate`.

Resultat du test avec le profil de la carte Way4 `5413330002001049` :

- sign-on `0800 -> 0810`, `DE39=00` ;
- KEK identique des deux cotes, KCV `2D617C` ;
- PEK/TPK Way4 identique des deux cotes, KCV `439B5A` ;
- MDK Way4 identique des deux cotes, KCV `850571` ;
- montant `000000006589`, ATC `009D`, UN `28058417` ;
- ARQC recu et recalcule `ED2D10DD856931AB`, `match=true` ;
- reponse `0110`, `DE39=00`, ARPC present dans le tag `91` ;
- bilan : `sent=1 approved=1 declined=0 errors=0`.

Ce test valide la generation et la verification CVN10 entre nos deux modules.
Le rejeu Way4 reel vers DMAS Mastercard reste necessaire : la date et l'UN
dynamiques signifient que l'ARQC local ne peut pas etre identique a celui de
la trace historique.

## Interfaces SWAM et logs pilotes par la base

SWAM utilise maintenant une table `swam_interface`, symetrique a
`mc_dmas_interface`. Les modules demarrent avec :

```bash
java -jar sg-swam-issuer.jar --sg.interface=SWAM_NETWORK_1
java -jar sg-swam-acquirer.jar --sg.interface=SWAM_MEMBER_A
```

La ligne selectionnee fournit le port REST, le port ISO ou la cible distante,
le DE32 acquereur, le DE33 issuer, le `member_group_id` et le chemin du fichier
de log. `mc_dmas_interface` contient egalement `log_file` pour les modules
DMAS.

Migration : `deploiement/migration_v1.4.0_swam_interfaces_logs.sql`.

Validation locale :

- SWAM issuer demarre sur REST `8511` et ISO `8510` ;
- SWAM membre demarre sur REST `8094` et cible `localhost:8510` ;
- sign-on `1804/801 -> 1814`, DE39 `800`, DE33 `300853` ;
- achat `1100 -> 1110`, DE39 `000`, DE32 `12345` ;
- les logs sont crees aux chemins stockes dans `swam_interface`.
