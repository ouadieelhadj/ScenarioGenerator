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
