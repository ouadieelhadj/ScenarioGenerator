# Conception SWAM LIS avant implémentation

**Statut :** proposition à valider
**Référence :** `SPC/LIS - 4.13`, document interne daté du 18/01/2019
**Source :** `documents/specifications/swam/Local Interchange Specifications - LIS4 14-CMI.pdf`
**Décision de gouvernance :** aucun code LIS ne sera commencé avant validation de ce document.

## 1. Objet

Le SID déjà présent dans `sg-swam-issuer` et `sg-swam-acquirer` couvre les
échanges ISO 8583 en ligne entre un membre et le switch SWAM.

LIS est une chaîne distincte de compensation et de règlement. Elle doit :

- transformer les transactions de la journée en présentations de clearing ;
- échanger quotidiennement des fichiers entre le switch et chaque membre ;
- gérer les litiges et leurs cycles ;
- calculer les frais, commissions et montants de règlement ;
- fournir les écritures et positions nécessaires au settlement ;
- permettre le rapprochement avec les transactions SID.

Le périmètre cible comporte deux applications déployables et une bibliothèque
commune :

```text
sg-swam-lis-member  <---- fichiers LIS ---->  sg-swam-lis-switch
            \                                  /
             +------ sg-swam-lis-common ------+
```

## 2. Faits imposés par la spécification

### 2.1 Cycle quotidien

Le switch effectue son traitement LIS une fois par jour, du lundi au samedi,
hors jours fériés, sur les journées métier. Il produit un LIS pour chaque
membre, même lorsqu’il n’existe aucune transaction : le fichier contient alors
uniquement ses en-têtes et fins de fichier.

Chaque membre produit également un LIS retour pour le switch, même vide.

La spécification prévoit :

- LIS switch vers membre : livraison annoncée entre 14 h et 15 h ;
- LIS retour membre vers switch : transmission entre 9 h et 11 h ;
- transport par CFT ou solution compatible ;
- le lundi, réception de deux LIS côté membre : traitement du samedi et
  traitement du lundi.

Références : pages PDF 19 et 22.

### 2.2 Structure physique

- un enregistrement physique est un **TCR** ;
- chaque TCR fait exactement **256 octets** ;
- un enregistrement logique est un **TC** ;
- un TC contient de 1 à 10 TCR ;
- `TCRx/F.003` contient le numéro de séquence du composant ;
- la valeur `0` indique le début d’un nouveau TC ;
- `TCRx/F.002` porte la séquence physique dans le fichier ;
- depuis la version 4.12, une séquence complémentaire de deux chiffres est
  placée dans les fillers de certains TCR lorsque la séquence principale atteint
  `999999`.

Références : pages PDF 4 et 20.

### 2.3 Structure logique

Le fichier contient obligatoirement cinq sous-fichiers dans cet ordre :

| Ordre | Sous-fichier | Contenu |
|---:|---|---|
| 1 | `FC` | Commerçants domiciliés chez le membre |
| 2 | `FV` | Activité Visa concernant les porteurs du membre |
| 3 | `FM` | Activité Mastercard concernant les porteurs du membre |
| 4 | `FL` | Activité locale SWAM |
| 5 | `FS` | Image de messages SID et services complémentaires |

Le sous-fichier `FL` est le cœur du clearing local demandé. `FS` est
principalement destiné au rapprochement avec les messages SID en ligne.

Références : pages PDF 20 et 21.

### 2.4 Enveloppes obligatoires

| Niveau | En-tête | Fin |
|---|---:|---:|
| Fichier LIS complet | `TC90` | `TC91` |
| FC | `TC92` | `TC93` |
| FV | `TC94` | `TC95` |
| FM | `TC96` | `TC97` |
| FL | `TC98` | `TC99` |
| FS | `TC80` | `TC81` |

Le `TC90/TCR0` identifie notamment :

- le membre destinataire ;
- la date de traitement du switch ;
- la séquence de fichier ;
- le statut normal ou régénéré ;
- le membre initiateur.

Le `TC91/TCR0` contient le nombre total de TCR et les compteurs de contrôle par
famille de transactions. Les trailers de sous-fichiers contiennent les
compteurs propres à leur contenu.

Références : pages PDF 23, 31, 43 à 59.

## 3. Flux métier de clearing

### 3.1 Présentation et crédit

| TC | Fonction |
|---:|---|
| `05` | Présentation achat : débit du porteur |
| `06` | Présentation credit voucher/remboursement : crédit du porteur |
| `07` | Présentation cash/retrait : débit du porteur |

Une présentation peut porter le cycle `1` ou `2` :

- cycle `1` : première présentation ;
- cycle `2` : seconde présentation, c’est-à-dire représentation.

Les TC de transaction sont composés de plusieurs TCR :

- TCR0 : commerçant, terminal, routage et informations générales ;
- TCR1 : transaction, autorisation, références, montants, monnaies et frais ;
- TCR2 : données transport aérien lorsque nécessaires ;
- TCR3 : données EMV lorsque nécessaires.

Références : pages PDF 29, 32 et 70 à 85.

### 3.2 Chargeback et représentation

| Étape | Achat | Credit voucher | Cash/retrait |
|---|---:|---:|---:|
| Présentation | `TC05` | `TC06` | `TC07` |
| Chargeback | `TC15` | `TC16` | `TC17` |
| Redressement présentation | `TC25` | `TC26` | `TC27` |
| Redressement chargeback | `TC35` | `TC36` | `TC37` |

Le cycle complet supporté par les compteurs LIS est :

```text
première présentation
  -> chargeback
  -> seconde présentation / représentation
  -> second chargeback
```

Chaque étape peut également avoir un redressement. Le numéro de cycle porté dans
les enregistrements distingue la première de la seconde occurrence.

Les statuts du futur moteur de litiges ne doivent pas être déduits uniquement du
TC : ils doivent combiner TC, cycle, indicateur de routage, référence d’origine,
motif et statut de transaction.

Références : pages PDF 16, 32 à 33, 36 à 42 et 44 à 59.

### 3.3 Recherche de justificatifs

| TC | Fonction |
|---:|---|
| `51` | Demande du reçu original |
| `52` | Demande de copie/fax |
| `53` | Réponse à une demande |

Ces messages peuvent circuler dans `FV`, `FM` et `FL`.

### 3.4 Commerçants et remises

| TC | Fonction |
|---:|---|
| `70` | Crédit commerçant avec détail de règlement |
| `71` | Débit commerçant avec détail |
| `72` | Incident de paiement commerçant |
| `73` | Avis de remise achat sur GAB |
| `74` | Avis de remise cash advance |
| `75` | Avis de remise retrait |
| `76` | Avis de remise achat |
| `77` | Avis de remise credit voucher |

`TC70/71` contient notamment le MID, le RIB, les monnaies de remise et de
règlement, le montant net, les taux de conversion, le montant MAD, la date de
règlement, les frais de service commerçant, les remises et les informations de
recouvrement.

Le type de remise distingue notamment :

- manuelle ;
- POS/TPE ;
- ATM ;
- frais ;
- commerce électronique ;
- manuelle escomptée.

`TC72` renvoie l’identification du transfert rejeté et un motif tel que RIB
invalide, compte fermé ou monnaie de règlement invalide.

Références : pages PDF 15, 31, 60 à 69.

## 4. Frais, commissions, change et règlement

### 4.1 Frais unitaires

| TC | Sens comptable pour le membre receveur |
|---:|---|
| `10` | Débit relatif à un frais |
| `20` | Crédit relatif à un frais ou redressement d’un TC10 |

Le TCR0 contient notamment :

- sens incoming/outgoing ;
- produit de paiement ;
- institutions émettrice et réceptrice ;
- montant et monnaie source ;
- frais de traitement et d’autorisation ;
- frais d’interchange ;
- motif/type de frais ;
- montant de règlement et date.

Le TCR1 porte un message descriptif associé au frais.

Références : pages PDF 33 et 86 à 90.

### 4.2 Taux de change

`TC50` est un message générique. Certains types transportent des tables de taux :

- `0001` : taux de règlement Visa ;
- `0003` : taux de règlement Mastercard ;
- le texte libre est utilisé pour le type `0000`.

Chaque occurrence de taux contient monnaie, date de règlement, exposant et taux.

Le `TC46` porte également :

- monnaie de rapprochement ;
- monnaie de règlement ;
- taux rapprochement vers règlement ;
- exposant du taux ;
- date de valeur ;
- montants en monnaie de règlement ;
- montants convertis en MAD pour certains rapports.

La version 4.13 ajoute un identifiant de conversion de monnaie de clearing au
`TC05/TCR1`. La spécification inclut aussi le produit `9` pour l’activité DCC et
le reporting `22013`.

Références : pages PDF 4, 34, 97 et 139 à 140.

### 4.3 Règlement et reporting

`TC46` est l’enregistrement de règlement. Son champ `TYPE OF REPORTING` organise
les montants calculés.

Familles importantes pour le local :

| Famille | Signification |
|---|---|
| `01xxx` | Montants bruts de présentations, chargebacks et redressements par BIN |
| `02xxx` | Interchange et autres frais par BIN |
| `05xxx` | Activité achat sur GAB et frais associés |
| `21xxx` | Agrégats commerçant/membre, frais et commissions |
| `22xxx` | Montants nets de règlement |
| `23xxx` | Chargebacks et frais de chargeback |
| `24xxx` | Redressements et frais associés |
| `25xxx` | Redressements de chargeback |
| `26xxx` | Représentations et redressements de représentation |

Exemples explicitement décrits :

- `22001` : règlement net de la banque domiciliataire, activité carte locale en
  MAD ;
- `22006` : règlement net retrait local ;
- `22007` : règlement net cash advance local ;
- `22009` : règlement net achat sur GAB ;
- `22010` : règlement net achat ;
- `22011` : règlement net credit voucher ;
- `23001` à `23005` : montants bruts des chargebacks ;
- `24001` à `24005` : montants bruts des redressements ;
- `25001` à `25005` : redressements de chargeback ;
- `260xx` : représentations et redressements de représentation.

La spécification décrit aussi des variantes de `TC46` reproduisant plusieurs
groupes de rapports Visa. Elles devront être prises en charge pour `FV`, mais ne
doivent pas retarder le premier incrément local `FL`.

Références : pages PDF 17 à 18 et 121 à 155.

## 5. Contrôles obligatoires

Le chargement doit être transactionnel : aucun impact comptable avant validation
structurelle complète.

Contrôles minimaux imposés :

1. fichier lisible et longueur totale multiple de 256 ;
2. unicité de la combinaison du `TC90` :
   destination, date de traitement, séquence, statut et origine ;
3. présence de `TC90/91` et de tous les couples d’en-tête/trailer de
   sous-fichier ;
4. ordre strict `FC -> FV -> FM -> FL -> FS` ;
5. séquence physique continue de `TCRx/F.002` ;
6. cohérence de `TCRx/F.003` dans chaque TC ;
7. longueur et type de chaque champ ;
8. correspondance du compteur global `TC91` ;
9. correspondance des compteurs de `TC93/95/97/99/81` ;
10. existence du commerçant pour `TC70/71` ;
11. existence du porteur pour les `TC05..37` ;
12. détection des doublons fonctionnels ;
13. vérification des références d’origine pour litiges et redressements ;
14. contrôle d’équilibre des écritures de règlement.

Un fichier invalide est conservé avec ses erreurs, sans être comptabilisé. Une
nouvelle soumission corrigée doit rester traçable au fichier initial.

Références : pages PDF 23 et 24.

## 6. Responsabilités des modules

### 6.1 `sg-swam-lis-common`

Bibliothèque non déployable :

- catalogue versionné TC/TCR/champs ;
- parseur et sérialiseur fixed-width de 256 octets ;
- gestion des types numérique, alphanumérique et binaire ;
- règles de padding, exposants, signes, monnaies et dates ;
- validateurs de structure et compteurs ;
- modèles immuables des enregistrements ;
- dictionnaires des codes ;
- calcul des identités fonctionnelles et empreintes de fichiers ;
- jeux de fichiers de conformité.

La bibliothèque ne contient aucune règle propre au membre ou au switch.

### 6.2 `sg-swam-lis-switch`

- utilise comme source les tables transactionnelles créées et alimentées par
  `sg-swam-issuer` ;
- génère le fichier LIS outgoing du switch à partir des transactions éligibles
  enregistrées dans ces tables pour la journée métier clôturée ;
- clôture d’une journée métier ;
- ingestion des transactions SID du switch ;
- réception et chargement du LIS retour de chaque membre ;
- validation, quarantaine et rejeu contrôlé ;
- moteur de présentations ;
- moteur de chargebacks, représentations et redressements ;
- calcul des frais, interchange et conversions ;
- génération des `TC46` ;
- calcul des positions nettes par membre et monnaie ;
- génération du LIS sortant propre à chaque membre ;
- génération obligatoire d’un fichier vide lorsque nécessaire ;
- production de l’état LIS ;
- préparation des instructions de settlement ;
- audit et rapprochement global.

### 6.3 `sg-swam-lis-member`

- utilise comme source les tables transactionnelles créées et alimentées par
  l'application SWAM membre ;
- génère le fichier LIS outgoing du membre à partir des transactions éligibles
  enregistrées dans ces tables pour la journée métier concernée ;
- extraction des transactions SID locales depuis les tables du SWAM membre ;
- génération du LIS retour/outgoing du membre ;
- réception et chargement du LIS du switch ;
- validation des enveloppes, séquences et compteurs ;
- rapprochement avec les transactions, porteurs et commerçants locaux ;
- préparation des écritures porteur, commerçant, switch et comptes internes ;
- création et suivi des litiges ;
- génération des chargebacks, représentations, redressements et réponses ;
- rapports d’exceptions ;
- export des écarts et accusés opérationnels.

### 6.4 Règle de propriété des données source

Les modules clearing ne recréent pas les transactions SID et ne deviennent pas
leur source de vérité :

```text
tables SWAM membre
    -> sg-swam-lis-member
    -> LIS outgoing membre

tables sg-swam-issuer
    -> sg-swam-lis-switch
    -> LIS outgoing switch destiné à chaque membre
```

Chaque module clearing conserve ses propres tables de traitement, de
validation, de rapprochement et de settlement, mais les données transactionnelles
initiales proviennent obligatoirement de l'application SID correspondante. Un
identifiant stable doit relier chaque présentation LIS à la ligne SID source.

## 7. Modèle de données proposé

### 7.1 Fichiers et enregistrements

```text
lis_file
lis_subfile
lis_transaction_record
lis_component_record
lis_validation_error
lis_file_delivery
lis_business_day
```

`lis_file` doit conserver :

- direction ;
- membre source et destination ;
- date de traitement ;
- numéro de séquence ;
- statut normal/régénéré ;
- empreinte cryptographique ;
- état de validation ;
- état de chargement ;
- version LIS ;
- nom et emplacement du fichier original.

### 7.2 Clearing et litiges

```text
clearing_presentment
clearing_adjustment
clearing_dispute
clearing_dispute_event
retrieval_request
retrieval_response
```

Chaque objet métier conserve un lien vers :

- la transaction SID d’origine ;
- le fichier, TC et TCR source ;
- le membre acquéreur et le membre émetteur ;
- les références réseau, RRN, STAN et autorisation disponibles ;
- les montants et monnaies à chaque étape ;
- le cycle et le motif.

### 7.3 Frais et settlement

```text
clearing_fee
exchange_rate
settlement_report
settlement_position
settlement_entry
settlement_instruction
reconciliation_exception
```

Les écritures doivent être immuables et équilibrées. Une correction génère une
contre-écriture ; elle ne modifie pas une écriture déjà comptabilisée.

## 8. Rattachement SID vers LIS

Une table de mapping formelle sera requise pour chaque champ LIS. Elle devra
indiquer :

```text
TC/TCR/champ
<- champ SID ou donnée métier
<- règle de transformation
<- valeur par défaut
<- caractère obligatoire/conditionnel
<- source switch ou membre
```

Le rapprochement ne doit pas dépendre d’un seul identifiant. La clé de
corrélation utilisera, selon disponibilité :

- RRN ;
- STAN ;
- code d’autorisation ;
- PAN tokenisé ou empreinte contrôlée ;
- date et heure ;
- montant et monnaie ;
- MID/TID ;
- membre acquéreur et membre émetteur.

Le sous-fichier `FS`, notamment `TC82`, pourra fournir une image des messages
temps réel SID pour compléter le rapprochement.

## 9. Architecture d’échange

La spécification cite CFT ou compatible. L’implémentation doit isoler le
transport derrière un port :

```text
LisTransport
  - CftTransport
  - SftpTransport
  - LocalFolderTransport (tests)
```

Le dépôt et la réception suivent une convention atomique :

1. écriture sous nom temporaire ;
2. calcul de taille et empreinte ;
3. renommage atomique vers le nom final ;
4. enregistrement de la livraison ;
5. chargement idempotent.

Le nom exact des fichiers et les accusés de transport ne sont pas suffisamment
définis dans les pages fonctionnelles étudiées : ils constituent une décision
d’intégration à confirmer avec SWAM/CMI.

## 10. Proposition de réalisation

### Incrément 0 — conformité du format

- catalogue TC/TCR ;
- parseur/sérialiseur 256 octets ;
- `TC90/91`, `TC98/99` et enveloppes vides des autres sous-fichiers ;
- séquences, compteurs et tests golden files.

### Incrément 1 — clearing local de base

- `FL` ;
- première présentation `TC05/06/07` ;
- mapping des transactions SID ;
- génération switch vers membre ;
- chargement et rapprochement membre ;
- LIS retour vide obligatoire ;
- `TC46` local minimal et position nette.

### Incrément 2 — litiges

- `TC15/16/17` ;
- seconde présentation ;
- second chargeback ;
- `TC25/26/27` et `TC35/36/37` ;
- demandes `TC51/52/53` ;
- frais de litige.

### Incrément 3 — commerçants

- `FC` ;
- `TC70/71/72` ;
- avis `TC73..77` ;
- commissions, MDR/FSC et règlement commerçant.

### Incrément 4 — change et reporting complet

- `TC10/20` ;
- `TC50` ;
- catalogue complet `TC46` local ;
- DCC et frais de change ;
- rapprochement multi-monnaie.

### Incrément 5 — international et services

- `FV` et rapports Visa ;
- `FM` et rapports Mastercard ;
- `FS`, `TC82` et services complémentaires ;
- conformité complète du LIS 4.13.

## 11. Décisions à valider avant le code

Les réponses doivent être inscrites dans ce document.

1. **Version contractuelle :** le nom du PDF mentionne LIS4 14-CMI, mais le
   contenu porte `SPC/LIS - 4.13`. La version cible est-elle bien 4.13 ?
2. **Premier périmètre :** valide-t-on `FL` local avant `FC/FV/FM/FS` ?
3. **Rôle des modules SID :** les modules existants publient-ils leurs
   transactions vers LIS par événements, API ou lecture de tables ?
4. **Source de vérité :** quelle application représente le switch SWAM central
   dans le dépôt actuel ?
5. **Journée métier :** calendrier, fuseau, heure de cut-off et gestion des
   jours fériés marocains.
6. **Transport :** CFT réel, SFTP ou dépôt local pour le simulateur ?
7. **Nommage :** convention officielle de nom de fichier et gestion des
   régénérations.
8. **Settlement :** le module produit-il seulement les positions/instructions,
   ou doit-il simuler l’exécution comptable ?
9. **Tarification :** les tables de frais/interchange sont-elles fournies par
   SWAM ou configurées dans le simulateur ?
10. **Change :** source des taux, arrondis et nombre de décimales.
11. **Données sensibles :** politique de stockage/tokenisation du PAN dans les
    fichiers de test.
12. **Périmètre des litiges :** chargeback local complet dès le premier MVP ou
    incrément séparé ?
13. **Rapport LIS :** faut-il reproduire immédiatement l’état papier décrit en
    annexe C ?
14. **Fichiers retour :** quels TC initiés par le membre doivent être supportés
    dans le premier incrément ?

## 12. Critères d’acceptation avant implémentation

La conception est considérée validée lorsque :

- le périmètre du premier incrément est signé ;
- les deux sens d’échange sont confirmés ;
- le calendrier et le cut-off sont définis ;
- la version LIS cible est confirmée ;
- les règles de fichier et de transport sont fixées ;
- le mapping SID vers LIS du premier incrément est approuvé ;
- les formules de frais et de position nette sont approuvées ;
- les scénarios présentation/chargeback/représentation sont approuvés ;
- au moins un fichier LIS switch et un fichier retour membre de référence sont
  disponibles ou définis comme golden files.

## 13. Points d’attention issus de la lecture

- La spécification contient des formulations anciennes, des incohérences
  typographiques et quelques références TC contradictoires. Le code ne devra pas
  deviner silencieusement : chaque ambiguïté sera matérialisée par une décision.
- Le document mélange clearing local, passerelles Visa/Mastercard, services
  carte et reporting. Ils doivent être livrés par incréments sans modifier le
  format global obligatoire.
- Un LIS vide reste un fichier fonctionnel valide et obligatoire.
- Les compteurs des trailers sont des règles métier, pas seulement des
  métadonnées.
- Le settlement ne peut pas être calculé uniquement depuis les montants bruts :
  il doit intégrer frais, interchange, redressements, litiges, signes, monnaies
  et taux.
- La régénération d’un fichier doit préserver l’audit et empêcher une double
  comptabilisation.

## 14. Décisions d'architecture confirmées

Cette section remplace toute interprétation antérieure qui ferait dépendre la
génération d'un LIS de l'intégration préalable du LIS de la contrepartie.

### 14.1 Deux chaînes EOD indépendantes

Le membre et le switch sont deux systèmes autonomes. Ils ne partagent pas leur
base de données et produisent chacun leur LIS outgoing depuis leur propre
journal SID :

```text
swam_acq_transactions
    -> batch EOD membre
    -> clearing_transaction_member
    -> LIS outgoing membre

swam_iss_transactions
    -> batch EOD switch
    -> clearing_transaction_switch
    -> LIS outgoing switch
```

Le batch membre ne consulte jamais les autorisations du switch. Le batch switch
ne consulte jamais les autorisations du membre. Une indisponibilité de la
contrepartie ne doit donc pas empêcher la clôture EOD locale.

### 14.2 Intégration croisée après génération

Après échange, chaque système intègre le LIS produit par l'autre :

```text
LIS outgoing switch -> LIS incoming membre -> consolidation membre
LIS outgoing membre -> LIS incoming switch -> consolidation switch
```

L'intégration est idempotente. Elle conserve la donnée locale et la donnée LIS
reçue séparément, puis crée un lien de rapprochement lorsque les deux visions
correspondent. Une réintégration du même fichier ne doit créer ni transaction,
ni chargeback, ni écriture comptable supplémentaire.

### 14.3 Le LIS reçu fait foi pour le clearing

La présence dans le LIS incoming est le fait générateur du clearing et de la
comptabilisation sur le système destinataire.

- Une transaction LIS rapprochée peut produire une écriture comptable.
- Une transaction présente uniquement dans le LIS reste comptabilisable, sous
  réserve des contrôles structurels et métier, car le LIS fait foi.
- Une autorisation locale absente du LIS est classée douteuse et ne produit pas
  automatiquement d'écriture comptable définitive.
- Une opération douteuse peut être rapprochée, validée, rejetée ou laissée en
  attente par un opérateur.

Les statuts minimaux de rapprochement sont :

```text
MATCHED
LIS_ONLY
AUTH_ONLY_SUSPECT
MATCH_PROPOSED
MANUALLY_MATCHED
MANUALLY_VALIDATED
PENDING_REVIEW
REJECTED
ACCOUNTED
```

Toute décision manuelle conserve l'utilisateur, la date, le motif et les
valeurs avant/après.

### 14.4 Chargebacks membre et switch

Chaque système possède sa propre table de chargebacks. La direction est
toujours exprimée du point de vue du système qui héberge la ligne :

```text
chargeback_member.direction = EMITTED | RECEIVED
chargeback_switch.direction = EMITTED | RECEIVED
```

Cas nominal de transaction totalement inconnue côté membre :

```text
transaction dans LIS incoming switch
    -> absente des autorisations et transactions clearing membre
    -> proposition de chargeback membre EMITTED
    -> validation
    -> prochain LIS outgoing membre
    -> intégration switch
    -> chargeback switch RECEIVED
```

Le flux inverse est strictement symétrique lorsqu'une transaction du LIS
incoming membre est totalement inconnue du switch.

Une autorisation locale simplement absente du LIS ne crée pas automatiquement
un chargeback. Elle reste une opération douteuse jusqu'à décision.

Les chargebacks et leurs événements doivent conserver :

- transaction clearing d'origine ;
- direction `EMITTED` ou `RECEIVED` ;
- TC, cycle, motif et statut ;
- montants et monnaies ;
- fichier LIS source et fichier LIS outgoing ;
- référence fonctionnelle de la présentation ;
- échéance de réponse ;
- historique d'acceptation, représentation, redressement ou clôture.

### 14.5 Préparation du frontend Angular

Les workflows ne seront jamais implémentés uniquement dans Angular. Le backend
expose des transitions métier contrôlées et une liste d'actions autorisées.

Le frontend doit pouvoir présenter :

- transactions clearing et détail local/LIS côte à côte ;
- opérations douteuses ;
- chargebacks émis et reçus ;
- fichiers LIS entrants et sortants ;
- exécutions EOD, erreurs et rejets ;
- actions de rapprochement, validation, rejet, chargeback, acceptation et
  représentation.

Les API seront paginées, filtrables et sécurisées par permissions. Le PAN sera
masqué dans les DTO. Les transitions utiliseront un verrouillage optimiste et
seront auditées.

## 15. Constat technique sur les journaux SID existants

Les tables `swam_acq_transactions` et `swam_iss_transactions` contiennent déjà
les principales données nécessaires au premier clearing local :

- MTI, processing code, PAN, STAN, RRN et code d'autorisation ;
- montants transaction, règlement et facturation ;
- monnaies transaction, règlement et facturation ;
- date/heure locale, date de règlement et date de conversion ;
- MCC, POS data code, TID, MID et nom/localisation commerçant ;
- institutions acquéreuse et de forwarding ;
- réponse SID, statut de cycle, montant clearing et éligibilité clearing.

Le mapper SID marque actuellement comme éligibles les messages financiers
approuvés `1200`, `1220` et `1221`. Les `1100` restent autorisés mais non
présentables. Les `1420` et `1421` mettent à jour le cycle de redressement.

Avant le batch EOD, les compléments techniques suivants sont requis :

1. une identité stable et unique de transaction/cycle ;
2. un marqueur EOD indiquant la date et l'exécution ayant extrait la ligne ;
3. une protection contre deux extractions du même cycle ;
4. une règle explicite achat/retrait basée sur le processing code et le
   contexte terminal ;
5. une référence sécurisée du PAN adaptée au rapprochement et aux recherches ;
6. la conservation de la source SID sans recopier DE52, DE128, pistes ou
   message brut sensible ;
7. des index sur RRN, STAN/date, code d'autorisation et identité fonctionnelle.

## 16. Modèle logique à détailler avant création des tables

Les deux applications utiliseront le même modèle logique, avec des tables
physiquement distinctes :

```text
lis_member_* / lis_switch_*
    business_day
    batch_execution
    clearing_transaction
    lis_file
    lis_record
    reconciliation
    reconciliation_action
    chargeback
    chargeback_event
    accounting_entry
    validation_error
```

Les entités communes de format, énumérations et règles restent dans
`sg-swam-lis-common`. Les entités JPA, repositories, batches et API restent dans
le module propriétaire afin de préserver l'indépendance des deux systèmes.

## 17. Matrice SID vers LIS FL - premier périmètre

Le premier périmètre couvre l'achat `TC05` et le cash/retrait `TC07`, chacun
avec `TCR0` et `TCR1`. La même matrice est utilisée par le membre et le switch,
mais chaque application lit exclusivement son journal SID local.

| Cible LIS | Source SID existante | Règle initiale |
|---|---|---|
| TCR0/F.001 Transaction code | `processingCode`, MCC et contexte terminal | `05` achat, `07` cash/retrait |
| TCR0/F.005 Point de vente | `terminalId` | cadrage numérique sur 10 positions à confirmer |
| TCR0/F.006 Nom commerçant | `merchantNameLocation` | extraire/padder le nom sur 25 |
| TCR0/F.007 Ville | `merchantNameLocation` | extraire/padder la ville sur 13 |
| TCR0/F.008 Pays | `merchantNameLocation` ou référentiel terminal | code pays sur 3 |
| TCR0/F.009 MCC | `merchantCategoryCode` | copie sur 4 |
| TCR0/F.010 Type commerçant | MCC | espace achat, `2` agence, `3` GAB |
| TCR0/F.012 Type terminal | `posDataCode`, MCC | table de correspondance SID/LIS |
| TCR0/F.013 TID | `terminalId` | copie sur 8 |
| TCR0/F.014 Usage | cycle clearing | `1` première présentation |
| TCR0/F.020 Produit | BIN/carte/réseau | valeur locale configurée |
| TCR0/F.021 PAN | `pan` | 19 caractères, jamais exposé en clair par l'API |
| TCR0/F.022 Expiration | `expiryDate` | format MMAA attendu par LIS |
| TCR0/F.023 Authentification | `posDataCode`, données SID | table de correspondance |
| TCR0/F.024 Capture | `posDataCode` | piste, manuel, puce, GAB, e-commerce |
| TCR0/F.025 Compte ATM | `processingCode` | type de compte, `00` hors ATM |
| TCR1/F.004 Date transaction | `localTransactionDt` | conversion vers JJMMAA |
| TCR1/F.005 Code autorisation | `authorizationCode` | copie/padding sur 6 |
| TCR1/F.006 Source autorisation | réponse SID | `2` émetteur, autres valeurs selon scénario |
| TCR1/F.007 Type transaction | `processingCode`, terminal | achat/retrait selon LIS |
| TCR1/F.008 Référence acquéreur | identité clearing stable | génération déterministe sur 23 |
| TCR1/F.009 FID | `forwardingInstitutionId` | normalisation sur 8 |
| TCR1/F.012 Montant source | `clearingAmount` | 12 chiffres, unités mineures |
| TCR1/F.013 RID | membre émetteur/configuration | normalisation sur 8 |
| TCR1/F.016 Montant destination | `billingAmount` ou `clearingAmount` | selon monnaie et conversion |
| TCR1/F.019 Date de valeur | journée métier | JJMMAA |
| TCR1/F.020 Date de traitement | journée métier du LIS | identique à TC90/F.005 |
| TCR1/F.025 Séquence carte | `cardSequenceNumber` | zéro si indisponible |
| TCR1/F.027 Référence retrait/achat | `rrn` ou STAN selon capture | cadrage sur 12 |
| TCR1/F.028 Heure transaction | `localTransactionDt` | HHMMSS |
| TCR1/F.029 Monnaie source | `currency` | ISO 4217 numérique |
| TCR1/F.030 Monnaie destination | `billingCurrency`/`settlementCurrency` | priorité métier à fixer |
| TCR1/F.034 Montant facturation | `billingAmount` | zéro lorsque la règle LIS l'impose |
| TCR1/F.035-036 Taux | futur référentiel de change | zéros si aucune conversion |
| TCR1/F.037 Conversion ID | futur référentiel de change | `0000000` si non applicable |

Les champs de séquence physique, numéro TCR, fillers, compteurs et trailers sont
calculés par le générateur de fichier et ne proviennent jamais du journal SID.

### 17.1 Données insuffisantes ou ambiguës

Le journal SID est suffisant pour construire le noyau d'une présentation, mais
les éléments suivants ne doivent pas être inventés :

- découpage normé du DE43 en nom, ville et pays ;
- identification produit local à partir du BIN ;
- exposant de chaque monnaie ;
- FID/RID SWAM normalisés sur huit positions ;
- règles exactes `processingCode` vers achat, cash advance, retrait et compte ;
- correspondance du `posDataCode` SID vers les indicateurs LIS ;
- référence acquéreur de 23 positions ;
- taux de change, date de taux et identifiant de conversion ;
- règles d'interchange et frais de service commerçant.

Ils seront fournis par des tables de configuration versionnées et non par des
constantes dispersées dans le code.

## 18. Contraintes techniques découvertes

Les modules LIS créés compilent mais ne possèdent pas encore les dépendances
Web, JPA, validation, PostgreSQL et sécurité nécessaires aux batches et API.
Celles-ci devront être ajoutées explicitement à `sg-swam-lis-member` et
`sg-swam-lis-switch`.

Les applications SID utilisent actuellement la même instance PostgreSQL mais
des utilisateurs distincts. Pour respecter l'autonomie fonctionnelle, les
tables clearing seront préfixées par rôle et les droits SQL limiteront chaque
application à ses propres tables ainsi qu'à la lecture de son journal source.

La création des tables doit passer par des migrations versionnées. Le mode
Hibernate restera `validate`; il ne devra pas créer ou modifier le schéma au
démarrage.

## 19. Schéma de données du premier incrément

Les noms ci-dessous utilisent le préfixe `{side}` remplacé physiquement par
`member` ou `switch`.

### 19.1 `{side}_lis_business_day`

Pilote une clôture journalière locale :

```text
id, business_date, status, cutoff_at, opened_at, closed_at,
outgoing_file_id, version, created_at, updated_at
```

Contrainte : une seule journée active par système et unicité de
`business_date`.

### 19.2 `{side}_lis_batch_execution`

Trace chaque exécution EOD, import, rapprochement ou comptabilisation :

```text
id, business_day_id, batch_type, status, started_at, completed_at,
read_count, write_count, skip_count, error_count,
requested_by, correlation_id, error_summary, version
```

`correlation_id` est unique et permet au frontend de suivre un traitement
asynchrone sans le relancer.

### 19.3 `{side}_clearing_transaction`

Vue consolidée de l'opération sur le système :

```text
id, business_day_id,
local_sid_transaction_id, local_source_type,
incoming_lis_file_id, incoming_lis_record_id,
functional_key, transaction_type, clearing_cycle,
pan_fingerprint, masked_pan, rrn, stan, authorization_code,
transaction_at, processing_date, value_date,
processing_code, mcc, pos_data_code, terminal_id, merchant_id,
merchant_name, merchant_city, merchant_country,
acquirer_institution_id, issuer_institution_id,
transaction_amount, transaction_currency,
billing_amount, billing_currency,
settlement_amount, settlement_currency,
source_presence, match_status, accounting_status, dispute_status,
manual_decision, manual_reason,
accounted_at, created_at, updated_at, version
```

Contraintes :

- unicité de la source locale par
  `(local_source_type, local_sid_transaction_id, clearing_cycle)` ;
- unicité d'une ligne LIS importée par
  `(incoming_lis_file_id, incoming_lis_record_id)` ;
- index sur `functional_key`, `rrn`, `stan`, `authorization_code`,
  `pan_fingerprint`, `transaction_at` et `match_status` ;
- aucun PIN, MAC, track ou message ISO brut.

`source_presence` vaut `LOCAL_ONLY`, `LIS_ONLY` ou `BOTH`. `match_status`
décrit le résultat du rapprochement sans écraser cette provenance.

### 19.4 `{side}_lis_file`

Conserve l'identité et le cycle de vie du fichier :

```text
id, business_day_id, direction, file_name, storage_path,
source_member, destination_member, processing_date,
file_sequence, regeneration_status, lis_version,
sha256, byte_size, physical_record_count,
business_transaction_count, total_amount, currency,
validation_status, processing_status,
received_at, generated_at, processed_at,
original_file_id, created_by, created_at, version
```

Contraintes :

- unicité de `sha256` pour les fichiers incoming ;
- unicité métier
  `(direction, source_member, destination_member, processing_date,
  file_sequence, regeneration_status)` ;
- conservation du fichier rejeté et de ses erreurs ;
- `original_file_id` obligatoire pour une régénération.

### 19.5 `{side}_lis_record`

Trace chaque TC logique et ses composants TCR :

```text
id, lis_file_id, clearing_transaction_id, chargeback_id,
subfile_type, transaction_code, usage_code,
logical_sequence, first_physical_sequence, tcr_count,
route_indicator, raw_sha256, parse_status, created_at
```

Le contenu brut complet n'est conservé que dans le fichier original protégé.
La base conserve l'empreinte et les champs métier nécessaires à l'audit.

### 19.6 `{side}_reconciliation`

Conserve le résultat calculé ou décidé :

```text
id, clearing_transaction_id, status, match_method, confidence_score,
local_functional_key, incoming_functional_key,
amount_difference, currency_difference, reference_difference,
proposed_at, decided_at, decided_by, decision_reason, version
```

### 19.7 `{side}_reconciliation_action`

Journal immuable des actions opérateur :

```text
id, reconciliation_id, action_type, previous_status, new_status,
reason, actor, occurred_at, payload_before, payload_after
```

### 19.8 `{side}_chargeback`

Représente un dossier de litige émis ou reçu :

```text
id, clearing_transaction_id, parent_chargeback_id,
direction, status, transaction_code, cycle_number,
reason_code, chargeback_reference,
amount, currency, original_amount, original_currency,
source_lis_file_id, source_lis_record_id, outgoing_lis_file_id,
counterparty_member, due_date, emitted_at, received_at,
created_by, manual_reason, created_at, updated_at, version
```

Contraintes :

- direction obligatoire `EMITTED` ou `RECEIVED` ;
- unicité d'un chargeback reçu par
  `(source_lis_file_id, source_lis_record_id)` ;
- unicité fonctionnelle d'un chargeback émis par transaction, cycle, motif et
  référence ;
- une représentation référence le dossier reçu via `parent_chargeback_id`.

### 19.9 `{side}_chargeback_event`

Historique immuable :

```text
id, chargeback_id, event_type, previous_status, new_status,
lis_file_id, actor, reason, occurred_at, event_payload
```

### 19.10 `{side}_accounting_entry`

Écriture comptable immuable et idempotente :

```text
id, clearing_transaction_id, chargeback_id, business_day_id,
entry_type, account_code, debit_amount, credit_amount, currency,
value_date, accounting_key, status, reversal_of_entry_id,
created_at, posted_at, created_by
```

`accounting_key` est unique. Une correction crée une contre-écriture via
`reversal_of_entry_id`; aucune écriture comptabilisée n'est modifiée.

### 19.11 `{side}_validation_error`

```text
id, lis_file_id, lis_record_id, batch_execution_id,
severity, error_code, field_reference, physical_sequence,
message, rejected_value, created_at
```

Les erreurs bloquantes empêchent la comptabilisation du fichier entier. Les
avertissements restent visibles et peuvent être traités par le frontend.

## 20. Transitions principales exposées au frontend

```text
AUTH_ONLY_SUSPECT
  -> MANUALLY_MATCHED | MANUALLY_VALIDATED | REJECTED | PENDING_REVIEW

LIS_ONLY
  -> MATCHED | ACCOUNTED | CHARGEBACK_DRAFT | PENDING_REVIEW

CHARGEBACK EMITTED
  DRAFT -> APPROVED -> READY_TO_SEND -> SENT

CHARGEBACK RECEIVED
  RECEIVED -> UNDER_REVIEW -> ACCEPTED | REPRESENTATION_DRAFT
```

Chaque commande API exige la version courante de l'objet. Une transition
invalide renvoie une erreur métier stable, exploitable par Angular.

## 21. Scénario d'acceptation E2E final

La livraison finale inclura un script reproductible qui démarre et contrôle les
deux chaînes SWAM. Le scénario nominal retenu est :

1. démarrer/connecter les composants SWAM membre et SWAM switch ;
2. effectuer le sign-on réseau sur l'unique liaison SID permanente, utilisée
   de manière bidirectionnelle sans ouvrir une seconde connexion ;
3. charger la ZMK initiale puis réaliser les échanges de clés nécessaires ;
4. vérifier les KCV et l'état actif des clés des deux côtés ;
5. exécuter cinq achats financiers `1200/1210` initiés par le membre et
   traités par le switch/issuer ;
6. exécuter cinq achats financiers `1200/1210` dans le sens inverse,
   concernant une carte du membre, sur la même liaison ;
7. vérifier les réponses SID, RRN, codes d'autorisation et journaux locaux ;
8. lancer indépendamment la fin de journée membre et la fin de journée switch ;
9. générer le LIS outgoing membre et le LIS outgoing switch ;
10. intégrer le LIS membre côté switch et le LIS switch côté membre ;
11. vérifier le rapprochement et les écritures clearing attendues ;
12. créer, valider et émettre un chargeback côté membre ;
13. intégrer ce chargeback côté switch et le contrôler comme `RECEIVED` ;
14. créer, valider et émettre un chargeback côté switch ;
15. intégrer ce chargeback côté membre et le contrôler comme `RECEIVED` ;
16. générer une représentation en réponse à un chargeback reçu ;
17. intégrer la représentation chez la contrepartie ;
18. contrôler les statuts finaux, compteurs LIS, montants et audit.

Le script échoue immédiatement si une étape fonctionnelle ne produit pas le
résultat attendu. Il conserve un rapport d'exécution contenant les identifiants
de transactions, fichiers LIS, chargebacks, représentations et écritures, sans
exposer les clés en clair, les PIN, les pistes ou les PAN complets.

Les données de test seront déterministes mais isolées par un identifiant
d'exécution afin que le scénario puisse être rejoué sans collision. Les
commandes de chargement de clés utiliseront des variables d'environnement ou
un coffre de test ; aucune clé claire ne sera inscrite dans le dépôt.
### Ambiguïté normative TC 93/95/97/99/81 - champ 022

Dans la section 7.4, le champ 022 est annoncé `N 006` à la position 118,
alors que le champ 023 commence à la position 120. Les positions suivantes,
le filler à la position 234 et la longueur physique obligatoire de 256 octets
sont cohérents uniquement si le champ 022 occupe les positions 118-119
(`N 002`). L'implémentation privilégie donc les positions physiques et encode
ce champ sur deux chiffres. Cette décision est couverte par un test de
positions et devra être confirmée avec le CMI si un erratum LIS est disponible.
### Clé de rapprochement SID/LIS

La clé fonctionnelle utilise membre, RRN, code d'autorisation, horodatage
normalisé, montant et devise. Le STAN (DE11) est conservé comme donnée d'audit,
mais exclu de cette clé car les TCR financiers LIS 4.13 ne le transportent pas.
Une clé contenant le STAN rendrait le rapprochement automatique SID/LIS
structurellement impossible.
