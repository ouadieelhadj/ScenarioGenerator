# Scripts de test

Tous prennent `<login> <password>` en parametres optionnels
(defaut : `admin` / `Admin123!`) et redemarrent les modules eux-memes,
**serveur d'abord, client ensuite**.

## Prerequis commun

**PostgreSQL doit tourner.** Les ports REST sont lus en base AVANT le
demarrage de Tomcat : sans base, le module refuse de demarrer avec
`[SG-PORTS] Base injoignable`.

    export PGPASSWORD=postgres123
    /f/MoneyCore/pgsql/bin/pg_ctl.exe -D /f/MoneyCore/pgsql/data \
        -l /f/MoneyCore/pgsql/data/pg.log start

Piege recurrent : un JAR verrouille ou un port occupe par un process
Java resté en vie. Avant tout build ou test :

    taskkill //F //IM java.exe        # double slash en Git Bash

## Les scripts

| Script | Objet | Verdict attendu |
|---|---|---|
| `test-dmas.sh` | sign-on, liaison permanente, controle des ports | `[JPOS-CLI]` present, `[DMAS-ACQ]` absent, port 8600 libre |
| `test-dmas-key-injection.sh` | injection manuelle KEK et PEK | meme KCV des deux cotes |
| `test-dmas-key-162.sh` | les trois mecanismes d'echange | 3 fois « 2 cles ACTIVE, 1 KCV » |
| `test-dmas-multibank.sh` | deux banques, deux JVM | 4 cles ACTIVE, 2 KCV distincts |
| `test-mc-sms-keyexchange.sh` | echange de cle MC SMS | meme KCV des deux cotes |

## DMAS — parametre de lancement

Depuis le passage au multi-banque, chaque module DMAS recoit son ou ses
interfaces :

    java -jar sg-mc-dmas-mastercard/target/*.jar --sg.interface=DMAS_MASTERCARD_1
    java -jar sg-mc-dmas-member/target/*.jar     --sg.interface=DMAS_BANK_A

    # ou, pour piloter deux banques dans une seule JVM :
    java -jar sg-mc-dmas-mastercard/target/*.jar \
        --sg.interface=DMAS_MASTERCARD_1,DMAS_MASTERCARD_2
    java -jar sg-mc-dmas-member/target/*.jar \
        --sg.interface=DMAS_BANK_A,DMAS_BANK_B

Le port REST est celui de la PREMIERE interface. Les appels designent
ensuite la banque par `?bank=022905`, optionnel en mono-banque.

Interfaces disponibles :

    SELECT id_interface, bank_code, label, rest_port, iso_port
    FROM mc_dmas_interface;

## Ports

| Reseau | REST | ISO |
|---|---|---|
| DMAS banque A | 8084 | — |
| DMAS banque B | 8085 | — |
| DMAS Mastercard 1 | 8501 | 8500 |
| DMAS Mastercard 2 | 8502 | 8503 |
| MC SMS acquereur | 8095 | 8096 (inutilise) |
| MC SMS issuer | 8097 | 8098 |
| SWAM acquereur | 8094 | — |
| SWAM issuer | 8511 | — |

## Authentification

DMAS protege ses endpoints par JWT ; SWAM et MC SMS sont en `permitAll`.

    TOKEN=$(curl -s -X POST http://localhost:8084/auth/login \
        -H "Content-Type: application/json" \
        -d '{"login":"admin","password":"Admin123!"}' \
        | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

    curl -X POST http://localhost:8084/api/admin/dmas/network/signon \
        -H "Authorization: Bearer $TOKEN"

En Git Bash, entourer le mot de passe de quotes SIMPLES : le `!` serait
sinon interprete par l'historique du shell.

## Endpoints DMAS

    # membre
    POST /api/admin/dmas/network/signon?bank=       sign-on
    POST /api/admin/dmas/network/signon-all         toutes les banques
    POST /api/admin/dmas/network/signoff?bank=
    POST /api/admin/dmas/network/echo?bank=
    GET  /api/admin/dmas/network/status?bank=       ou toutes si multi
    POST /api/admin/dmas/kek/bootstrap              corps {memberGroupId, kekClear}
    POST /api/admin/dmas/keys/inject?bank=&clear=   injection manuelle
    POST /api/admin/dmas/keys/solicit?bank=         echange 162
    GET  /api/admin/dmas/keys/current?bank=

    # Mastercard
    GET  /api/admin/dmas/jpos/status?bank=          ou toutes si multi
    GET  /api/admin/dmas/jpos/sessions              membres connectes
    POST /api/admin/dmas/jpos/push/pek?bank=        flux system generated
    POST /api/admin/dmas/jpos/push/network?de70=&wait=&de48=&bank=
    POST /api/admin/dmas/jpos/push/advice?de70=&de48=&bank=

## Cles de reference

| Cle | Valeur | KCV |
|---|---|---|
| KEK / ZMK | `13AED5DA1F32347523C708C11F2608FD` | `2D617C` |
| PEK de test | `BC4AEA2F5BB3FD1504624F8623835D5B` | `43A186` |

Le KCV `43A186` correspond a celui calcule depuis la trace du simulateur
officiel : la cryptographie DMAS est bien celle des traces.

## Deux identifiants a ne pas confondre

| Identifiant | Exemple | Role |
|---|---|---|
| `member_group_id` | `TESTGRP01` | cle de recherche EN BASE des cles |
| Group Sign-on ID (DE2) | `40260` | identifiant du membre SUR LE RESEAU |

**Regle : chaque cote cherche ses cles avec SON identifiant local.** Le
DE2 d'un message recu ne sert jamais de cle de recherche — cote
Mastercard, il sert a identifier la banque via `lookupByGroupSignon()`,
qui remonte ensuite au `member_group_id`.
