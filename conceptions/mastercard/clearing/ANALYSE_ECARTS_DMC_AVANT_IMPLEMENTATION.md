# Analyse des écarts DMC avant implémentation

**Statut :** rapport de cadrage préalable au développement
**Date d'analyse :** 28 juillet 2026
**Référence normative :** *Mastercard Network Processing Dual Message Clearing System Guide*, 4 novembre 2025
**Sources :**

- `documents/specifications/mastercard/dmc/m_DMC_guide_en-us.pdf`
- `documents/specifications/mastercard/dmc/m_DMC_guide_en-us.txt`

**Modules analysés :**

- `sg-mc-dmas-member`
- `sg-mc-dmas-mastercard`
- `sg-dmcs-acquirer`
- `sg-dmcs-issuer`
- modèles DMAS/DMCS présents dans `sg-common`
- scripts SQL associés

## 1. Décisions fonctionnelles confirmées

DMAS couvre l'autorisation en ligne. DMCS couvre le clearing batch.

```text
CÔTÉ MEMBRE
sg-mc-dmas-member
        -> autorisations membre
        -> batch EOD membre
        -> tables clearing propres à sg-dmcs-acquirer
        -> IPM outgoing membre

CÔTÉ MASTERCARD / ISSUER SIMULÉ
sg-mc-dmas-mastercard
        -> autorisations issuer
        -> batch EOD issuer
        -> tables clearing propres à sg-dmcs-issuer
        -> traitement IPM incoming et outgoing issuer
```

Chaque côté est autonome :

- il lit uniquement ses propres tables d'autorisation DMAS ;
- il possède ses propres journées métier, batches et transactions clearing ;
- ses tables clearing sont alimentées par le batch EOD local et par les fichiers
  IPM incoming ;
- il ne génère jamais un fichier IPM directement depuis les tables
  d'autorisation ;
- il conserve les liens vers l'autorisation source et vers le fichier IPM
  source ;
- le fichier clearing reçu fait foi pour la comptabilisation, sous réserve de
  ses contrôles de conformité ;
- une autorisation locale absente du clearing reçu reste suspecte et ne produit
  pas automatiquement une écriture définitive.

La construction détaillée de l'ARN/DE 31 est volontairement reportée. DE 31
reste néanmoins obligatoire dans les messages concernés ; le premier incrément
devra donc isoler sa production derrière une interface sans prétendre valider
encore sa règle métier définitive.

## 2. Faits structurants de la spécification

### 2.1 Séparation autorisation, clearing et settlement

L'autorisation crée l'obligation financière. Le clearing échange les données
financières par lots et calcule les coûts et positions. Le settlement agrège
les positions en un montant net journalier.

Références : pages PDF 52 à 59.

### 2.2 Rôles

- l'acquéreur soumet les présentations ;
- Mastercard/GCMS valide, enrichit, route, calcule les frais et les positions ;
- l'issuer reçoit les présentations et comptabilise les opérations porteur ;
- tous les participants sont émetteurs et récepteurs de fichiers clearing.

Références : pages PDF 53 à 59 et 64 à 66.

### 2.3 Messages IPM

IPM est fondé sur ISO 8583:1993 et contient :

- un MTI ;
- un bitmap primaire et, si nécessaire, secondaire ;
- des data elements ;
- des Private Data Subelements Mastercard.

Messages principaux :

| MTI / fonction | Rôle |
|---|---|
| `1240/200` | First Presentment |
| `1240/205` | Second Presentment complète |
| `1240/282` | Second Presentment partielle |
| `1442/450` | First Chargeback complet |
| `1442/453` | First Chargeback partiel |
| `1442/451` | Arbitration Chargeback complet, marchés autorisés |
| `1442/454` | Arbitration Chargeback partiel, marchés autorisés |
| `1644/697` | File Header |
| `1644/695` | File Trailer |
| `1644/696` | Financial Detail Addendum |
| `1644/680` | File Currency Summary |
| `1644/685` | Financial Position Detail |
| `1644/688` | Settlement Position Detail |
| `1644/691` | Message Exception |
| `1644/699` | File Reject |
| `1740/700` | Fee Collection |
| `1740/780` | Fee Collection Return |
| `1740/781` | Fee Collection Resubmission |
| `1740/782` | Fee Collection Arbitration Return |
| `1740/783` | Fee Collection générée par le clearing |

Références : pages PDF 61 à 82.

### 2.4 Structure physique du fichier

- chaque message IPM est précédé d'un RDW de quatre octets ;
- le fichier commence obligatoirement par `1644/697` ;
- il se termine obligatoirement par `1644/695` ;
- un addendum `1644/696` suit immédiatement la présentation associée ;
- un `1644/691` précède immédiatement le message rejeté ;
- DE 71 est unique, strictement croissant et commence à `00000001` dans le
  header de chaque fichier logique ;
- le trailer porte l'identité du fichier, les checksums de montants et les
  compteurs de messages.

Références : pages PDF 70, 76 à 80, 93 à 95 et 2233 à 2235.

### 2.5 Cycles métier

```text
First Presentment
    -> First Chargeback complet ou partiel
    -> Second Presentment complète ou partielle
    -> pré-arbitrage / arbitrage selon les services et marchés applicables
```

Les chargebacks standards sont normalement pilotés par Claims Manager. Le
guide autorise la soumission directe de certains messages `1442` seulement dans
des marchés spécifiques. Pour le simulateur, ces messages restent utiles pour
reproduire le circuit financier, mais le mode de fonctionnement doit être
identifié explicitement comme simulation DMC.

Références : pages PDF 66 à 67, 72, 75 à 76 et 157 à 169.

### 2.6 Cycles de clearing et reconciliation

- GCMS traite six cycles principaux par jour ;
- l'originateur reçoit des messages d'acknowledgment ;
- le destinataire reçoit des messages de notification ;
- les totaux concernent uniquement les fichiers et messages financiers
  acceptés ;
- les fichiers entrants peuvent regrouper plusieurs origines et plusieurs
  cycles ;
- les messages de reconciliation `1644/680`, `1644/685` et `1644/688`
  portent les totaux clearing et settlement.

Références : pages PDF 66 à 70, 78, 189 à 213, 2042 à 2073 et 2240 à 2246.

### 2.7 Validation, erreurs et doublons

GCMS :

- valide le format, la syntaxe et le contenu ;
- continue à traiter les messages valides lorsqu'un autre message est rejeté ;
- retourne les erreurs de message via `1644/691` ;
- retourne le rejet du fichier via `1644/699` ;
- détecte fichiers et écritures en doublon ;
- supporte des mécanismes de reversal de fichier.

Références : pages PDF 54, 59, 79 à 81, 1409 à 1854.

## 3. État réel de l'implémentation

### 3.1 `sg-dmcs-acquirer`

Fonctions présentes :

- lecture directe de `acq_authorizations` approuvées ;
- lecture optionnelle des reversals et advices ;
- création de lignes représentant un header, des `1240/200`, des
  `1644/696` et un trailer ;
- persistance dans `acq_ipm_files` et `acq_ipm_records` ;
- écriture de fichiers `.txt` et `.ipm` ;
- chargement d'un fichier texte de chargebacks ;
- anti-doublon élémentaire par checksum.

### 3.2 `sg-dmcs-issuer`

Fonctions présentes :

- lecture d'un fichier texte de présentations ;
- persistance dans `iss_ipm_files` et `iss_ipm_records` ;
- génération directe de `1442` depuis `iss_authorizations` ;
- anti-doublon élémentaire par checksum.

### 3.3 Format réellement produit

Le format actuel n'est pas un encodage IPM jPOS :

```text
1240|200|PAN=...|AMT=...|RRN=...|...
```

Le prétendu fichier binaire contient ces lignes texte précédées d'un entier
Java de quatre octets. Il n'existe :

- ni `ISOMsg` ;
- ni bitmap IPM ;
- ni packager jPOS DMC ;
- ni encodage normatif des DE/PDS ;
- ni parseur RDW/IPM symétrique ;
- ni test par golden file.

### 3.4 Modèle de données actuel

Les tables `acq_ipm_*` et `iss_ipm_*` représentent des fichiers et des
enregistrements techniques. Elles ne constituent pas une transaction clearing
consolidée.

La génération DMCS utilise directement les anciennes entités
`AcqAuthorization` et `IssAuthorization`, mappées sur `acq_authorizations` et
`iss_authorizations`, puis leur ajoute des drapeaux `ipm_generated`. Cela
mélange :

- la source d'autorisation ;
- l'éligibilité clearing ;
- la génération de fichier ;
- le traitement du cycle de litige.

Ces tables ne sont pas les journaux réels des applications DMAS actuelles :

- `sg-mc-dmas-member` construit et envoie les messages, mais ne persiste pas
  actuellement un journal transactionnel complet propre au membre ;
- `sg-mc-dmas-mastercard` persiste un sous-ensemble dans
  `mc_dmas_transactions` ;
- `mc_dmas_transactions` ne conserve que PAN, STAN, transmission date, MTI,
  processing code, montant, devise, réponse, statut et dates techniques ;
- les données indispensables au clearing comme DE 12, DE 18, DE 22, DE 23,
  DE 32, DE 37, DE 38, DE 41, DE 42, DE 43, DE 48, DE 55 et DE 61 n'y sont
  pas conservées intégralement ;
- les scripts contiennent également d'anciennes tables `dmas_acq_*` et
  `dmas_iss_*` qui ne sont pas les sources effectivement utilisées par les
  deux applications actuelles.

Une mise à niveau préalable des journaux DMAS est donc obligatoire, de la même
manière que la mise à niveau SID réalisée avant SWAM LIS.

### 3.5 Tests

Les deux modules DMCS ne contiennent actuellement aucun test sous `src/test`.
Il n'existe pas de scénario E2E couvrant :

- EOD membre et issuer ;
- création des transactions clearing ;
- génération d'un IPM conforme ;
- transfert et intégration bilatérale ;
- chargeback ;
- seconde présentation ;
- reconciliation et équilibre des positions.

## 4. Matrice synthétique des écarts

| Domaine | Exigence cible | Existant | Écart |
|---|---|---|---|
| Source clearing | batch EOD depuis DMAS vers table clearing propre | génération directe depuis autorisations | critique |
| Journal DMAS membre | transaction d'autorisation complète et stable | aucun journal complet propre au membre | critique |
| Journal DMAS issuer | transaction d'autorisation complète et stable | `mc_dmas_transactions` trop pauvre | critique |
| Autonomie des côtés | tables membre et issuer séparées | fichiers séparés, pas de transactions clearing séparées | critique |
| Packager | jPOS IPM ISO 8583:1993 | texte `KEY=VALUE` | critique |
| Fichier physique | RDW + messages packagés | longueur Java + texte | critique |
| Header | `1644/697` + DE/PDS obligatoires | acquirer utilise `1644/685` | critique |
| Trailer | `1644/695` + checksums/compteurs | acquirer utilise `1644/686` | critique |
| First Presentment | layout obligatoire complet/conditionnel | sous-ensemble non validé | critique |
| Chargeback | `1442/450` ou `453`, lien à la présentation | ligne texte créée depuis l'autorisation | critique |
| Second Presentment | `1240/205` ou `282` | absente | critique |
| Import IPM | lecture RDW + unpack jPOS | lecture ligne par ligne | critique |
| Contrôles | présence, longueur, types, ordre, séquence | contrôles très limités | critique |
| Erreurs | `1644/691` et `1644/699` | absentes | majeur |
| Reconciliation | `680/685/688`, ack et notification | absente | critique |
| Settlement | positions nettes par cycle/jour/devise | absent | critique |
| Frais | `1740` et interchange configuré | absent | majeur |
| Change | taux, montants de reconciliation et billing | simples copies montant/devise | majeur |
| Doublons | identité fichier/message/transaction | checksum fichier uniquement | majeur |
| Reversals | règles DMC et cycle de vie | conversion simplifiée en `1240/200` | majeur |
| Journée métier | calendrier, cycle et idempotence | date courante | critique |
| États métier | match, suspect, comptabilisé, litige | flags techniques | critique |
| Audit | événements immuables | journal fichier minimal | majeur |
| Configuration | chemins/IDs/secrets externes | chemins `D:/` et credentials en YAML | majeur |
| Sécurité | RBAC et secrets externes | Basic Auth en mémoire | majeur |
| Tests | unitaires, golden files, intégration, E2E | absents | critique |

## 5. Architecture cible

### 5.1 Bibliothèque commune DMC

Créer un socle non déployable, soit dans `sg-common` avec un package DMC
strictement isolé, soit dans un nouveau `sg-dmcs-common`.

Décision recommandée : `sg-dmcs-common`, afin de ne pas réintroduire un scan JPA
global et de séparer le format DMC des autres réseaux.

Responsabilités :

- packager jPOS IPM ;
- modèles immuables des messages ;
- codec RDW ;
- codec PDS ;
- règles de présence par MTI/fonction ;
- validation structurelle ;
- compteurs et checksums ;
- catalogue des codes ;
- lecture/écriture streaming ;
- fixtures et golden files.

Le module commun ne contient aucune entité JPA membre ou issuer.

### 5.2 Tables propriétaires

Chaque application clearing possède ses propres tables :

```text
dmcs_acquirer_business_day
dmcs_acquirer_batch_execution
dmcs_acquirer_clearing_transaction
dmcs_acquirer_file
dmcs_acquirer_message
dmcs_acquirer_validation_error
dmcs_acquirer_dispute
dmcs_acquirer_dispute_event
dmcs_acquirer_reconciliation
dmcs_acquirer_settlement_position
dmcs_acquirer_accounting_entry

dmcs_issuer_business_day
dmcs_issuer_batch_execution
dmcs_issuer_clearing_transaction
dmcs_issuer_file
dmcs_issuer_message
dmcs_issuer_validation_error
dmcs_issuer_dispute
dmcs_issuer_dispute_event
dmcs_issuer_reconciliation
dmcs_issuer_settlement_position
dmcs_issuer_accounting_entry
```

Les entités et repositories restent physiquement dans le module propriétaire.

### 5.3 Transaction clearing consolidée

Champs fonctionnels minimaux :

```text
id
business_day_id
local_authorization_id
incoming_file_id
incoming_message_id
source_presence
match_status
cycle_type
cycle_number
functional_key
pan_fingerprint
masked_pan
processing_code
transaction_amount
transaction_currency
reconciliation_amount
reconciliation_currency
billing_amount
billing_currency
local_transaction_at
stan
rrn
authorization_code
mcc
pos_data_code
terminal_id
merchant_id
merchant_name_location
acquirer_institution_id
issuer_institution_id
clearing_status
accounting_status
dispute_status
created_at
updated_at
version
```

Contraintes :

- unicité de la source locale et du cycle ;
- unicité du message IPM incoming ;
- index de rapprochement ;
- aucun PIN, MAC, piste ou message d'autorisation brut ;
- PAN exposé uniquement masqué, avec empreinte contrôlée pour le
  rapprochement.

### 5.4 Batch EOD

Le batch est idempotent :

1. ouvre ou reprend une journée métier ;
2. sélectionne les autorisations financièrement éligibles ;
3. prend en compte les advices et reversals ;
4. construit une transaction clearing locale ;
5. calcule une clé fonctionnelle stable ;
6. classe la ligne `LOCAL_ONLY` ;
7. ne marque jamais directement l'autorisation comme « fichier généré » ;
8. trace l'exécution, les lectures, écritures, skips et erreurs.

Le côté membre lit le journal propriétaire de `sg-mc-dmas-member`. Le côté
issuer lit le journal propriétaire de `sg-mc-dmas-mastercard`.

Ces journaux d'autorisation seront physiquement séparés :

```text
mc_dmas_member_transactions
mc_dmas_issuer_transactions
```

Ils conservent la même identité fonctionnelle et les données nécessaires au
clearing, mais chaque application écrit exclusivement dans sa table. Les
modules DMCS disposent uniquement du droit de lecture sur le journal DMAS qui
leur correspond.

### 5.5 Génération outgoing

1. sélectionner les transactions `READY_TO_PRESENT` de la journée et du cycle ;
2. créer un fichier DMC en statut `BUILDING` ;
3. produire `1644/697` avec DE 71 = `00000001` ;
4. produire les `1240/200` et addenda applicables ;
5. produire les litiges ou fees prêts à envoyer ;
6. produire `1644/695` avec compteurs et checksums ;
7. packager chaque `ISOMsg` avec jPOS ;
8. préfixer chaque message avec son RDW ;
9. écrire atomiquement le fichier ;
10. relire et revalider le fichier produit ;
11. figer les liens transaction-message-fichier ;
12. passer le fichier à `READY_TO_SEND`.

### 5.6 Intégration incoming

1. calculer l'empreinte et détecter les doublons ;
2. lire les RDW en streaming ;
3. unpackager chaque message avec jPOS ;
4. valider header, trailer, DE 71, compteurs, checksums et ordre des addenda ;
5. conserver les erreurs avant tout impact comptable ;
6. créer ou enrichir les transactions clearing locales ;
7. rapprocher avec les transactions issues du batch EOD ;
8. classer `BOTH`, `IPM_ONLY` ou `LOCAL_ONLY`;
9. produire les erreurs de message/fichier simulées ;
10. calculer reconciliation et positions ;
11. autoriser la comptabilisation uniquement après validation.

### 5.7 Rapprochement

La corrélation ne dépend pas d'un seul champ. Elle combine selon disponibilité :

- identité du cycle de vie ;
- PAN fingerprint ;
- montant et devise ;
- date/heure ;
- RRN ;
- STAN ;
- code d'autorisation ;
- acquiring institution ;
- terminal et commerçant.

États minimaux :

```text
LOCAL_ONLY
IPM_ONLY
MATCHED
MATCH_PROPOSED
MANUALLY_MATCHED
MANUALLY_VALIDATED
REJECTED
READY_TO_ACCOUNT
ACCOUNTED
```

### 5.8 Litiges

```text
FIRST_PRESENTMENT
    -> FIRST_CHARGEBACK_FULL | FIRST_CHARGEBACK_PARTIAL
    -> SECOND_PRESENTMENT_FULL | SECOND_PRESENTMENT_PARTIAL
    -> ACCEPTED | CLOSED | PRE_ARBITRATION
```

Chaque côté possède ses dossiers émis et reçus. Un événement immuable conserve
chaque transition. Le chargeback doit toujours référencer la présentation
clearing, pas seulement l'autorisation d'origine.

Le Maker/Checker du portail s'appliquera aux transitions qui produisent un
message outgoing.

### 5.9 Reconciliation, frais et settlement

Le simulateur doit au minimum :

- calculer les totaux par MTI/fonction, devise, sens et cycle ;
- distinguer accepted/rejected ;
- produire les messages `680/685/688` nécessaires ;
- calculer des écritures équilibrées ;
- conserver les positions acquéreur, issuer et réseau ;
- rendre les règles d'interchange, de frais et de change paramétrables et
  versionnées ;
- utiliser `1740` pour les fee collections applicables.

## 6. Mise à niveau préalable des journaux DMAS

Avant le premier EOD, les deux journaux DMAS doivent conserver :

```text
id, interface_id, bank_code, direction,
mti_request, mti_response,
pan_fingerprint, masked_pan,
de003_processing_code, de004_amount,
de007_transmission_datetime, de011_stan,
de012_local_time, de013_local_date,
de014_expiry, de018_mcc,
de022_pos_entry_mode, de023_card_sequence,
de032_acquiring_id, de033_forwarding_id,
de037_rrn, de038_authorization_code,
de039_response_code, de041_terminal_id,
de042_acceptor_id, de043_acceptor_name_location,
de048_additional_data, de049_currency,
de055_icc_data, de061_pos_data,
approved, reversed, reversal_at,
request_received_at, response_sent_at,
clearing_eligible, clearing_cycle_status,
created_at, updated_at, version
```

Les journaux ne conservent pas DE 52, les clés ou le MAC. Les données EMV et
privées doivent être protégées et leur conservation limitée à ce qui est
nécessaire pour le clearing.

Le côté membre doit enregistrer la requête envoyée et enrichir la même ligne à
la réception de la réponse. Le côté issuer doit enregistrer la requête reçue et
la réponse qu'il émet. Une identité unique réseau empêche les doublons.

## 7. Mapping DMAS vers clearing : noyau initial

| Clearing | Source DMAS |
|---|---|
| autorisation source | identifiant du journal DMAS membre ou issuer propriétaire |
| PAN protégé | `de002_pan` / `de002_pan_raw`, transformé immédiatement |
| processing code | `de003_proc_code` |
| montant transaction | `de004_amount` |
| date/heure réseau | `de007_datetime` |
| STAN | `de011_stan` |
| date/heure locale | `de012_local_time` + `de013_local_date` |
| MCC | `de018_mcc` |
| POS data | `de022_pos_mode`, insuffisant pour tout DE 22 DMC |
| acquiring institution | `de032_acq_id` |
| RRN | `de037_rrn` |
| code autorisation | `de038_auth_code` |
| réponse | `de039_response` |
| terminal | `de041_term_id` |
| commerçant | `de042_merch_id` |
| nom/localisation | `de043_merch_name` |
| devise transaction | `de049_currency` |
| décision | `approved` |
| horodatage source | `sent_at` ou `received_at/responded_at` |

Données insuffisantes à compléter par configuration ou enrichissement :

- DE 22 complet ;
- DE 43 structuré ;
- DE 33, DE 93, DE 94 et DE 100 ;
- devises et montants de reconciliation et billing ;
- taux de conversion ;
- product identifiers et licensed product identifiers ;
- business activity et settlement indicators ;
- données e-commerce/3DS et EMV conditionnelles ;
- règles d'interchange ;
- détails nécessaires aux PDS obligatoires.

Ces valeurs ne doivent pas être inventées dans les builders.

## 8. Ordre d'implémentation retenu

### Incrément -1 - Mise à niveau DMAS

- créer les journaux `mc_dmas_member_transactions` et
  `mc_dmas_issuer_transactions` ;
- journaliser le même échange des deux côtés de la liaison permanente ;
- conserver les données nécessaires au mapping DMC ;
- gérer réponse, reversal et éligibilité clearing ;
- vérifier que les deux journaux restent indépendants ;
- ajouter les tests de non-régression DMAS.

### Incrément 0 - Socle et tests de format

- créer `sg-dmcs-common` ;
- packager jPOS DMC ;
- codec RDW ;
- header `1644/697`, trailer `1644/695`, `1240/200` ;
- règles de présence du premier périmètre ;
- golden files et round-trip pack/unpack.

### Incrément 1 - Journée métier et présentation

- migrations des tables acquirer et issuer ;
- EOD depuis les tables DMAS propriétaires ;
- transactions clearing consolidées ;
- génération outgoing acquirer ;
- intégration incoming issuer ;
- rapprochement ;
- reconciliation minimale et écritures équilibrées.

### Incrément 2 - Chargeback et seconde présentation

- dossier de litige ;
- `1442/450` et `1442/453` ;
- intégration côté acquirer ;
- Maker/Checker ;
- `1240/205` et `1240/282` ;
- intégration côté issuer ;
- échéances et audit.

### Incrément 3 - Erreurs, rejets et idempotence complète

- `1644/691` ;
- `1644/699` ;
- message, fichier et transaction duplicate detection ;
- rejeu, quarantaine et fichier régénéré ;
- reversals de transactions et de fichiers.

### Incrément 4 - Frais, change et settlement

- règles versionnées ;
- `1740` ;
- conversion ;
- `1644/680`, `685`, `688` complets ;
- positions nettes multi-devises.

### Incrément 5 - Services avancés

- addenda métier ;
- e-commerce, 3DS, EMV et tokenisation ;
- retrieval lorsque le marché le permet ;
- pré-arbitrage/arbitrage et intégration Claims Manager simulée ;
- rapports et fichiers complémentaires.

## 9. Critères d'acceptation du premier E2E

1. lancer `sg-mc-dmas-member` et `sg-mc-dmas-mastercard` ;
2. exécuter des autorisations approuvées et refusées ;
3. lancer séparément l'EOD acquirer et issuer ;
4. vérifier que chaque DMCS lit uniquement ses autorisations locales ;
5. vérifier l'idempotence d'un deuxième EOD ;
6. produire un IPM outgoing acquirer avec jPOS et RDW ;
7. relire le fichier produit avec le même packager ;
8. intégrer le fichier côté issuer ;
9. rapprocher les présentations ;
10. classer les écarts local-only et IPM-only ;
11. produire les reconciliation totals ;
12. générer un chargeback issuer ;
13. intégrer le chargeback acquirer ;
14. générer une seconde présentation ;
15. intégrer la seconde présentation issuer ;
16. contrôler l'audit, les compteurs et les écritures équilibrées ;
17. arrêter tous les services sans processus résiduel.

## 10. État de l'implémentation du premier socle

Le socle suivant est maintenant développé :

- module séparé `sg-dmcs-common` ;
- packager jPOS ISO 8583:1993 avec données et préfixes de longueur EBCDIC,
  bitmap binaire et RDW VBS ;
- construction et validation de l'enveloppe `1644/697` - `1240/200` -
  `1644/695` ;
- PDS 0105, 0122, 0301 et 0306 ;
- contrôle strict du DE71, du File ID, du checksum des DE4 et du nombre de
  messages ;
- journaux d'autorisation séparés
  `mc_dmas_member_transactions` / `mc_dmas_issuer_transactions` ;
- alimentation des deux journaux par les échanges DMAS réels, sans DE52 ;
- tables de clearing séparées
  `dmcs_acquirer_clearing_transactions` /
  `dmcs_issuer_clearing_transactions` ;
- EOD idempotent de chaque côté, lisant uniquement le journal DMAS de son
  propriétaire ;
- mapping du code POS DMC DE22 selon la matrice du guide pages 286-289 ;
- lecture des fichiers entrants jPOS/RDW et intégration des présentations,
  chargebacks et secondes présentations dans la table propriétaire ;
- désactivation par défaut des anciens services DMCS texte et de leur lecture
  directe des anciennes tables d'autorisation.

La génération d'une First Presentment locale reste volontairement bloquée par
la validation lorsque DE31 est absent. Conformément à la décision prise, aucune
valeur ARN fictive n'est créée dans cet incrément. La définition de la règle
DE31/ARN sera traitée séparément avant le premier fichier outgoing E2E.

Restent notamment à développer : rapprochement, dossiers de litige
Maker/Checker, génération 1442 puis 1240 de seconde présentation,
reconciliation, comptabilisation et settlement.

## 11. Conclusion

Les modules existants constituent une base de prototype utile :

- rôles acquirer/issuer séparés ;
- entités fichier/enregistrement ;
- premiers builders de présentation et chargeback ;
- APIs de génération et lecture ;
- anti-doublon par checksum.

Ils ne sont toutefois pas encore conformes à un clearing DMC/IPM exploitable.
Les écarts structurants sont le format non jPOS, l'absence de transactions
clearing alimentées par EOD, l'absence de seconde présentation, de validation
normative, de reconciliation, de settlement et de tests.

Le développement doit conserver les modules déployables existants, remplacer
progressivement les builders texte et introduire le socle commun et les tables
propriétaires sans coupler les deux systèmes.
