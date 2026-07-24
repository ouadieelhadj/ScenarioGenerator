# MASTERCARD_SMS — RÉSUMÉ & INDEX D'INTÉGRATION

> Réf. : *Mastercard Network Processing — Single Message System Guide*, éd. 2 June 2026 (1909 p.)
> Ce document est un **index de navigation** + résumé incrémental. Chaque affirmation
> porte son **numéro de page** (p.XXX) pour approfondir sans tout relire.
> Statut : **v1 — Chantier 1 lu**, Chantier 2+ en attente. Dernière MAJ : 2026-07-17.

---

## 0. DÉCISIONS D'ARCHITECTURE (arrêtées avec l'équipe)

- **Une seule ligne `networks`** `MASTERCARD_SMS` portant les DEUX rôles (issuer + acquéreur),
  comme SWAM (option A). Colonnes issuer_* ET acquirer_* renseignées.
- Nouveau **packager jPOS** `MastercardPackager` (ISO 8583:1987, MTI `0xxx`).
- On développe **issuer + acquéreur** sur le même socle multi-réseau existant.
- À définir plus tard : header (type de framing MIP), ports, host.

---

## 1. VUE D'ENSEMBLE (Chapitre 2, p.47)

- Single Message System (SMS) = **autorisation + clearing dans UN seul message**
  (opposé au Dual Message System). p.50
- Participants et bases du traitement. p.51-53
- Produits couverts : Debit Mastercard, Maestro, Cirrus, ATM. (à confirmer p.50)

---

## 2. INDEX PAR CHANTIER

| Chantier                         | Où (chapitre / section)                     | Pages          |
|----------------------------------|---------------------------------------------|----------------|
| **Liste des MTI**                | Ch.3 — List of SMS MTIs                      | p.59           |
| **Familles de messages**         | Ch.3 — Financial 02xx / Reversal 04xx / etc. | p.62-69        |
| **Flux transactionnels**         | Ch.3 — Message flows                         | p.70-159       |
| **Network sign-on/sign-off**     | Ch.3 — Network management requests           | p.150          |
| **PEK key exchange (flux)**      | Ch.3 — PEK-exchange request                  | p.155-159      |
| **Layout général / notations**   | Ch.4 — General message layout info           | p.160-168      |
| **Layout 0200 / 0210**           | Ch.4                                         | p.169-197      |
| **Layout 0220 / 0230**           | Ch.4                                         | p.197-234      |
| **Layout 0420 / 0422 / 0430**    | Ch.4 — Reversal advices                      | p.238-285      |
| **Layout 0620 / 0644 (admin)**   | Ch.4 — Administrative advice                 | p.286-292      |
| **Layout 0800 / 0810 / 0820**    | Ch.4 — Network Management                    | p.293-300      |
| **Dictionnaire des DE (1..128)** | Ch.5 — Data element definitions              | p.301-1096     |
| **Validation / erreurs**         | Ch.6 — Transaction Message Validation        | p.1097-1109    |
| **Services & fonctionnalités**   | Ch.7 — System functionality and services     | p.1110-1227    |
| **Batch / reports / settlement** | Ch.8, Ch.9                                   | p.1228-1660    |
| **DE utilisés / non utilisés SMS**| Appendix A                                  | p.1662-1666    |
| **Glossaire / acronymes**        | Appendix H, I, J                             | p.1858-1908    |

---

## 3. MTI DU SINGLE MESSAGE SYSTEM (Ch.3, p.59) — à détailler

Familles (p.59-69), à compléter à la lecture :
- **02xx** Financial Transaction (0200 requête / 0210 réponse / 0220 advice / 0230 advice rsp). p.62
- **03xx** Issuer File Update (0302 / 0312). p.64
- **04xx** Reversal Advice (0420 acquéreur / 0422 issuer / 0430 / 0432). p.65
- **06xx** Administrative Advice (0620 / 0630 / 0644). p.67
- **08xx** Network Management (0800 / 0810 / 0820). p.69

> [À FAIRE step by step] extraire le tableau exact des MTI p.59-61 (sens, générateur,
> usage) pour construire la liste des messages du packager.

---

## 4. DATA ELEMENTS — ANCRAGE PAGE (Ch.5, p.301)

DE clés pour l'implémentation (page de définition) :

| DE   | Nom                                   | Page  |
|------|---------------------------------------|-------|
| DE1  | Bit Map secondaire                    | p.327 |
| DE2  | PAN                                   | p.329 |
| DE3  | Processing Code                       | p.331 |
| DE4  | Amount, Transaction                   | p.338 |
| DE7  | Transmission Date & Time              | p.345 |
| DE11 | STAN                                  | p.352 |
| DE12 | Time, Local Transaction               | p.355 |
| DE13 | Date, Local Transaction               | p.357 |
| DE14 | Expiration                            | p.358 |
| DE18 | Merchant Type (MCC)                   | p.363 |
| DE22 | POS Entry Mode                        | p.368 |
| DE24 | Network International Identifier (NII) | p.377 |
| DE25 | POS Condition Code                    | p.377 |
| DE32 | Acquiring Institution ID              | p.383 |
| DE33 | Forwarding Institution ID             | p.385 |
| DE35 | Track 2                               | p.387 |
| DE37 | Retrieval Reference Number            | p.394 |
| DE38 | Authorization ID Response             | p.397 |
| DE39 | Response Code                         | p.399 |
| DE41 | Terminal ID                           | p.416 |
| DE42 | Acceptor ID                           | p.417 |
| DE43 | Acceptor Name/Location                | p.419 |
| DE48 | Additional Data: Private Use (subelts)| p.445 |
| DE49 | Currency Code, Transaction            | p.683 |
| DE52 | PIN Data                              | p.688 |
| DE53 | Security Related Control Info         | p.689 |
| DE54 | Additional Amounts                    | p.689 |
| DE55 | ICC / EMV data                        | p.704 |
| DE61 | POS Data                              | p.750 |
| DE63 | Network Data                          | p.768 |
| DE64 | MAC (Message Authentication Code)     | p.775 |
| DE70 | Network Management Information Code    | p.776 |
| DE90 | Original Data Elements                | p.781 |
| DE110| Encryption Data (PIN/Key datasets)    | p.921 |
| DE120| Record Data (files)                   | p.1005|
| DE127| Processor Private Data                | p.1093|
| DE128| MAC                                   | p.1096|

---

## 5. SÉCURITÉ — MAC / PIN / CLÉS (ancres)

- **MAC** : DE64 (p.775) et DE128 (p.1096). Algo/plage : [À EXTRAIRE].
- **PIN** : DE52 (p.688). Format du PIN block : [À EXTRAIRE].
- **PEK (PIN Encryption Key) exchange** :
  - flux customer-generated p.155, system-generated p.158
  - dans 0800 : "Network Management Request/0800: PEK exchange" p.295
  - **DE48 subelement 11** (Key Exchange Data Block, double & triple length) p.469-476
  - **DE110** datasets : Dataset 01 (PIN Encryption) p.924, Dataset 04 (Key Exchange) p.928
  - **DE110 SE9** ANSI X9 TR-31 Key Block (128/192 bits) p.918-920
- **Key Check Value** : DE48 SE11 subfield 5 (p.472), DE110 SE10 (p.920).

> [POINT D'ATTENTION] Mastercard SMS utilise des mécanismes de clés MODERNES
> (TR-31 Key Blocks, DE110 datasets) très différents de SWAM (tags P16/P10 HPS).
> Le chantier "clés" sera à traiter à part.

---

## 6. NETWORK MANAGEMENT 0800/0810 (Ch.4, p.293-300) — socle prioritaire

- 0800 acquéreur/issuer-generated p.293
- 0800 system-generated p.294
- 0800 PEK exchange p.295
- 0810 réponses (p.296-298)
- 0820 advice (p.299-300)
- **DE70** = Network Management Information Code (sign-on/sign-off/echo/key). p.776
- Réponses 0810 : codes DE39 network management p.415

> [CHANTIER 1 recommandé] démarrer par 0800/0810 sign-on + DE70, comme socle réseau,
> avant les messages financiers 0200/0210.

---

## 7. FEUILLE DE ROUTE PROPOSÉE (step by step)

1. **CHANTIER 1 — Socle réseau** ✅ LU (pages 59-69, 160-168, 293-300, 776-778)
   MTI list + layout 0800/0810 + DE70. **EN ATTENTE** : attributs DE du Ch.5 pour coder le packager.
2. **CHANTIER 2 — Financier** : layout 0200/0210 (p.169-197) + DE essentiels
   (DE2,3,4,7,11,12,14,22,24,25,32,33,35,37,38,39,41,42,43,49). Packager complet.
3. **CHANTIER 3 — Sécurité** : DE52/DE64/DE128 + PEK exchange (DE48 SE11 / DE110).
4. **CHANTIER 4 — Reversals & advices** : 0420/0422 (p.238), 0220/0230 (p.197).
5. **CHANTIER 5 — Ligne networks + intégration** issuer/acquéreur dans le socle.

---

---

## 9. CHANTIER 1 — LU ET EXTRAIT (pages 59-69, 160-168, 293-300, 776-778)

### 9.1 MTI du Single Message System (p.59-61)
| Famille | MTI | Description | Générateur |
|---------|-----|-------------|------------|
| 02xx | 0200 | Financial Transaction Request | Acquirer |
| 02xx | 0210 | Financial Transaction Request Response | Issuer / MC Network |
| 02xx | 0220 | Financial Transaction Advice | Acquirer / MC Network |
| 02xx | 0230 | Financial Transaction Advice Response | Issuer / MC Network |
| 03xx | 0302 | Issuer File Update Request | Issuer |
| 03xx | 0312 | Issuer File Update Request Response | MC Network |
| 04xx | 0420 | Acquirer Reversal Advice | Acquirer / MC Network |
| 04xx | 0422 | Issuer Reversal Advice | Issuer / MC Network |
| 04xx | 0430 | Acquirer Reversal Advice Response | Issuer / MC Network |
| 04xx | 0432 | Issuer Reversal Advice Response | Acquirer / MC Network |
| 06xx | 0620 | Administrative Advice | System / Processor |
| 06xx | 0630 | Administrative Advice Response | — |
| 06xx | 0644 | Administrative Advice | MC Network |
| 08xx | 0800 | Network Management Request | Acquirer/Issuer/System |
| 08xx | 0810 | Network Management Request Response | — |
| 08xx | 0820 | Network Management Advice | MC Network |

### 9.2 ENCODAGE — POINT CRITIQUE (p.163)
- **Transmission en EBCDIC** ("display character representation"), PAS ASCII.
  → différence majeure avec SWAM. Le packager jPOS doit utiliser des
  interpréteurs EBCDIC (IFE_*) et non ASCII (IFA_*), OU être paramétrable.
- Numériques (attribut n) : right-justified, leading zeros.
- Autres : left-justified, trailing spaces.
- Sous-champs de longueur (LL/LLL) : numériques EBCDIC, right-justified, leading zeros.
- Alignement sur frontières d'octet.

### 9.3 Notations de représentation (p.162-166)
- a / an / ans / as / b / n / ns / s (Table 33, p.162)
- b-8 = binaire fixe 8 octets. Bitmap = binaire.
- Longueurs : `-digit(s)` fixe ; `...digit(s)` variable ; LLVAR (01-99) ; LLLVAR (001-999) ;
  TAGLL/TAGLLL (PDS IPM).
- Date/heure : MMDDYYhhmmss (Table 37, p.165-166).

### 9.4 Notations de présence (p.167-168)
- **Org/Dst** : M (Mandatory), C (Conditional), O (Optional),
  ME (Mandatory Echo), CE (Conditional Echo), OE (Optional Echo), `·` (not required).
- **Sys (MC Network)** : X (system interaction, insert/overwrite),
  XE (system echo), `·`/P (pass-through).
- Presence requirement = triplet (Org, Sys, Dst) — Table 41, p.168.

### 9.5 DE 70 — Network Management Information Code (p.776-777) ⭐
- Format : **n-3** (3 chiffres numériques), fixe. Obligatoire dans TOUS les 08xx.
- **Valeurs (Table 726) :**
  | Code | Sens |
  |------|------|
  | 060 | Processor-generated SAF session request |
  | **061** | **General sign-on by the processor** |
  | **062** | **General sign-off by the processor** |
  | 065 | Issuer sign-off (begin Stand-In) |
  | 066 | Issuer sign-on (cease Stand-In) |
  | 161 | Encryption key exchange |
  | 162 | Solicitation for encryption key exchange |
  | 163 | Solicitation for key exchange: TR-31 keyblock |
  | 164 | Key exchange confirmation of success |
  | 165 | Key exchange advice of failure |
  | 166 | Load Comm Key |
  | 167 | Load previous Comm Key |
  | **270** | **Echo test** |
  | 363 | End-of-file (EOF) for SAF traffic |
- ATTENTION : sign-on = **061** (pas 001 comme supposé). sign-off = **062**. echo = **270**.
- Usage (Table 725) : 0800 acquirer/issuer-gen → Org=M ; 0810 response → ME.

### 9.6 Layout 0800/0810/0820 (p.293-300)
- Pages lues : la structure exacte des DE présents par message est dans ces pages
  (à ré-extraire en détail au moment de coder le packager 0800).
- 0800 : acquirer/issuer-generated (p.293), system-generated (p.294), PEK exchange (p.295).
- 0810 : réponses (p.296-298). 0820 : advice (p.299-300).
- Key exchange = **DE48 SE11** (double/triple length keys) ou **DE110 datasets** (TR-31),
  PAS les tags HPS P16/P10 de SWAM.

### 9.7 PLAN PACKAGER (à reprendre)
- **Option A retenue** : packager MINIMAL 0800/0810 d'abord (socle réseau),
  puis extension 0200/0210.
- DE nécessaires pour 0800/0810 : DE7, DE11, DE33, DE39, DE70, DE96, DE128
  (+ DE48/DE110 pour PEK exchange, phase 2).
- **AVANT DE CODER, obtenir :**
  1. Encodage réel du lien (EBCDIC confirmé ? ou ASCII ? → packager paramétrable sinon).
  2. Framing / header MIP (préfixe longueur ?) — probablement Appendix D p.1825.
  3. Attributs précis des DE du network mgmt (pages Ch.5) :
     DE7 p.345, DE11 p.352, DE33 p.385, DE39 p.399, DE96 p.792, DE128 p.1096.
- **Premier incrément visé** : sign-on pur 0800 (DE70=061) → 0810, sans clés ni MAC.

---

## 8. POINTS OUVERTS / À CLARIFIER
- **Header / framing MIP** : non couvert par la TOC lue — Appendix D (System access) p.1825 ou doc MIP séparée.
- **Rôle exact** (ATM ? POS débit ? les deux ?) — impacte les DE obligatoires.
- **Encodage EBCDIC confirmé** (p.163) : le lien utilise EBCDIC (≠ ASCII de SWAM).
  → le packager jPOS doit utiliser des interpréteurs EBCDIC (IFE_*) ou être paramétrable.
- **Algo MAC exact** (DE64/DE128) — à extraire p.775 / p.1096.
- **Attributs précis des DE** pour le packager 0800/0810 (à extraire Ch.5) :
  DE7 p.345, DE11 p.352, DE33 p.385, DE39 p.399, DE96 p.792, DE128 p.1096.
- **Prochain incrément** : envoyer les pages d'attributs DE (Ch.5) pour coder le
  `MastercardPackager` minimal (0800/0810, sign-on DE70=061).

---

## SESSION 18 (2026-07-18) — SOCLE RESEAU : MODULES CREES ET COMPILES

### 1. METHODE DE LECTURE DU GUIDE — RESOLUE DEFINITIVEMENT

Le probleme des sessions precedentes (PDF imprimes illisibles, pages
introuvables a cause du decalage numero affiche / numero de feuille) est
regle : le guide entier a ete exporte en texte.

    Fichier : MDS_m_SMS_Guide_en-us-2026-06-02.txt
    Taille  : 3.4 Mo, 148 157 lignes
    Source  : Adobe Reader -> Fichier -> Enregistrer sous -> Texte accessible

Recherche d'une section :
```bash
grep -n "DE 64 (Message" guide.txt        # trouve la ligne
sed -n '59798,59840p' guide.txt           # affiche le bloc
```

Ne plus imprimer de PDF page par page.

### 2. ATTRIBUTS DES DE — CHANTIER 1 COMPLET

| DE | Nom | Representation | Format | Page |
|---|---|---|---|---|
| 7 | Transmission Date and Time | n-10 | fixe | 345 |
| 11 | System Trace Audit Number | n-6 | fixe | 352 |
| 33 | Forwarding Institution ID | n..10 | LLVAR (2 pos) | 385 |
| 39 | Response Code | **an-2** | fixe | 399 |
| 70 | Network Mgmt Info Code | n-3 | fixe | 776 |
| 96 | Message Security Code | n-8 (binary) | fixe | 792 |
| 64 | MAC | — | **NON UTILISE** | 775 |
| 128 | MAC | — | **NON UTILISE** | 1096 |

Egalement non utilises en SMS : DE65, DE66, DE67, DE68.

**Consequence majeure : PAS DE MAC dans le Single Message System.**
Contrairement a SWAM (DE128) ou DMAS. L'authentification du sign-on passe
par le **DE96 (Message Security Code)**, un "password" binaire 8 octets.

Note : DE39 est `an-2` chez Mastercard (SWAM utilisait `n-3`). Les colonnes
`response_code` des tables MC SMS sont donc en `VARCHAR(2)`.

### 3. LAYOUTS 0800 / 0810

**Table 74 — Network Management Request/0800 (acquirer- or issuer-generated)**

| DE | Nom | Org |
|---|---|---|
| — | MTI (0800) | M |
| — | Bit Map Primary | M |
| 1 | Bit Map Secondary | M |
| 7 | Transmission Date and Time | M |
| 11 | System Trace Audit Number | M |
| 33 | Forwarding Institution ID | M |
| 48 | Additional Data | C (si DE70=161, key exchange) |
| 63 | Network Data | O |
| 70 | Network Management Info Code | M |
| 96 | Message Security Code | C |

**Table 77 — Network Management Request Response/0810**

| DE | Nom | Org |
|---|---|---|
| — | MTI (0810) | M |
| 1 | Bit Map Secondary | M |
| 7 | Transmission Date and Time | M (meme valeur que la requete) |
| 11 | STAN | ME |
| 33 | Forwarding Institution ID | ME |
| 39 | Response Code | M |
| 44 | Additional Response Data | C (si DE39=30) |
| 48 | Additional Data | C |
| 63 | Network Data | ME |
| 70 | Network Mgmt Info Code | ME |

Codes DE70 retenus : `061` sign-on, `062` sign-off, `270` echo test,
`161` PEK exchange.

### 4. ARCHITECTURE DES MODULES

D'apres Appendix D (p.1826) : *"Customers connect to the Mastercard Network
through at least two MIP"* — c'est le **membre** qui initie la connexion TCP
vers le MIP, jamais l'inverse.

    sg-mc-sms-acquirer  = LE MEMBRE (nous)
       REST 8095 / ISO 8096 / user PG mc_sms_acquirer_user
       -> se connecte au MIP Mastercard, emet 0800 et 0200

    sg-mc-sms-issuer    = SIMULATEUR MASTERCARD
       REST 8097 / ISO 8098 / user PG mc_sms_issuer_user
       -> ecoute, recoit les 0800/0200, repond 0810/0210

En test local : acquereur (8095) -> issuer (8098).
Vers le MIP reel : acquereur -> host/port Mastercard.

Chaque module a **deux ports** : un REST (admin, envoi manuel de messages)
et un ISO (liaison permanente), comme SWAM.

### 5. FICHIERS CREES

```
sg-common/src/main/java/com/staging/sg/common/iso/
    MastercardSmsPackager.java        (tous les DE ; DE64 et DE128 = null)

sg-mc-sms-acquirer/
    pom.xml
    src/main/resources/application.properties
    src/main/java/com/staging/sg/mc/sms/acquirer/
        SgMcSmsAcquirerApplication.java
        network/McJposClient.java     (sign-on / echo / sign-off, DE7 en UTC)
        api/McNetworkController.java  (POST /api/admin/mc/network/signon|echo|signoff)
        entity/     McSmsKek, McSmsAcqKey, McSmsAcqTransaction
        repository/ les 3 repositories correspondants

sg-mc-sms-issuer/
    pom.xml
    src/main/resources/application.properties
    src/main/java/com/staging/sg/mc/sms/issuer/
        SgMcSmsIssuerApplication.java
        entity/     McSmsCard, McSmsIssTransaction
        repository/ les 2 repositories correspondants
```

Note : le DE7 est en **UTC** dans McJposClient (`ZonedDateTime.now(ZoneOffset.UTC)`),
conformement a la definition du guide. C'est le bug +1h qu'on avait cote SWAM.

### 6. BASE DE DONNEES

Script `V1__create_mc_sms_tables.sql` execute. Tables calquees sur SWAM :

    mc_sms_kek                <- swam_kek
    mc_sms_acq_keys           <- swam_acq_keys
    mc_sms_iss_keys           <- swam_iss_keys
    mc_sms_acq_transactions   <- swam_acq_transactions
                                 + auth_id_response (DE38)
                                 + network_id (DE24)
                                 + retrieval_ref (DE37)
                                 response_code en VARCHAR(2)
    mc_sms_iss_transactions   <- swam_iss_transactions
    mc_sms_cards              <- swam_cards + cvv2 + service_code

Owner : `postgres`. Users applicatifs : `mc_sms_acquirer_user` (mdp
`mc_acq_pass`) et `mc_sms_issuer_user` (mdp `mc_iss_pass`), avec les memes
grants que leurs equivalents SWAM.

Ligne networks inseree (attention : la colonne est `active` boolean, pas
`status`) :
```sql
INSERT INTO networks (code, name, iso_version, header_type, packager_class,
                      issuer_host, issuer_iso_port, acquirer_jpos_port, active)
VALUES ('MASTERCARD_SMS', 'Mastercard Single Message System', '1987', 'MC_SMS',
        'com.staging.sg.common.iso.MastercardSmsPackager',
        'localhost', 7001, 8095, true);
```

### 7. BUILD — PIEGES RENCONTRES

**Coordonnees du POM parent** : c'est `com.staging:scenario-generator`,
**PAS** `com.staging.sg:ScenarioGenerator`. Et il n'y a **pas** de
`<relativePath>`. La dependance `sg-common` est aussi en groupId
`com.staging`. Regle : toujours copier le bloc `<parent>` d'un module
existant qui compile plutot que de le deviner.

Modules ajoutes au pom.xml parent (lignes 30-31) :
```xml
<module>sg-mc-sms-acquirer</module>
<module>sg-mc-sms-issuer</module>
```

Commande de build validee :
```bash
export JAVA_HOME="/f/MoneyCore/jdk-26_windows-x64_bin/jdk-26.0.1"
mvn -pl sg-mc-sms-acquirer,sg-mc-sms-issuer -am clean package -DskipTests -q
```
=> BUILD OK.

### 8. RESTE A FAIRE

| # | Sujet | Detail |
|---|---|---|
| 1 | Serveur ISO issuer | ecoute 8098, recoit 0800, repond 0810 |
| 2 | Test sign-on local | acquereur 8095 -> issuer 8098 |
| 3 | Framing MIP | 2 octets big-endian **suppose** ; la spec reelle est dans le *Secured Data Communications Guide* (non disponible) |
| 4 | Encodage EBCDIC | guide p.163 ; packager actuel en ASCII (IFA_*) ; prevoir une variante IFE_* pour le MIP reel |
| 5 | DE96 | valeur du Message Security Code a obtenir de Mastercard |
| 6 | Transaction 0200/0210 | apres validation du socle reseau |

---

## 24. SESSION 20 — ECHANGE DE CLES MASTERCARD SMS (2026-07-20)

Cette section documente **les deux mecanismes** d'echange de cles du Single
Message System. Le mecanisme **162 est implemente et valide** ; le **163
(TR-31) ne l'est pas**, mais tout ce qu'il faut pour l'implementer est
consigne ici — il ne sera pas necessaire de relire les specifications.

Sources : guide Mastercard SMS du 2 juin 2026 + **traces reelles du
simulateur officiel** (AcquirerSwitchSimulator, format Mastercard Credit
26Q3). En cas de divergence, la trace fait foi.

---

### 24.1 LES SEPT CODES DE70 DE GESTION DE CLES

Table 726 du guide (ligne 59962 du fichier texte) :

| Code | Description |
|---|---|
| 161 | Encryption key exchange — **livraison** de la cle |
| 162 | Solicitation for encryption key exchange — **demande**, mecanisme DE48 |
| 163 | Solicitation for encryption key exchange: **TR-31 keyblock** — demande, mecanisme DE110 |
| 164 | Encryption key exchange confirmation of success |
| 165 | Encryption key exchange advice of failure |
| 166 | Load Comm Key |
| 167 | Load previous Comm Key |

Le code 161 sert de livraison **dans les deux mecanismes** ; c'est le code de
sollicitation (162 ou 163) qui determine le format de transport.

---

### 24.2 MECANISME 162 — DE48 SUBELEMENT 11  [IMPLEMENTE ET VALIDE]

#### Flux

    Membre -> MIP : 0800 DE70=162   sollicitation
    MIP -> Membre : 0810 DE70=162   DE39=00, SANS cle
    MIP -> Membre : 0800 DE70=161   la cle, DE48 SE11        [SPONTANE]
    Membre -> MIP : 0810 DE70=161   accuse (00 ou 96)
    MIP -> Membre : 0820 DE70=161   acquittement : cle utilisable

**Le flux est ASYNCHRONE**, contrairement a SWAM ou la cle arrive dans la
reponse au 1804 (un seul aller-retour). Ici la demande n'obtient qu'un
accuse ; la cle arrive plus tard sur le thread receiver. D'ou la machine a
etats : PENDING -> RECEIVED -> ACTIVE.

Le guide (ligne 36090) : *"Upon receipt of a Network Management Advice/0820
message, processors may begin to use the new working key delivered in the
Network Management Request/0800 message."* Le 0820 est donc l'autorisation
d'usage, pas une simple politesse.

**Observation de la trace** : le MIP envoie le 0820 **meme si le membre a
repondu DE39=96**. Notre simulateur reproduit ce comportement.

#### Structure du DE48

Suite de subelements `ID(2) + Longueur(2) + Valeur` — attention, la longueur
est sur **2 positions**, alors que le DE48 SWAM utilise Tag(3)+Longueur(3).

Subelement 11 (Key Exchange Data Block), deux formats selon la longueur :

| SF | Contenu | an-54 (double) | an-70 (triple) |
|---|---|---|---|
| 1 | Key Class ID | 2 — `PK` (PIN Key) | 2 |
| 2 | Key Index Number | 2 — `00` | 2 |
| 3 | Key Cycle Number | 2 — `00`..`99` | 2 |
| 4 | Cle chiffree sous ZMK | 32 hex | 48 hex |
| 5 | Key Check Value | 16 (4 hex + 12 espaces) | 16 |

Variante **an-38** observee dans le 0820 : SF1-SF3 renseignes, SF4 et SF5 a
blanc (16 espaces chacun).

Exemple reel (trace Mastercard) :

    1154PK0000E02B0E8BD4644E6341182D71F4F3F5B543A1____________
    ^^ ^^ ^^ ^^ ^^      SF4 cle chiffree (32)        SF5 KCV (16)
    |  |  |  |  +-- SF3 cycle
    |  |  |  +----- SF2 index
    |  |  +-------- SF1 classe = PK
    |  +----------- longueur 54
    +-------------- subelement 11

Notre emission, structurellement identique :

    1154PK0000708F1964DBAE610781C6211436254A696E19____________

#### Cryptographie — VERIFIEE CONTRE LA TRACE

    cle chiffree = 3DES-ECB(cle claire) sous ZMK
    KCV          = 3DES-ECB(8 octets nuls) avec la cle claire

Verification faite sur les valeurs de la trace, 4 correspondances sur 4 :

| Element | Valeur | Resultat |
|---|---|---|
| ZMK | `13AED5DA1F32347523C708C11F2608FD` | KCV `2D617C` — **meme ZMK que SWAM** |
| Cle claire | `BC4AEA2F5BB3FD1504624F8623835D5B` | — |
| Chiffree | `E02B0E8BD4644E6341182D71F4F3F5B5` | **MATCH** |
| KCV | `43A1866D253E9365` | **MATCH**, tronque a `43A1` |

**PIEGE** : la trace du simulateur annonce *"Key Check Value Encryption
Algorithm: DES-CBC"*, mais c'est bien du **3DES avec la cle double
longueur**. Le DES simple sur la moitie gauche donne autre chose.

**PIEGE** : Mastercard tronque le KCV a **4 caracteres** dans SF5, la ou SWAM
en conserve 6. La comparaison doit donc porter sur les 4 premiers caracteres.

#### Classes implementees

**sg-common**
- `McSmsDe48.java` — parseur/generateur des subelements. `KeyExchangeBlock`
  decode SF1-SF5, detecte double/triple/acquittement par la longueur.
  Methodes `putKeyExchange()` et `putKeyExchangeAck()`.

**sg-mc-sms-acquirer** (le membre)
- `McSmsKeyExchange.java` — `solicitKeyExchange()` envoie le 162 ;
  `handleKeyDelivery()` importe la cle sous LMK, verifie le KCV sur 4
  caracteres, persiste en statut RECEIVED ; `handleKeyAcknowledgement()`
  passe a ACTIVE et retire l'ancienne cle.
- `McSmsJposClient.java` — route les 0800/161 et les 0820 vers le service.
  **`@Lazy` obligatoire** sur l'injection de `McSmsKeyExchange` : sans lui,
  Spring detecte un cycle (le service prend le client au constructeur).
- `McKeyExchangeController.java` — bootstrap ZMK, sollicitation, consultation.

**sg-mc-sms-issuer** (simulateur Mastercard)
- `McSmsIssKeyExchange.java` — genere une PEK double longueur (parite impaire),
  la chiffre sous ZMK, calcule le KCV, envoie le 0800/161 puis le 0820.
  Crypto en JCE standard, pas de HSM : c'est un outil de test.
- `McSmsJposServer.java` — sur 0800/162 repond 0810 puis declenche la
  livraison asynchrone. Refuse explicitement le 163 avec DE39=96.
- `McIssKeyController.java` — expose la derniere cle livree, pour comparaison.

#### Endpoints

    POST /api/admin/mc/keys/bootstrap-zmk?zmk=<32 ou 48 hex>
    POST /api/admin/mc/keys/solicit
    GET  /api/admin/mc/keys/current
    GET  /api/admin/mc/sim/last-key          (issuer, port 8097)

#### Resultat du test local

    Simulateur : clair     A45449BF80BF1AF72F7AEAF7B03EEFF2
                 chiffree  708F1964DBAE610781C6211436254A69
                 KCV       6E197BAF48A47D84  -> envoie "6E19"

    Membre     : KCV importe 6E197B  (calcule par le HSM apres import)
                 statut ACTIVE, 16 octets

Le HSM a dechiffre la cle et recalcule un KCV identique : les deux cotes
detiennent bien la meme cle. Les 5 etapes se sont enchainees sans erreur.

---

### 24.3 MECANISME 163 — TR-31 KEYBLOCK VIA DE110  [NON IMPLEMENTE]

Tout ce qui suit vient de traces reelles. Suffisant pour implementer sans
relire les specifications.

#### Flux

    Membre -> MIP : 0800 DE70=163   solicitation TR-31 or AES
    MIP -> Membre : 0810 DE70=163   DE39=00
    MIP -> Membre : 0800 DE70=161   keyblock, DE110           [SPONTANE]
    Membre -> MIP : 0810 DE70=161   accuse
    MIP -> Membre : 0820 DE70=161   acquittement

Meme sequence que le 162 ; seul le transport change.

#### Structure du DE110

**ATTENTION — LE GUIDE ET LA PRATIQUE DIVERGENT.**

Le guide (p.921, ligne 70567) decrit un DE110 « Encryption Data » en
**BER-TLV binaire** : Dataset ID (1 octet) + Dataset length (2 octets) +
tags codes ISO 8825. Dataset 04 = Key Exchange, tags 80 (Control), 81
(Key-set Identifier), 83 (Algorithm), 86 (Key Index), 87 (Encrypted Data),
88 (Key Checksum Value).

**La trace montre autre chose** : des subelements ASCII `ID(2)+len(3)+valeur`,
etiquetes « Additional Data-2 » (l'autre usage du DE110, p.890) :

    09 080 B0080P0TB00E000022D08F4891AD6042734A1E432242CE80D6B928DF5A496751D63E5EE08A5D7D90
    10 006 B3F2DE

    total 2+3+80 + 2+3+6 = 96, coherent avec le prefixe LLLVAR "096"

| Subelement | Contenu | Longueur |
|---|---|---|
| 09 | ANSI X9 TR-31 Key Block | 80 |
| 10 | Key Check Value | 6 |

Noter que la longueur est ici sur **3 positions**, contre 2 dans le DE48.

**Recommandation** : implementer le format de la trace (subelements ASCII).
Le BER-TLV du guide est probablement la cible d'une migration future — le
guide dit lui-meme (ligne 68439) *"when ready to migrate to DE 110
Encryption data must first..."*.

#### Le keyblock TR-31 (ANSI X9.143)

    B0080P0TB00E0000 22D08F4891AD6042734A1E432242CE80D6B928DF5A496751D63E5EE08A5D7D90
    ^^^^^^^^^^^^^^^^ header 16 caracteres        payload 64 caracteres

| Position | Valeur | Signification |
|---|---|---|
| 0 | `B` | Version B — TDES key derivation binding |
| 1-4 | `0080` | Longueur totale du keyblock |
| 5-6 | `P0` | Key Usage — **PIN Encryption Key** |
| 7 | `T` | Algorithm — TDES |
| 8 | `B` | Mode of Use — chiffrement et dechiffrement |
| 9-10 | `00` | Key Version Number |
| 11 | `E` | Exportability — exportable |
| 12-13 | `00` | Nombre de blocs optionnels |
| 14-15 | `00` | Reserve |
| 16-79 | 64 hex | 48 = cle chiffree (24 octets) + 16 = MAC (8 octets) |

Le KCV est dans le subelement 10 : `B3F2DE`, soit **3 octets** — contre
4 caracteres (2 octets) dans le DE48. Verifie contre la trace : la cle claire
`F4EF91DF862564EF38B952DA312910D9` donne le KCV `B3F2DECD89772341`, tronque
a `B3F2DE`.

#### Ce qui reste a determiner pour l'implementation

1. **La KBPK** (Key Block Protection Key) qui protege le keyblock. La trace ne
   la montre pas. Le guide n'en parle pas — c'est probablement dans le
   *Security Guide* ou le *Secured Data Communications Guide*, non disponibles.
   Sera injectee hors bande comme la ZMK.
2. **Le deverrouillage du keyblock** : derivation des cles de chiffrement et
   de MAC depuis la KBPK selon X9.143, verification du MAC, dechiffrement.
   **Le HSM jPOS ne sait probablement pas le faire nativement** — c'est le
   point technique le plus lourd de ce chantier.
3. **Le packager** : le DE110 est declare `IFA_LLLCHAR(999)` par la boucle
   generique DE105-127 de `MastercardSmsPackager`. A verifier si le format
   ASCII de la trace passe tel quel.

---

### 24.4 ECARTS ENTRE LE GUIDE ET LA PRATIQUE

Trois divergences constatees. **La trace du simulateur officiel prime.**

| Point | Guide | Trace reelle |
|---|---|---|
| **DE33** | *"ten-digit number of the format 9000xxxxxx"*, min 10 / max 10 (p.385) | `002202` et `022905` — **6 chiffres, un ICA** |
| **DE110** | BER-TLV binaire, Dataset 04, tags 80-88 (p.921) | subelements ASCII `09`/`10`, « Additional Data-2 » |
| **KCV DE48** | 4 caracteres hex + espaces (p.36330) | conforme : `43A1` + 12 espaces |

Le DE33 est le plus genant : notre bouchon `9000000001` respecte le guide
mais pas la pratique. **A verifier avec Mastercard** avant tout test reel.
Notre packager `IFA_LLNUM(10)` accepte les deux longueurs en LLVAR, donc pas
de blocage technique.

---

### 24.5 AUTRES OBSERVATIONS DES TRACES

- **EBCDIC confirme sur le fil.** Toutes les trames du simulateur sont en
  EBCDIC (`F0F8F0F0` = "0800"). Notre packager ASCII ne passera pas contre un
  MIP reel — la variante `IFE_*` reste a faire.
- **DE2 (PAN)** porte l'ID du client : `41232` dans les traces.
- **DE63 (Network Data)** : `MCC0000J1` = Financial Network Code (`MCC`,
  Mastercard mixed BIN Immediate Debit) + Banknet Reference Number (`0000J1`),
  incremente a chaque message.
- **Mastercard echange les PEK toutes les 24 heures** (guide, ligne 7751),
  plus a la demande en cas de probleme.

---

### 24.6 CORRECTIONS DE CETTE SESSION

| Symptome | Cause | Correction |
|---|---|---|
| Cycle de dependances au demarrage | `McSmsKeyExchange` prend le client au constructeur, le client reference le service | `@Lazy` sur l'injection dans `McSmsJposClient` |
| `value too long for type character varying(6)` | KCV complet (16 car.) ecrit dans une colonne VARCHAR(6) | troncature a 6 dans le controller et le service |

SWAM evite le premier probleme parce que `SwamJposClient` fait l'import
lui-meme, sans passer par `SwamKeyExchange`.

---

### 24.7 RESTE A FAIRE SUR L'ECHANGE DE CLES

1. **TR-31 (163)** — voir 24.3. Point dur : le deverrouillage du keyblock.
2. **Codes 164 / 165** — confirmation de succes et avis d'echec ne sont pas
   emis par le membre. A ajouter apres l'import.
3. **Comm Key (166 / 167)** — jamais decrite dans ce guide. Les tags 83 et 87
   du DE110 disent que la cle est chiffree *"under the current communications
   key"*, ce qui suggere que la Comm Key remplace la ZMK dans le mecanisme
   TR-31. A clarifier avec Mastercard.
4. **Renouvellement automatique 24 h** — non gere.
5. **Multi-banques** — `member_group_id` est fixe par propriete
   (`mc.sms.member-group-id`). A revoir dans le chantier multi-banques
   (section 23.6).

---

### 24.8 BASCULE EN EBCDIC  [FAIT]

Le MIP Mastercard parle EBCDIC. Les deux modules ont ete bascules pour que
le test local reproduise fidelement le format du fil.

#### Preuve

Trame 0810 sign-on emise par notre simulateur, relevee dans le dump du
channel :

    F0F8F1F0                    "0810"
    8220000082000000            bitmap 1 : DE7, DE11, DE33, DE39
    0400000000000000            bitmap 2 : DE70
    F0F7F2F0F1F1F0F0F1F1        "0720111011"        DE7
    F0F0F0F0F0F1                "000001"            DE11
    F1F0 F9F0F0F0F0F0F0F0F0F1   "10" + "9000000001" DE33
    F0F0                        "00"                DE39
    F0F6F1                      "061"               DE70

53 octets, coherent avec le prefixe de longueur `0035`.

Comparaison avec la trace du simulateur Mastercard officiel : structure
identique. Les seules differences sont fonctionnelles — leur bitmap
`C220000082000002` porte DE2 et DE63 que nous n'emettons pas encore, et
leur DE33 fait 6 chiffres (ecart guide/pratique, cf. 24.4).

#### Classe

`sg-common/.../iso/MastercardSmsPackagerEbcdic.java`

Couvre TOUS les champs, contrairement a `McPackagerEbcdic` (DMAS) qui se
limite au sign-on. Correspondance de types :

| ASCII | EBCDIC |
|---|---|
| `IFA_NUMERIC` | `IFE_NUMERIC` |
| `IFA_LLNUM` | `IFE_LLNUM` |
| `IFA_LLCHAR` | `IFE_LLCHAR` |
| `IFA_LLLCHAR` | `IFE_LLLCHAR` |
| `IFA_LLLNUM` | `IFE_LLLCHAR` — jPOS 2.1.9 n'a pas d'`IFE_LLLNUM` |
| `IF_CHAR` | `IFE_CHAR` |
| `IFA_BINARY` | `IFB_BINARY` — vrai binaire, pas de l'hex ASCII |
| `IFB_BITMAP` | inchange |

**Les prefixes de longueur passent aussi en EBCDIC** : dans la trace,
`F0F6` = "06" pour le DE33 et `F0F9F6` = "096" pour le DE110. Les `IFE_LL*`
s'en chargent automatiquement.

**Les champs binaires ne changent pas** : bitmaps, DE52 (PIN block) et
DE96 (Message Security Code).

#### Choix du packager

En dur, comme DMAS (`new McPackagerEbcdic()` dans `DmasJposServer` et
`DmasJposClient`). `MastercardSmsPackager` (ASCII) est conserve et coexiste
— il n'est plus reference par les modules mais reste disponible.

Une variante pilotee par `networks.default_field_encoding` serait possible
(la colonne existe deja dans `NetworkRef`, inutilisee) mais n'a pas ete
retenue : le MIP reel est en EBCDIC, autant tester dans ce format.

#### Test de non-regression

Le flux complet a ete rejoue en EBCDIC : sign-on, sollicitation 162,
livraison 161, accuse, acquittement 0820. KCV `EFC3F0` identique des deux
cotes, PEK ACTIVE.
