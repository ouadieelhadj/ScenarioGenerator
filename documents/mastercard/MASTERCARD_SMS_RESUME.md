# MASTERCARD_SMS — RÉSUMÉ & INDEX D'INTÉGRATION

> Réf. : *Mastercard Network Processing — Single Message System Guide*, éd. 2 June 2026 (1909 p.)
> Ce document est un **index de navigation** + résumé incrémental. Chaque affirmation
> porte son **numéro de page** (p.XXX) pour approfondir sans tout relire.
> Statut : **v0 — index seul**. Les chantiers seront détaillés step by step.

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

1. **CHANTIER 1 — Socle réseau** : MTI list (p.59) + layout 0800/0810 (p.293-298)
   + DE70 (p.776). Construire le packager minimal + sign-on.
2. **CHANTIER 2 — Financier** : layout 0200/0210 (p.169-197) + DE essentiels
   (DE2,3,4,7,11,12,14,22,24,25,32,33,35,37,38,39,41,42,43,49). Packager complet.
3. **CHANTIER 3 — Sécurité** : DE52/DE64/DE128 + PEK exchange (DE48 SE11 / DE110).
4. **CHANTIER 4 — Reversals & advices** : 0420/0422 (p.238), 0220/0230 (p.197).
5. **CHANTIER 5 — Ligne networks + intégration** issuer/acquéreur dans le socle.

---

## 8. POINTS OUVERTS / À CLARIFIER
- Header / framing MIP (longueur, format) : non couvert par la TOC lue — à trouver
  dans Appendix D (System access) p.1825 ou doc MIP séparée.
- Rôle exact voulu (ATM ? POS débit ? les deux ?) — impacte les DE obligatoires.
- Version character set / encodage (EBCDIC vs ASCII sur le lien MIP) — Appendix C p.1816.
- Algo MAC exact (DE64/DE128) — à extraire p.775 / p.1096.
