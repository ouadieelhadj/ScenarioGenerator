# WayPos - Matrice de validation Basic, Extended et routage

Date de reference : 2026-07-30.

Cette matrice compare les specifications OpenWay POS Basic/Extended avec
les sources WayPos et les adaptateurs reseau presents dans le depot. Elle
ne remplace pas une recette connectee et ne transforme jamais un test
unitaire en preuve E2E.

Sources d'autorite :

- `documents/specifications/waypos/OpenWay_POS_BasicSet_extracted.txt` ;
- `documents/specifications/waypos/OpenWay_POS_ExtendedSet_extracted.txt` ;
- PDFs originaux conserves hors depot sous `D:/LanaCash/OpenWay/SpecsPos/`.

Legende :

- **Complet code** : implementation et preuve automatisee disponibles ;
- **Partiel** : transport/effet comptable disponible, semantique ou payload
  specialise incomplet ;
- **Bloque explicitement** : refus metier volontaire, sans approbation
  fictive ;
- **E2E requis** : code et tests unitaires disponibles, raccordement reel
  non encore prouve.

## Socle transport, securite et persistence

| Exigence | Etat | Preuve ou reserve |
|---|---|---|
| TCP, longueur binaire 2 octets big-endian | Complet code | `WayPosLengthChannel`, tests packager/framing |
| Packager jPOS OpenWay DE2-DE64 | Complet code | `WayPosPackager`, tests pack/unpack |
| MAC BIN et HEX, TAK simple/double | Complet code | 8 vecteurs OpenWay automatises |
| MAC requete et reponse | Complet code | `WayPosSecurityService`, simulateur verifie la reponse |
| Bootstrap initial TAK/TPK | Complet code | API sous LMK, KCV recalcule par HSM |
| Key change ANSI X9.17 | Complet code, E2E requis | livraison, import, KCV, activation apres ACK |
| PIN ISO-0/PVV local | Complet code, E2E requis | verification HSM sans PIN clair |
| ARQC, anti-rejeu ATC, ARPC tag 91 | Complet code, E2E requis | test crypto LMK temporaire |
| PVK et MDK provisionnees | Complet code | controle KCV par HSM avant persistence |
| Journal `pos_authorizations` | Complet code | etats, liens origine, ARPC, idempotence |
| Outbox chiffree AES-256-GCM | Complet code, E2E requis | anti-alteration, backoff, statut MANUAL |
| Routage BIN/IIN | Complet code | priorite, route locale `00000`, adaptateurs |

## Basic Set

| Operation | MTI / DE3 / DE24 | Catalogue | Route locale `00000` | Simulateur / test |
|---|---|---|---|---|
| Authorization | 0100 / 00xx00 | Complet | hold atomique | scenario advice + tests |
| Purchase/Cash | 0200 / 00xx00 | Complet | debit atomique | purchase, repeat, reversal, EOD |
| Purchase with cashback | 0200 / 09xx00 | Complet | debit global | pas de ventilation cash dediee |
| Balance inquiry | 0100 / 30xx00 | Complet | solde disponible | pas de scenario REST dedie |
| Mini statement | 0100 / 32xx00 | Complet | Bloque explicitement RC96 | historique formate non implemente |
| Card verification | 0100 / 39xx00 | Complet | controles carte/PIN/EMV | pas de scenario dedie |
| Pre-authorization cash | 0100 / 51xx00 | Complet | hold | capture generique disponible |
| Authorization confirmation | 0220 / DE24=202 | Complet | capture du hold | final advice scenario |
| EMV informational/final advice | 0120/0220 | Complet | advice correle | final advice scenario |
| Tip completion | 0220 / 02xx00 / DE24=202 | Complet | debit du tip uniquement | test metier serveur |
| Refund | 0200 / 20xx00 | Complet | credit | controle origine a completer selon profil |
| Purchase return | 0200 / 25xx00 | Complet | credit | controle origine a completer selon profil |
| Cash to card | 0200 / 290000 | Complet | credit | payload/check specialises partiel |
| Cash by code | 0200 / 520000 | Complet | Bloque explicitement RC96 | generation/consommation du code absente |
| MIR EC purchase return | 0200 / 23xx00 | Complet | credit | payload MIR specialise non valide |
| Universal reversal/advice | 0400/0420 / 400-402 | Complet | restauration idempotente | scenario reversal + outbox |
| Reconciliation/advice | 0500/0520 / 920000 | Complet | compteurs, montants, etats batch | scenario EOD |
| Batch upload advice | 0320 | Complet | persistence et reprise | construction simulateur |
| POS initialization | 0800 / 930000 | Complet | batch ID 6 chiffres | tous les scenarios |
| Reject message | 0830 / 970000 | Reconnu | pas une operation locale | E2E requis |
| DCC/OTP/instalment/e-receipt inquiries | 9100/9700 | Bloque explicitement | RC96 | payloads specialises absents |

## Extended Set

| Operation | MTI / DE3 | Etat | Reserve |
|---|---|---|---|
| Loyalty Program Request | 0100 / 16xxxx | Bloque explicitement | RC96 tant que bonus/remises et tags dedies sont absents |
| Utility Payment | 0100/0200 / 50xx00 | Bloque explicitement | RC96 tant que le fournisseur externe est absent |
| Universal Bill Payment Authorization | 0100 / 59xx00 | Bloque explicitement | RC96 tant que fournisseur/details metier sont absents |
| Universal Bill Payment Advice | 0200/0220 / 59xx00 | Bloque explicitement | RC96 tant que references et fournisseur sont absents |
| Card Control Request | 0100 / 91xx00 | Bloque explicitement | RC96 tant que les vrais issuer scripts EMV sont absents |
| PIN Change/Enrolment non-EMV | 0100 / 920000 | Complet code | nouveau PIN block opaque, PVV calcule par HSM |
| PIN Change/Enrolment EMV | 0100 / 920000 | Bloque explicitement | issuer scripts requis |
| Credit | 0200 / 21xx00 | Complet code | credit cible atomique |
| Credit Voucher | 0200 / 240000 | Complet code | credit ; regles d'origine a valider en recette |
| P2P Card to Card | 0200 / 480000 | Complet code | verrouillage ordonne, debit/credit atomiques |
| AFD Completion | 0220 / DE24=102 | Complet code | delta hold/final atomique |
| File Update | 0302 -> 0312 | Complet code | donnees binaires persistantes, idempotence |
| Keys Change | 0800 / 960000 | Complet code, E2E requis | ANSI X9.17 ; GISKE volontairement reporte |

## Routage reseau et comparaison DMAS/DMC

| Cible | Endpoint normalise | Autorisation/financial | Advice/reversal | PIN/EMV | Etat connecte |
|---|---|---|---|---|---|
| Route locale `00000` | interne | oui | oui | PIN, ARQC, ARPC | E2E reel en attente |
| DMAS Member | `/api/routing/v1/transactions` | oui | oui | PIN traduit, DE55/ARPC | tests unitaires, E2E en attente |
| SWAM Member | `/api/routing/v1/transactions` | oui | oui | PIN traduit, DE55/ARPC | tests unitaires, E2E en attente |
| Mastercard SMS | `/api/routing/v1/transactions` | oui | oui | PIN traduit, DE55/ARPC | tests unitaires, E2E en attente |
| Visa/ARDEF | reserve | non | non | non | a implementer |
| Autre interface | contrat `PosConnector` | extensible | extensible | par capacite | a raccorder |

Le clearing DMCS/DMC reste un chantier separe :

- premier lot publie sous le tag `ValidationDmcsDmasFirst` ;
- reversals/advices et sources chargeback/seconde presentation presentes ;
- fichiers IPM complets, reconciliation, frais, change, settlement et E2E
  DMAS/DMC connecte restent a terminer ;
- DE31/ARN reste volontairement sans valeur tant que sa source reelle et sa
  regle ne sont pas confirmees.

La source de reprise de ce chantier reste `REPRISE_DMCS_DMAS.md`.

## Ecarts prioritaires restants

1. **P0 - E2E reel WayPos** : charger les cles, carte, PIN block et quatre
   ARQC/ATC reels, puis executer `tests/waypos/Invoke-WayPosE2E.ps1`.
2. **P1 - Operations specialisees Basic** : mini statement, cash by code,
   DCC, OTP, instalment et e-receipt.
3. **P1 - Operations specialisees Extended** : loyalty, utility et bill
   payment avec leurs fournisseurs/references metier.
4. **P1 - Issuer scripts EMV** : Card Control et PIN management EMV.
5. **P1 - Reseaux** : E2E connecte DMAS, SWAM et Mastercard SMS ; Visa
   ARDEF et Mastercard BIN/reference data.
6. **P1 - Clearing DMAS/DMC** : IPM, reconciliation, frais, change,
   settlement et validation exhaustive separee.

## Reference de non-regression

Commande :

```text
mvn -o -nsu -f pom.xml
  -pl sg-way-pos-server,sg-way-pos-simulator,sg-mc-dmas-member,
      sg-swam-acquirer,sg-mc-sms-acquirer,sg-mc-sms-issuer
  -am test
```

Resultat du 2026-07-30 :

- `sg-common` : 63 tests ;
- DMAS Member : 3 tests ;
- SWAM Member : 11 tests ;
- Mastercard SMS : 3 tests ;
- WayPosServer : 34 tests ;
- wayPosSimulator : 15 tests ;
- total : **129 tests, 0 echec** ;
- `sg-mc-sms-issuer` : compilation reussie, aucun test propre.

Le test de magasin de cles couvre aussi les TAMK/TPMK de recette en triple
longueur : XOR des trois composantes, KCV et import ANSI X9.17 TAK/TPK.
