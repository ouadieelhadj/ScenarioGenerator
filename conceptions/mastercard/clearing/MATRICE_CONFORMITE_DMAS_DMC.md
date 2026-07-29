# Matrice de conformité croisée DMAS → DMC

## 1. Objet et méthode

Cette matrice relie les deux référentiels qui régissent le flux complet :

```text
DMAS/CIS autorisation
  → journal propriétaire membre ou Mastercard
  → batch EOD DMCS propriétaire
  → mapping DMC
  → message IPM ISO 8583:1993 + PDS + RDW
```

Sources :

- `m_DMAS_en-us-2025-11-04.pdf`, version du 4 novembre 2025 ;
- `m_DMC_guide_en-us.pdf` et sa version texte locale.

Le guide DMC est la source normative pour le clearing. DMAS/CIS vérifie que
chaque donnée source a le sens, le format et les conditions de présence attendus
côté autorisation.

Les numéros de page sont ceux imprimés dans les documents. Ils peuvent différer
du numéro physique affiché par un lecteur PDF.

## 2. Autorisation DMAS et alimentation des journaux

| Élément | Règle DMAS/CIS | Journal actuel | Usage DMC | État |
|---|---|---|---|---|
| MTI | 0100/0110 et advices financiers selon le cycle | `mti_request`, `mti_response` | sélection EOD et cycle de vie | noyau conforme, reversals à compléter |
| DE2 | PAN variable | PAN + PAN masqué | DE2 et rapprochement | fonctionnel, protection du PAN brut à renforcer |
| DE3 | processing code sur 6 positions | conservé | DE3 IPM | implémenté |
| DE4 | montant sur 12 positions | unité mineure | DE4 et PDS 0301 | implémenté |
| DE7/11 | horodatage réseau et STAN | conservés | audit/corrélation | implémenté |
| DE12/13 | date et heure locales | conservées | DE12 DMC | mapping implémenté |
| DE14 | date d'expiration | conservée | DE14 conditionnel | implémenté |
| DE18 | MCC | conservé | DE26 DMC | implémenté |
| DE22 | entrée/capacité d'authentification | 3 positions | composantes DE22 DMC | implémenté avec DE61 |
| DE23 | card sequence number | conservé | EMV/PDS conditionnel | journalisé, PDS restant |
| DE32/33 | institutions | conservées | DE32/33 | implémenté |
| DE37/38/39 | RRN, autorisation, décision | conservés | rapprochement/présentation | implémenté |
| DE41/42/43 | terminal et commerçant | conservés | DE41/42/43 | implémenté, DE43 à durcir |
| DE48 | données additionnelles/e-commerce | conservé | DE22/PDS conditionnels | partiel |
| DE49 | devise transaction | conservée | DE49 | implémenté |
| DE52 | PIN block binaire 8 octets, très sensible | **jamais conservé** | aucun usage clearing | conforme |
| DE55 | binaire LLLVAR, TLV EMV, notamment 9F26 | hexadécimal sans altération | PDS conditionnels | autorisation conforme, mapping à compléter |
| DE61 | 14 sous-champs POS, remplace DE25 | conservé | construction DE22 DMC | noyau implémenté |
| ARQC | recalcul Mastercard depuis DE55 | résultat non copié au clearing | validité autorisation | test E2E prévu |
| ARPC | produit après ARQC valide, tag 91 | non persisté comme secret | aucune copie aveugle IPM | test E2E prévu |

Références DMAS principales :

- DE52 et interdiction de conservation permanente : pages 726–727 ;
- DE55 et TLV EMV : pages 740–745 ;
- Application Cryptogram 9F26 : page 742 ;
- Issuer Authentication Data/tag 91 : section DE55, page 745 ;
- DE61 : page 782 ;
- cohérence e-commerce DE48/DE61 : pages 707–708 ;
- contrôles Authorization Request : page 820 et suivantes.

## 3. Conversion vers DMC/IPM

| Élément DMC | Exigence | Implémentation | État |
|---|---|---|---|
| Packager | ISO 8583:1993, EBCDIC, bitmap binaire | `DmcIpmPackager` jPOS | implémenté |
| Fichier | RDW/VBS | `DmcRdwCodec` | implémenté et testé |
| Header | `1644/697` | `DmcIpmMessageFactory` | implémenté |
| Présentation | `1240/200` | factory + validation | structure implémentée |
| Trailer | `1644/695` | factory + validation | implémenté |
| DE71 | séquence depuis `00000001` | contrôle strict | implémenté |
| PDS 0105 | identifiant fichier | type/date/processor/séquence | implémenté |
| PDS 0122 | mode Test/Production | `T`/`P` | implémenté |
| PDS 0301 | checksum montants | calcul/contrôle | implémenté |
| PDS 0306 | nombre de messages | calcul/contrôle | implémenté |
| DE22 | code POS 12 positions | matrice DMC pages 286–289 | noyau implémenté |
| DE31 | Acquirer Reference Data, 23 positions, stable sur le cycle | aucune valeur inventée | **bloquant outgoing réel** |
| Incoming | RDW, unpack jPOS, validation | deux côtés | premier noyau |
| Chargeback | `1442/450` ou `453` lié à la présentation | import partiel | génération ouverte |
| Seconde présentation | `1240/205` ou `282` | import partiel | génération ouverte |
| Reconciliation | contrôles/messages de cycle | non finalisé | ouvert |
| Frais/change/settlement | PDS, taux, positions, écritures | non finalisé | ouvert |

Références DMC principales :

- DE22 POS Data Code : pages 286–289 ;
- DE31 Acquirer Reference Data : page 382 ;
- PDS 0105 : pages 561–562 ;
- PDS 0122 : page 569 ;
- PDS 0301 : page 835 ;
- PDS 0306 : page 837 ;
- File Header `1644/697` : page 1545 ;
- First Presentment `1240/200` : page 1548 ;
- File Trailer `1644/695` : page 1848.

## 4. Propriété et séparation

| Système | Source autorisation | Table clearing |
|---|---|---|
| membre | `mc_dmas_member_transactions` | `dmcs_acquirer_clearing_transactions` |
| Mastercard | `mc_dmas_issuer_transactions` | `dmcs_issuer_clearing_transactions` |

Chaque côté exécute son batch EOD et possède ses données. Il n'existe aucun
rapprochement direct entre les deux journaux d'autorisation. Le rapprochement
doit passer par les fichiers DMC/IPM, comme entre deux systèmes indépendants.

## 5. Décision de conformité

Le premier noyau est conforme pour la séparation des propriétaires, la capture
des champs nécessaires, l'absence de DE52 dans les journaux, la validation en
ligne PIN/ARQC/ARPC, le mapping de base, l'enveloppe jPOS/IPM/RDW et les EOD
idempotents.

La conformité complète exige encore :

1. règle officielle DE31/ARN ;
2. PDS conditionnels EMV, e-commerce et produit ;
3. reversals et advices complets ;
4. chargebacks et seconde présentation sortants ;
5. erreurs/rejets de fichier et de message ;
6. reconciliation, frais, change, settlement et comptabilisation ;
7. golden files et E2E IPM bilatéral complet.

Aucun champ obligatoire ne doit être inventé uniquement pour faire passer un
test.
