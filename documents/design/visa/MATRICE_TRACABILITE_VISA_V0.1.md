# Matrice initiale de traçabilité Visa Online vers Base II

## Objet

Cette matrice relie les données d'autorisation Visa Online aux données que le
journal membre doit conserver puis projeter vers Base II. Elle ne remplace pas
les règles de présence détaillées des tables Visa.

## Frontières des quatre applications

| Application | Propriétaire simulé | Entrée principale | Sortie principale | Frontend |
|---|---|---|---|---|
| `sg-visa-online-member` | banque membre | contrat de routage neutre | Visa Online vers VisaNet | menu VisaNet Membre |
| `sg-visa-visanet-simulator` | réseau Visa + issuer externe | messages Visa Online | réponses et cycle réseau simulés | LAB uniquement |
| `sg-visa-base2-member` | banque membre | journal Online membre | fichiers Base II et actions clearing | menu Visa Base II Membre |
| `sg-visa-base2-network-simulator` | réseau Visa + issuer clearing externe | fichiers Base II membre | contrôles, livraisons, retours et litiges simulés | LAB uniquement |

`sg-visa-common` et `sg-visa-base2-common` sont des bibliothèques. Elles ne
sont ni déployées seules ni exposées dans le portail.

## Autorisation et cycle de vie

| Besoin | Source Visa Online | Cible interne | Usage Base II | État |
|---|---|---|---|---|
| Identité interne | transaction/correlation/idempotence | journal propriétaire | audit, EOD | contrat existant à étendre |
| PAN | F2, en mémoire | référence, empreinte, masque | TC05/TCR0 via résolution sécurisée | port sécurisé à créer |
| Montant | F4 | montant mineur | montants source/destination | existant partiel |
| Transmission | F7 | horodatage normalisé | contrôle et audit | à créer |
| STAN | F11 | `f011_stan` | corrélation | existant dans Acquisition |
| Date/heure locale | F12/F13 | date/heure normalisées | purchase date et contrôles | à créer |
| MCC | F18 | `f018_mcc` | TC05/TCR0 MCC | profil commerçant disponible |
| Pays acquéreur | F19 | `f019_acquirer_country` | routage et règles | configuration officielle requise |
| Mode d'entrée | F22 | `f022_pos_entry_mode` | données POS/Base II | valeur e-commerce à mapper |
| Condition POS | F25 | `f025_pos_condition` | qualification e-commerce | valeur à valider |
| Acquéreur | F32 | `f032_acquirer_id` | acquiring identifier/ARN | identifiant officiel requis |
| RRN | F37 | `f037_rrn` | corrélation/audit | généré aujourd'hui, règle Visa à durcir |
| Autorisation | F38 | `f038_authorization_code` | TC05/TCR0/TCR5 | existant partiel |
| Réponse | F39 | `f039_response_code` | TCR5 et décision | existant |
| Terminal | F41 | terminal logique | traçabilité | existant |
| Commerçant | F42/F43 | acceptor + localisation structurée | TC05/TCR0 | enrichissement requis |
| Devise | F49 | devise ISO numérique | source/authorization currency | existant |
| E-commerce | F60.8/F60.9 | indicateurs validés | qualification/clearing | à mapper |
| ACI | F62.1 | `f062_1_aci` | TC05/TCR0 | obligatoire pour le pont |
| TID Visa | F62.2 | `f062_2_transaction_id` | TC05/TCR5 et cycle de vie | obligatoire pour le pont |
| Validation | F62.3 | `f062_3_validation_code` | TC05/TCR5 | obligatoire pour le pont |
| XID | F126.8 si applicable | empreinte/référence | preuve e-commerce | conditionnel |
| CAVV | F126.9 si applicable | non persisté ; résultat seulement | qualification e-commerce | port/mapping à créer |
| Indicateur 3DS | F126.20 si applicable | statut/ECI/DS ID | qualification e-commerce | socle 3DS existant |
| Annulation | 0400/0420 + F90/F62.2 | lien original + montant annulé | éligibilité/cycle de vie | à créer |

## Base II

| Objet | Format/règle observée | Implémentation cible | Dépendance ouverte |
|---|---|---|---|
| CTF | TCR fixes 168 octets | codec `sg-visa-base2-common` | aucune pour le format physique |
| ITF | 170 octets, hash binaire | incrément ultérieur | règle de hash et transport à confirmer |
| Header | TC90 | builder/validator | CIB, sécurité et identifiants officiels |
| Présentation achat | TC05/TCR0 | projection EOD | catalogues de codes |
| Payment service | TC05/TCR5 | TID/validation/montants/réponse | règles conditionnelles détaillées |
| Batch trailer | TC91 | compteurs et totaux | aucune pour le noyau |
| File trailer | TC92 | compteurs et totaux | aucune pour le noyau |
| ARN | 23 chiffres structurés | service générateur versionné | acquiring identifier/convention membre |
| Dispute financial | TC15/16/17 | dossier + outgoing | codes de raison officiels |
| Dispute response | TC05/06/07, usage adapté | événement et réponse | règles VROL |
| Reversals litige | TC25-27/TC35-37 | événements liés | règles VROL |
| VROL | TCR4 + TC33 | adaptateur/catalogue | manuel VROL/VCRFS |
| Retrieval | TC52 restreint/retiré | capacité désactivée par défaut | règle régionale officielle |
| Frais | TC10 | moteur versionné | règles/tarifs Visa |
| Change | TC56 incoming | catalogue de taux daté | abonnement et données de recette |
| Settlement | TC46 | intégration et positions | guide VSS manquant |

## Pages de référence principales

- Visa Online : correspondance des messages, pages PDF 23 à 30 ; structure,
  pages 31 à 40 ; F37/F38, pages 165 à 169 ; F62.1/F62.2/F62.3, pages 325 à
  338 ; e-commerce, pages 734 à 760 ; annulations e-commerce, pages 845 à 847.
- Base II TC01-TC49 : architecture des fichiers, pages 15 à 24 ; TC05, pages
  39 à 205 ; TCR4 VROL, pages 170 à 173 ; TCR5, pages 174 et suivantes ; TC33,
  pages 306 à 335 ; TC46, page 813.
- Base II TC50-TC92 : CTF/ITF, pages 8 à 17 ; TC52, pages 128 à 140 ; TC56,
  pages 150 à 170 ; TC90, pages 337 à 356 ; TC91/TC92, pages 357 à 365.
