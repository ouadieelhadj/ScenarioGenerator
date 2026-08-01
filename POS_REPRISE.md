# Reprise - Way POS

## Etat du chantier

- Phase : jalon 5/6, E2E reel.
- Date de demarrage : 2026-07-29.
- `WayPosServer` : jalon Basic+Extended/local/securite termine et teste.
- `wayPosSimulator` : client jPOS, echange dynamique de cles et scenarios
  financiers/EOD implementes et testes.
- Harnais E2E connecte prepare sous `tests/waypos/`, avec une variante
  PowerShell et une variante Git Bash equivalentes ; son execution reelle
  attend le chargement controle des secrets et vecteurs de recette.
- Choix technique impose : implementation ISO avec jPOS.
- Le chantier DMCS/DMAS reste sauvegarde separement dans `REPRISE_DMCS_DMAS.md`.

## Sources analysees

1. `Ol_POS_ISO_TS_BasicSet (1).pdf`
   - OpenWay POS dialect - Basic Set.
   - Version 01.01-46 du 2023-08-02.
   - 218 pages.
2. `Ol_POS_ISO_TS_ExtendedSet (1).pdf`
   - OpenWay POS dialect - Extended Set.
   - Version 01.01-37 du 2023-02-03.
   - 63 pages.

Emplacement local : `D:/LanaCash/OpenWay/SpecsPos/`.

Copies texte indexees par page, generees le 2026-07-30 pour la recherche,
l'analyse automatisee et la reprise :

- `documents/specifications/waypos/OpenWay_POS_BasicSet_extracted.txt` ;
- `documents/specifications/waypos/OpenWay_POS_ExtendedSet_extracted.txt`.

Ces extractions ne remplacent pas le PDF : les tableaux sensibles sont
egalement rendus en image et controles visuellement avant implementation.

## Perimetre convenu avec l'utilisateur

- Creer un serveur d'acquisition POS nomme `WayPosServer`.
- Creer un simulateur TPE nomme `wayPosSimulator`.
- Enregistrer chaque demande recue dans `pos_authorizations`, avec un
  journal d'evenements et une boite persistante pour les reprises.
- Router en priorite selon une table locale de BIN/IIN.
- Une carte locale est routee vers l'interface interne `00000`.
- L'interface `00000` sera raccordee ulterieurement au Host.
- Les cartes non locales pourront etre routees vers DMAS Member, SWAM
  Member, Mastercard SMS, Visa ou une autre interface.
- Preparer l'integration future des referentiels Visa ARDEF et Mastercard.
- Reutiliser les API REST existantes des modules reseau.
- Utiliser un modele transactionnel JSON normalise et versionne a la
  frontiere REST, sans faire dependre le coeur POS d'un dialecte reseau.
- Garder le chemin d'autorisation synchrone et rapide.
- Utiliser une boite persistante/outbox pour reversals, advices,
  reconciliation, reprises et traitements EOD.

## Resultats confirmes de l'analyse

### 1. Transport POS

- Le TPE est client TCP et `WayPosServer` est serveur TCP.
- Une connexion TCP correspond a une seule session de traitement de
  transaction.
- Chaque message ISO est encadre par une longueur binaire de 2 octets,
  ordre big-endian, suivie du corps ISO 8583.
- La longueur indique uniquement la taille du corps ISO.
- Le serveur doit reconstituer le header et le corps depuis le flux TCP.
- Le timer T1 documente est de 55 secondes. Une trame incomplete a
  l'expiration provoque la fermeture de la connexion.
- Apres rupture, la specification recommande 60 secondes avant reconnexion
  et impose au minimum 10 secondes. Une reconnexion trop rapide peut
  produire le code reponse 96, terminal deja en transaction.
- La valeur du timeout d'attente de reponse cote TPE n'est pas fournie
  explicitement et doit etre parametree/validee.

### 2. Packager OpenWay POS

- Le dialecte utilise uniquement les champs 2 a 64 et un bitmap primaire.
- Le MTI est transmis sur 4 chiffres, par exemple `0100`, `0200`.
- Les champs numeriques sont en BCD compacte, justifies a droite et
  completes par zero, sauf le type LLVARL.
- Les longueurs variables sont elles-memes codees en BCD :
  - LVAR : 1 chiffre dans 1 octet ;
  - LLVAR et LLVARL : 2 chiffres dans 1 octet ;
  - LLLVAR : 3 chiffres dans 2 octets ;
  - LLLLLVAR : 5 chiffres dans 3 octets.
- Les champs `b` sont binaires. Les champs `an`, `anp` et `ans` suivent
  les contraintes de caracteres de la specification.
- Champs structurants :
  - DE2 PAN LLVARL n19 ;
  - DE31 LLLVAR ans999 ;
  - DE47, DE48 et DE59 LLLVAR b999 ;
  - DE52 PIN block binaire fixe de 8 octets ;
  - DE55 donnees ICC BER-TLV, maximum 255 octets ;
  - DE60 LLLVAR ans999 ;
  - DE61 LLLLLVAR b15000 ;
  - DE62 reference securisee ;
  - DE63 LLLVAR ans999 avec sous-champs longueur/tag/valeur ;
  - DE64 MAC binaire fixe de 4 octets.

### 3. Operations du Basic Set

Le premier lot fonctionnel minimum doit couvrir :

- `0100 -> 0110` : autorisation, processing code `00xx00`.
- `0200 -> 0210` : purchase/cash, processing code `00xx00`.
- `0220 -> 0230` : confirmation d'autorisation, DE24 = `202`.
- `0400 -> 0410` : universal reversal.
- `0420 -> 0430` : universal reversal advice.
- `0500 -> 0510` : reconciliation, processing code `920000`.
- `0520 -> 0530` : reconciliation advice, processing code `920000`.
- `0800 -> 0810` : initialisation POS, processing code `930000`.
- `0830` : message de rejet, processing code `970000`.

Le Basic Set contient aussi balance inquiry, mini statement, card
verification, pre-authorisation cash, EMV informational advice, final
advice, cashback, refund, purchase return, cash to card, tips, cash by
code, batch upload advice, DCC inquiry, OTP et instalment inquiry.

### 4. Operations ajoutees par l'Extended Set

- Loyalty Program Request.
- Utility Payment.
- Universal Bill Payment Authorisation et Advice.
- Card Control Request.
- PIN Change et PIN Enrolment.
- Credit, Credit Voucher et P2P Card to Card.
- AFD Completion.
- File Update.
- Keys Change `0800 -> 0810`, processing code `960000`.

La V1 doit implementer les deux jeux : Basic Set et Extended Set. Le Basic
Set reste le socle commun et l'Extended Set est active selon les capacites
du profil TPE, sans reporter ses operations a une version ulterieure.

### 5. MAC POS

Ces regles concernent le protocole POS OpenWay et ne doivent pas etre
confondues avec le calcul M6 SWAM.

- Le MAC est calcule depuis le premier octet du MTI jusqu'au dernier octet
  precedant DE64.
- Le bit 64 du bitmap doit deja etre positionne avant le calcul.
- La valeur de DE64 elle-meme n'entre pas dans les donnees MACees.
- IV = zero.
- Cle : TAK.
- TAK double longueur : ANSI X9.19 / Retail MAC.
- TAK simple longueur : ANSI X9.9.
- Le resultat est tronque a 32 bits et place en DE64, 4 octets binaires.
- Si la requete contient DE64, la reponse doit contenir un MAC valide.
  Ignorer le MAC de reponse est explicitement interdit.
- `MACDATA=BIN` : calcul sur les octets reels, padding binaire `00`.
- `MACDATA=HEX` : chaque demi-octet est developpe en code HEX, avec l'ordre
  de demi-octets particulier defini par OpenWay, puis padding `30`.
- La configuration `MACDATA` est configurable par profil TPE. Les deux
  modes BIN et HEX doivent etre implementes dans la V1.
- Les vecteurs connus de la page 211 devront etre les premiers tests
  cryptographiques automatises avant toute integration HSM.

Vecteurs de reference, trois composantes identiques dont le XOR donne la
cle simple `0123456789ABCDEF`, KCV `D5D44F`, IV nul, texte
`Now is the time for all ` (espace final inclus) :

| Mode | Taille utile | MAC attendu |
|---|---:|---|
| BIN | 24 | `70A30640` |
| HEX | 24 | `D3FCABF4` |
| BIN | 19 | `12359511` |
| HEX | 19 | `D5887762` |
| BIN | 9 | `5770223B` |
| HEX | 9 | `DF105C31` |
| BIN | 2 | `C02D9550` |
| HEX | 2 | `A7D2174C` |

### 6. PIN et EMV

- DE52 est un PIN block opaque de 8 octets. Il ne doit jamais etre
  dechiffre ou journalise en clair hors HSM.
- DE55 est un BER-TLV EMV. Les tags obligatoires varient selon le contexte.
- DE55 est obligatoire pour les transactions originales sur carte a puce.
- DE23 est obligatoire si le tag EMV 5F34 est fourni.
- Une transaction EMV contact complete non differee impose un Final Advice
  apres Purchase/Cash ou Purchase with Cash Back.
- Les traitements ARQC/ARPC, le deuxieme Generate AC et la decision
  reversal sont lies : le serveur doit conserver un etat explicite de
  l'incertitude et du resultat terminal.

### 7. Reprises, repeats et reversals

- Un repeat est une copie exacte de la requete precedente avec le dernier
  chiffre du MTI remplace par `1`, par exemple `0200 -> 0201`.
- Le Host peut repondre a la requete originale avec le bit repeat, par
  exemple `0100 -> 0111`.
- Une reponse repeated signifie que l'operation a deja ete traitee. Elle
  ne doit pas etre recomptee dans les totaux du batch.
- Les strategies de reprise dependent de l'operation :
  - certaines acceptent repeat puis reversal automatique ;
  - certaines acceptent uniquement repeat ;
  - PIN Change/Enrolment utilisent uniquement le reversal automatique ;
  - reconciliation/advice utilisent des repeats automatiques ;
  - un echec de batch upload redemarre toute la reconciliation.
- Les messages cancellation/advice sans reponse sont repetes et bloquent
  les nouvelles transactions jusqu'a une reponse positive.
- Une autorisation financiere incertaine ne doit pas etre renvoyee
  aveuglement par le routeur REST. La machine d'etats POS decide entre
  repeat, reversal advice ou traitement offline selon l'operation et EMV.
- Universal reversal :
  - DE24 `400` : full merchant reversal ;
  - DE24 `401` : partial merchant reversal.
- Universal reversal advice :
  - DE24 `400` : full merchant reversal advice ;
  - DE24 `401` : partial merchant reversal advice ;
  - DE24 `402` : full automatic reversal advice ;
  - RC68 peut signaler un timeout reversal.

### 8. Reconciliation et batch

- L'initialisation POS est obligatoire avant les operations carte et
  fournit le Batch ID.
- Le Batch ID doit etre persiste en memoire non volatile, separement pour
  chaque terminal virtuel.
- Aucun nouveau paiement ne doit etre accepte pendant reconciliation ou
  batch upload.
- En cas d'echec de reconciliation, le batch upload devient obligatoire.
- En cas d'echec de batch upload, le terminal reste bloque, meme apres
  redemarrage, jusqu'a reussite ou intervention administrative.
- Les operations offline doivent etre envoyees avant reconciliation.
- Les reversals automatiques et leurs transactions originales ne sont pas
  inclus dans les totaux, sauf exception EMV offline decrite.

### 9. Validation des reponses

Les champs suivants, lorsqu'ils sont presents dans la requete, doivent
correspondre exactement dans la reponse :

- paire MTI requete/reponse, en tenant compte du bit repeat ;
- DE2 PAN ;
- DE3 processing code ;
- DE4 montant, avec les exceptions partial approval/cashback ;
- DE11 STAN ;
- DE41 terminal ID ;
- DE49 devise.

Une divergence impose d'ignorer la reponse et d'appliquer le traitement
"aucune reponse".

### 10. Echange dynamique de cles

- L'Extended Set definit `0800 -> 0810`, DE3 `960000`.
- DE48 transporte l'etat et les blocs de cles ; DE59 sert de debordement.
- Les informations sont en BER-TLV et couvrent notamment TAK, TPK, TMK,
  TPMK, TAMK, KLK, TDK et TEK.
- Les formats de bloc annonces sont GISKE et ANSI X9.17.
- Le MAC est obligatoire pour Keys Change sauf pour GISKE.
- La V1 implemente en priorite le format ANSI X9.17. GISKE est reporte
  comme extension ulterieure optionnelle.
- Une cle maitre initiale est chargee par un officier de securite.
- Apres reception d'un lot, le TPE confirme le statut de toutes ses cles
  dans la prochaine requete online.
- Des cles peuvent etre poussees dans n'importe quelle reponse.
- Les informations de cles ne doivent pas etre appliquees si la
  transaction se termine par un reversal automatique.
- Le simulateur TPE doit implementer l'echange dynamique complet des cles
  des la V1.

## Architecture retenue

Flux temps reel :

`TPE -> TCP/framing -> WayPosPackager -> validation/MAC -> persistence
RECEIVED -> modele normalise -> BIN/IIN router -> adaptateur REST de
l'interface -> reponse normalisee -> ISO POS -> MAC -> TPE`

Composants :

- `WayPosServer` : serveur jPOS, transport TCP, sessions et orchestration.
- `WayPosPackager` : `ISOBasePackager` jPOS dedie au dialecte OpenWay POS.
- Canal jPOS dedie : framing longueur binaire big-endian sur 2 octets.
- Participants jPOS/Q2 : reception, validation, securite, persistence,
  routage, appel connecteur, construction de reponse et reprise.
- `PosSecurityService` : MAC, PIN opaque, integration HSM et gestion des
  profils de cles.
- `PosTransactionService` : validation, idempotence et machine d'etats.
- `PosRoutingService` : priorites et resolution BIN/IIN.
- `PosConnector` : contrat commun des adaptateurs sortants.
- Connecteurs REST : `Internal00000`, DMAS Member, SWAM Member,
  Mastercard SMS, puis Visa/autres.
- `wayPosSimulator` : TPE parametrable Basic/Extended, BIN/HEX, EMV,
  timeout, repeat, reversal, reconciliation et key change.

Le modele Java interne est l'autorite dans le processus. Le JSON est sa
representation versionnee aux frontieres REST. Il ne remplace ni le
message ISO brut ni l'etat persiste.

### Traitement de la route locale `00000`

La cible `00000` n'est pas un simulateur d'approbation. Elle effectue une
autorisation interne reelle :

1. recherche de la carte locale ;
2. controle de son existence et de son statut actif/non bloque ;
3. controle de la date d'expiration ;
4. controle du produit, de la devise et des usages autorises ;
5. controle du montant, du solde disponible et des plafonds ;
6. verification du PIN par HSM lorsque le PIN est present ;
7. verification EMV/ARQC lorsque les donnees ICC sont presentes ;
8. detection des doublons et application de l'idempotence ;
9. reservation ou debit atomique selon le type d'operation ;
10. generation de la reponse POS et de l'ARPC lorsque requis.

Les reversals liberent/restaurent atomiquement la reservation ou le solde
et restent idempotents. Les refus doivent utiliser des codes metier reels,
notamment carte inconnue, carte expiree, fonds insuffisants, PIN incorrect
ou transaction interdite. Le mapping OpenWay exact des codes sera fixe
dans le contrat fonctionnel.

## Endpoints REST releves dans le depot

Les ports REST sont charges depuis les tables d'interfaces/reseaux.

### DMAS Member - port de reference 8084

Endpoints financiers deja disponibles :

- `POST /api/admin/dmas/auth` avec JSON :
  `type`, `pan`, `amount`, `pin`, `terminalId`, `acceptorId`, `entryMode`,
  `transport`.
- `POST /api/admin/dmas/advice` avec JSON :
  `pan`, `amount`, `processingCode`, `pin`, `terminalId`, `acceptorId`.
- `POST /api/admin/dmas/completion` avec JSON :
  `pan`, `finalAmount`, `processingCode`, `originalStan`, `originalDt`.
- `POST /api/admin/dmas/reversal` avec JSON :
  `pan`, `amount`, `processingCode`, `originalStan`, `originalDt`,
  `advice`.

Les endpoints de session, network management et key exchange existants
restent inchanges.

### SWAM Member/acquirer - port de reference 8094

Endpoints financiers deja disponibles :

- `POST /api/admin/swam/purchase`.
- `POST /api/admin/swam/financial`.
- `POST /api/admin/swam/financial-advice`.
- `POST /api/admin/swam/reversal`.

Ils utilisent actuellement des query parameters et retournent une map
specifique SWAM. Les endpoints network sign-on, echo, sign-off, KEK et HSM
restent inchanges.

Le module `sg-swam-lis-member`, port 8521, concerne le clearing LIS et non
le routage d'autorisation temps reel.

### Mastercard SMS Member/acquirer - port de reference 8095

Le code actuel expose seulement :

- connect, sign-on, echo, sign-off et health ;
- bootstrap/sollicitation/consultation des cles.

Il n'existe pas encore d'endpoint REST financier pour authorization,
financial, advice ou reversal. Les services ISO correspondants devront
etre completes avec jPOS avant raccordement au routeur POS.

### Nouveaux endpoints dedies au routage

Tous les endpoints `/api/admin/...` existants sont conserves sans
modification pour les besoins actuels.

Chaque module cible ajoutera le meme contrat :

- `POST /api/routing/v1/transactions` : traitement synchrone d'une
  transaction normalisee, y compris authorization, financial, advice,
  reversal et operations Extended supportees.
- `GET /api/routing/v1/capabilities` : operations et versions supportees.
- `GET /api/routing/v1/health` : disponibilite exploitable par le routeur.

Le POST transporte notamment :

- `schemaVersion`, `transactionId`, `correlationId` et
  `idempotencyKey` ;
- type d'operation, MTI source, processing code et references originales ;
- terminal, marchand, montant, devise et donnees carte protegees ;
- PIN block opaque, donnees EMV et indicateurs de securite ;
- contexte de repeat/reversal/advice et route demandee.

La reponse est normalisee : statut, code POS, code reseau original,
autorisation, montants autorises, donnees EMV/ARPC, references et
indicateurs de reprise. Les connecteurs restent responsables de la
conversion vers le dialecte DMAS, SWAM ou Mastercard SMS.

Les appels utilisent mTLS/JWT de service, `X-Correlation-ID` et
`Idempotency-Key`. Les endpoints de routage ne sont pas des endpoints
d'administration.

## Persistance proposee

### `pos_authorizations`

- identite interne et identite terminale ;
- terminal/merchant, STAN, RRN, batch ID et MTI ;
- montant, devise, type d'operation et etat ;
- route choisie, interface cible et version de regle ;
- code reponse reseau et code reponse POS ;
- empreinte d'idempotence ;
- PAN masque et empreinte non reversible de recherche ;
- indicateurs EMV/MAC/PIN sans secrets ;
- horodatages et latences ;
- lien vers l'operation originale en cas de repeat/reversal/advice.

### Tables complementaires

- `pos_transaction_events` : journal append-only des transitions.
- `pos_outbox` : messages a remettre, avec politique de reprise par type.
- `pos_terminal_profiles` : Basic/Extended, `MACDATA`, cles, batch et
  capacites.
- `pos_bin_routes` : plages IIN/BIN, priorite, localite, cible, statut et
  dates d'effet.

Etats minimum proposes :

`RECEIVED -> VALIDATED -> ROUTED -> SENT -> APPROVED/DECLINED`

Etats d'incertitude :

`UNKNOWN -> REPEAT_PENDING ou REVERSAL_PENDING -> RECOVERED/REVERSED`

La transaction SQL locale ne doit pas rester ouverte pendant l'appel REST.

## Securite et performance

- TLS, de preference mTLS, entre `WayPosServer` et les modules REST.
- Authentification de service et autorisation par interface.
- Anti-rejeu et idempotence bases sur les references POS et une empreinte
  du message.
- PAN jamais journalise en clair ; PIN et cles jamais persistants en clair.
- DE55 chiffre au repos s'il est conserve.
- Pool HTTP avec connexions persistantes ; aucun polling outbox sur le
  chemin critique d'autorisation.
- Table de routage compilee en memoire et versionnee.
- Les traitements asynchrones ne doivent jamais retarder la reponse POS.
- Objectif initial indicatif hors HSM et reseau externe : moins de 30 ms
  de traitement interne au percentile a definir.

## Decisions utilisateur enregistrees

1. La V1 couvre le Basic Set et l'Extended Set.
2. `MACDATA` BIN/HEX est configurable par profil TPE.
3. Le simulateur gere l'echange dynamique complet des cles.
4. La route `00000` effectue les controles internes reels, notamment
   existence carte, expiration et solde.
5. Les endpoints actuels sont conserves ; de nouveaux endpoints uniformes
   sont ajoutes exclusivement pour le routage.
6. L'implementation ISO est realisee avec jPOS.
7. L'echange dynamique de cles utilise ANSI X9.17 en V1. GISKE pourra etre
   implemente ulterieurement.

## Cles de test disponibles

### Visa CEMEA VIP / VCMS

Source locale :

`C:/Users/Admin/Downloads/CEMEA VIP Test Keys Double Length.pdf`

Le document contient des cles Visa de test double longueur pour
l'Encryption BIN `434179`, notamment :

- ZCMK et ses trois composantes ;
- IWK1/IWK2 ;
- AWK1/AWK2 ;
- CVK et CV2K ;
- PVK1/PVK2 ;
- MDK1/MDK2 ;
- CAVV, CAAV et WSD.

Le PDF est marque confidentiel et interdit sa duplication sans autorisation
ecrite de Visa. Les valeurs ne sont donc pas recopiees dans ce depot. Une
session autorisee doit relire le PDF source local lorsqu'elles sont
necessaires.

### TAMK de test - triple longueur

Usage : Terminal Authentication Master Key de test uniquement.

- Composante 1 :
  `D82E1C5300139702CE6973233E4FE3FEDD975703DCDB3A58`
- Composante 2 :
  `22CABDF3CBF201853698CDB40907B6577A25B9F052066DF4`
- Composante 3 :
  `23555A08DA5D3D3F6D5AC00D2E13B797365802A4987F109E`
- Cle combinee XOR :
  `D9B1FBA811BCABB895AB7E9A195BE23E91EAEC5716A24732`
- Longueur : 24 octets, 3DES triple longueur.
- KCV calcule par chiffrement 3DES-ECB d'un bloc nul, tronque a 3 octets :
  `51C71D`.

### TPMK de test - triple longueur

Usage : Terminal PIN Master Key de test uniquement.

- Composante 1 :
  `651B99ABF7F29BB90F618D55C1AC11F3ABA526A366642D05`
- Composante 2 :
  `9925FDB527E826C2BEC02C7357E4198969073DF749B81350`
- Composante 3 :
  `A9FC4A8930079574B351C538920CCF65A84F5B386EF81335`
- Cle combinee XOR :
  `55C22E97E01D280F02F0641E0444C71F6AED406C41242D60`
- Longueur : 24 octets, 3DES triple longueur.
- KCV calcule par chiffrement 3DES-ECB d'un bloc nul, tronque a 3 octets :
  `95B446`.

Ces valeurs sont reservees a la recette/test. Elles ne doivent jamais etre
chargees en production, journalisees par les applications ou envoyees dans
une reponse REST. Le simulateur accepte une cle maitre de 24 octets ; le
serveur doit conserver sa representation sous le LMK WayPos.

## Points restant a parametrer avant recette reelle

1. Quelle est la source d'administration des TPE, marchands, DE41/DE42,
   capacites, mode MAC et cles : nouvelles tables, API existante ou fichier ?
2. Quelles valeurs `xx` des comptes source/destination de DE3 sont attendues
   pour chaque operation du premier lot ?
3. Quel timeout metier de reponse doit utiliser le TPE/simulateur, en plus
   du T1 transport de 55 secondes ?
4. Quelle priorite exacte appliquer entre BIN local, BIN reseau, marque,
   produit, terminal/marchand et route de secours ?
5. Souhaitez-vous activer des la V1 la reference securisee DE62 afin
    d'eviter de conserver PAN/expiration/RRN pour les operations secondaires ?

## Plan de realisation en six jalons

1. **Specifications, packager, MAC et TLV — termine**
   - extraction Basic/Extended, framing, packager jPOS, MAC BIN/HEX,
     codecs DE48/DE59 et DE63.
2. **WayPosServer Basic+Extended, local et securite — termine**
   - termine : serveur ISO, profils TPE, routage BIN, persistence,
     idempotence/repeat, route `00000`, PIN/PVV, ARQC/ARPC, P2P,
     key change ANSI X9.17, batch upload et reconciliation ;
   - Card Control local reste volontairement bloque RC96 tant que le vrai
     service de generation d'issuer scripts EMV n'est pas raccorde.
3. **Services sortants — termine**
   - parite DMAS Member, SWAM Member et Mastercard SMS pour autorisation,
     financial, advice, reversal, EMV/ARPC et erreurs de reprise.
4. **Simulateur TPE et scenarios — termine**
   - Basic+Extended, MAC BIN/HEX, PIN/EMV, repeat, reversal, advice,
     reconciliation et key change.
5. **E2E reel — en cours**
   - PIN-ARQC-ARPC-repeat/reversal/advice-reconciliation-EOD avec cles et
     cartes provisionnees, sans valeur fictive ;
   - bootstrap initial TAK/TPK sous LMK et validation KCV par HSM termines ;
   - harnais connecte termine ; execution bloquee tant que les variables
     d'environnement de recette et quatre ARQC reels/ATC croissants ne sont
     pas charges.
6. **Validation exhaustive et livraison — a faire**
   - matrices OpenWay/DMAS/SWAM/Mastercard, documentation, migration,
     version Git et push apres validation.

## Historique

- 2026-07-29 : creation du document avant implementation.
- 2026-07-29 : lecture complete des deux specifications, extraction des
  contraintes transport, packager, MAC, reprise, reconciliation, EMV et
  echange dynamique de cles. Architecture et questions preparees ; aucun
  code POS commence.
- 2026-07-29 : validation Basic+Extended en V1, MAC configurable, jPOS,
  echange dynamique complet, controles internes `00000` et conservation
  des endpoints existants. Inventaire des endpoints du depot et definition
  d'une nouvelle API uniforme dediee au routage. Aucun code POS commence.
- 2026-07-29 : ANSI X9.17 retenu comme format de blocs de cles pour la V1 ;
  GISKE reporte comme extension optionnelle.
- 2026-07-29 : debut de l'implementation. Ajout des modules
  `sg-way-pos-server` et `sg-way-pos-simulator`, du packager jPOS OpenWay,
  du canal 2 octets, du MAC BIN/HEX, du serveur ISO, du client TPE, du
  modele REST de routage, des tables POS et du premier traitement interne
  `00000`. Compilation reussie ; les huit vecteurs MAC OpenWay passent.
- 2026-07-29 : arret demande par l'utilisateur, reprise prevue le lendemain.
  Etat sauvegarde : endpoints de routage ajoutes a DMAS, SWAM et Mastercard
  SMS sans supprimer les endpoints actuels ; issuer Mastercard SMS complete
  avec controles carte/expiration/devise/solde et reversal ; initialisation
  TPE, reconciliation, profils terminaux et controle MAC sous LMK ajoutes.
  Derniere commande de tests du validateur ISO interrompue par timeout :
  la reprendre en premier, puis corriger toute erreur avant de poursuivre
  l'echange de cles ANSI X9.17, les operations Extended et l'E2E.
- 2026-07-30 : reprise avec GPT-5.6 Avancee. Suite POS commune relancee :
  7 tests reussis, aucun echec (packager, framing logique, validateur,
  catalogue Basic/Extended, huit vecteurs MAC BIN/HEX et BER-TLV).
- 2026-07-30 : verification textuelle et visuelle des pages 9 a 12 de
  l'Extended Set. Codec key change complete avec groupes FF01-FF0F,
  statuts 0-3, actions charger/remplacer/revoquer, DF40=1, DF41 limite a
  127 octets et repartition DE48/DE59 sans couper un TLV.
- 2026-07-30 : persistance `pos_terminal_keys`, provisioning sans cle en
  clair, remise idempotente des blocs ANSI X9.17 fournis par HSM et
  activation TAK/TPK uniquement apres acquittement positif du TPE.
  Compilation de `sg-way-pos-server` reussie.
- 2026-07-30 : extraction complete des deux specifications en TXT avec
  separateurs de pages sous `documents/specifications/waypos/`, tout en
  maintenant le PDF comme source d'autorite.
- 2026-07-30 : simulateur key change ajoute. Il MACe la demande avec la
  TAK courante, verifie la reponse avant toute importation, dechiffre le
  bloc ANSI X9.17 sous la cle maitre configuree, controle le KCV, active
  la nouvelle TAK puis confirme les statuts dans un second 0800/960000.
  Encodage BINARY/HEX_ASCII et chiffrement ECB/CBC_ZERO_IV configurables.
  Test cryptographique d'import/KCV/bascule TAK reussi.
- 2026-07-30 : traduction PIN sortante ajoutee. `WayPosServer` traduit
  ISO-0 de la TPK terminal vers la PEK de l'interface dans
  `JposHsmService`, sans rendre le PIN clair au code applicatif. Les PEK
  d'interface sont conservees sous le LMK WayPos dans
  `pos_interface_keys`. DMAS, SWAM et Mastercard SMS acceptent le DE52
  uniquement si le contrat REST atteste le domaine de cle destination.
  Compilation reussie des cinq modules concernes ; E2E avec cles reelles
  encore a executer.
- 2026-07-30 : securite locale `00000` completee au niveau unitaire.
  Verification Visa PVV directement dans le HSM avec TPK et deux moities
  PVK sous LMK ; test LMK temporaire reussi pour PIN valide et PVV faux.
  Profil EMV carte ajoute (MDK sous LMK, PSN, ARC), validation ARQC,
  controle anti-rejeu ATC et generation ARPC dans DE55 tag 91. Test
  ARQC-ATC-ARPC reussi. Les cles temporaires ne sont utilisees que par les
  tests ; aucune valeur fictive n'est activee en configuration de recette.
- 2026-07-30 : P2P local et Credit Extended rendus atomiques avec
  verrouillage ordonne des deux cartes. La cible est extraite de DE63
  table `60`; controles carte, expiration, devise, PIN et EMV appliques.
- 2026-07-30 : catalogue d'operations Basic/Extended aligne sur MTI, DE3
  et DE24. Correction des MTI de reponse advice/reversal advice
  (`0220 -> 0230`, `0420 -> 0430`). Les repeats `0201/0221` reutilisent
  desormais la meme cle d'idempotence que la requete originale.
- 2026-07-30 : batch ID porte a six chiffres et machine d'etats
  `OPEN/BATCH_UPLOAD_REQUIRED/MANUAL_REQUIRED`. Reconciliation 0500,
  batch upload 0320 et reconciliation advice 0520 implementes avec les
  tables DE63 `PC`, `28` et `BI`, controle des compteurs/montants et
  blocage des nouvelles transactions en cas d'ecart.
- 2026-07-30 : endpoints d'administration ajoutes pour provisionner les
  profils TPE et les plages BIN/IIN sans exposer les cles :
  `/api/admin/waypos/v1/terminals` et
  `/api/admin/waypos/v1/bin-routes`.
- 2026-07-30 : validation unitaire du jalon intermediaire reussie :
  10 tests communs et 6 tests serveur cibles sans echec, incluant le cycle
  `0500 echec -> 0320 upload -> 0520 succes`, le P2P, ARQC/ARPC,
  reconciliation, repeats et MTI de reponse.
- 2026-07-30 : File Update Extended `0302 -> 0312` distingue du Batch
  Upload `0320 -> 0330`. DE3 n'est plus impose au File Update, DE47 est
  obligatoire, le contenu binaire est persiste de facon idempotente dans
  `pos_file_updates` et aucune donnee arbitraire n'est approuvee sans
  reception effective. Tests validateur et service reussis.
- 2026-07-30 : PIN Change/Enrolment local non-EMV ajoute. Le nouveau PIN
  reste un bloc chiffre DE31/tag `11`; le HSM calcule le nouveau PVV sous
  TPK/PVK sans exposer le PIN clair. Le changement controle d'abord
  l'ancien DE52 et le scheme PC=0 ; l'enrolement exige un PVKI
  pre-provisionne. Les demandes EMV sont volontairement refusees RC96 tant
  que la generation des issuer scripts n'est pas raccordee. Tests HSM et
  route `00000` reussis.
- 2026-07-30 : pause demandee par l'utilisateur. Reprise exacte : rester
  au jalon 2/6 et traiter ensuite Card Control Extended, puis verifier les
  autres operations metier Extended avant de passer au jalon 3 (services
  sortants). Ne pas oublier d'ajouter les migrations
  `pos_file_updates`/PIN au plan de recette base de donnees.
- 2026-07-30 : reprise du jalon 2. Validation Card Control DE63/tag `62`,
  activation Extended par profil TPE et refus RC57 pour un profil Basic.
  La route locale ne modifie pas fictivement la puce : RC96 explicite
  `ISSUER_SCRIPT_REQUIRED` jusqu'au raccordement des vraies cles/scripts.
- 2026-07-30 : AFD Completion DE24=102 corrige en capture reelle avec
  liberation du reliquat ou debit du complement. Tip Purchase Completion
  DE24=202/tag `38` debite uniquement le pourboire et ajuste le montant de
  l'original utilise dans les totaux.
- 2026-07-30 : recherche de l'operation originale corrigee pour exclure la
  transaction courante deja journalisee. Les reversals/advices restent
  autorises lorsque le batch bloque les nouvelles operations financieres.
  Un reversal automatique DE24=402 marque l'original `AUTO_REVERSED` et
  l'exclut des compteurs.
- 2026-07-30 : l'ARPC est persiste avec l'autorisation et restitue lors
  d'un repeat idempotent. File Update 0302 reste Extended, tandis que
  Batch Upload 0320 est correctement reconnu comme Basic.
- 2026-07-30 : jalon 2 termine. Suite globale Maven reussie :
  29 tests `sg-common`, 14 tests `WayPosServer` et 1 test simulateur,
  soit 44 tests sans echec. Passage au jalon 3/6, services sortants.
- 2026-07-30 : jalon 3 avance. DMAS Member, SWAM Member et Mastercard SMS
  propagent maintenant le MTI financier, DE3 exact, contexte terminal,
  PIN traduit, DE55 et le DE55/ARPC de reponse. Compilation des quatre
  adaptateurs et de WayPosServer reussie.
- 2026-07-30 : outbox de reprise implementee avec payload AES-256-GCM,
  cle obligatoire `WAY_POS_OUTBOX_KEY_HEX` fournie par l'environnement.
  Timeout debit/credit/hold genere un reversal automatique DE24=402 ;
  timeout advice/reversal/capture genere un repeat. Backoff borne,
  dix tentatives puis statut MANUAL. Sept tests ciblant chiffrement,
  anti-alteration, construction du reversal et livraison reussis.
- 2026-07-30 : pause immediate demandee pour basculer vers le projet
  Way4 Knowledge Base, une autre session prenant DMCS/DMAS. Point exact
  de reprise WayPos : terminer le test de mise a jour de l'original apres
  livraison outbox, lancer les tests des adaptateurs DMAS/SWAM/MC SMS,
  puis seulement cloturer le jalon 3/6.
- 2026-07-30 : reprise de la session ScenarioGenerator et audit en lecture
  seule demandes par l'utilisateur. Situation confirmee : WayPos reste au
  jalon 3/6 ; les jalons 1 et 2 sont termines, les jalons 4 a 6 restent a
  faire. DMCS/DMAS reste sauvegarde separement dans
  `REPRISE_DMCS_DMAS.md`, avec le tag publie
  `ValidationDmcsDmasFirst`.
- 2026-07-30 : verification des taches Codex. Une seule session est active
  sur `D:\MoneyCore\ScenarioGenerator` : la presente session. La tache
  Way4 documentaire du meme projet est inactive ; aucune autre session
  active concurrente n'a ete detectee.
- 2026-07-30 : tentative de reprise des tests cibles
  `PosRecoveryServiceTest,PosJournalServiceTest`. Premiere execution
  bloquee par le droit d'ecriture du cache
  `D:\MoneyCore\.m2\repository`. Nouvelle execution autorisee hors bac a
  sable, mais elle a depasse le delai de 120 secondes sans restituer de
  resultat final ; le processus Maven Java orphelin a ete arrete par son
  PID exact. Ce timeout n'est pas un echec fonctionnel du code.
- Point exact de reprise apres cet enregistrement :
  1. ajouter au test du dispatcher la verification de l'appel
     `PosJournalService.applyLinkedOutcome(request, response)` ;
  2. relancer les tests cibles avec un timeout adapte et un cache Maven
     accessible ;
  3. lancer les tests des adaptateurs DMAS Member, SWAM Member et
     Mastercard SMS ;
  4. corriger les ecarts eventuels, puis cloturer le jalon 3/6.
- 2026-07-30 : jalon 3 termine. Le dispatcher outbox appelle desormais
  explicitement `PosJournalService.applyLinkedOutcome` apres une livraison
  correlee approuvee ; le test couvre cette mise a jour avant de marquer
  le message `DELIVERED`.
- 2026-07-30 : tests de routage ajoutes pour DMAS Member, SWAM Member et
  Mastercard SMS. Ils couvrent la propagation des donnees financieres,
  du PIN deja traduit, de DE55/ARPC, des references de reversal et les
  controles de domaine de cle PIN.
- 2026-07-30 : non-regression agregee reussie sur huit modules :
  29 tests `sg-common`, 3 tests DMAS Member, 11 tests SWAM Member,
  3 tests Mastercard SMS, 17 tests WayPosServer et 1 test simulateur,
  soit 64 tests sans echec. `sg-mc-sms-issuer` compile avec succes mais
  ne possede pas encore de test propre. Passage au jalon 4/6.
- 2026-07-30 : fichier racine `AGENTS.md` cree. Toute nouvelle session
  ScenarioGenerator doit lire le document de reprise specialise, verifier
  Git et les sessions concurrentes, proteger le worktree et enregistrer
  le point exact avant toute interruption.
- 2026-07-30 : jalon 4 termine. Le simulateur construit desormais les
  messages financiers, reversals, systeme, File Update, batch upload et
  reconciliation selon le MTI. Il valide le MTI et les champs de
  correlation de la reponse et conserve la derniere requete par terminal
  pour produire un repeat exact avec meme STAN/RRN et MAC recalcule.
- 2026-07-30 : endpoint
  `POST /api/simulator/v1/scenarios/{scenario}` ajoute pour :
  `PURCHASE`, `PURCHASE_REPEAT`, `PURCHASE_REVERSAL`,
  `AUTHORIZATION_FINAL_ADVICE`, `PURCHASE_EOD`, `EXTENDED_P2P` et
  `EXTENDED_CARD_CONTROL`. L'endpoint key change existant est conserve.
- 2026-07-30 : le simulateur couvre PIN/EMV, MAC BIN/HEX via le profil,
  key change ANSI X9.17, repeat, reversal, advice, reconciliation/EOD,
  P2P et Card Control. Les cles temporaires obtenues pour le calcul MAC
  sont effacees apres usage.
- 2026-07-30 : non-regression agregee du jalon 4 reussie :
  29 tests `sg-common`, 3 DMAS Member, 11 SWAM Member,
  3 Mastercard SMS, 17 WayPosServer et 14 simulateur, soit 77 tests
  sans echec. Passage au jalon 5/6 ; aucun E2E connecte reel n'est encore
  revendique par ce resultat unitaire.
- 2026-07-30 : jalon 5 avance. Endpoint de bootstrap initial ajoute :
  `POST /api/admin/waypos/v1/terminals/{terminalId}/working-keys`.
  Il accepte uniquement TAK/TPK deja chiffrees sous le LMK WayPos. Le HSM
  recalcule et compare le KCV avant activation ; aucune cle claire n'entre
  dans l'API ni dans la base.
- 2026-07-30 : harnais connecte ajoute dans
  `tests/waypos/Invoke-WayPosE2E.ps1`, avec son `README.md`. Il provisionne
  le terminal, les cles sous LMK, la carte et la route locale, execute le
  key change ANSI X9.17 avec confirmation, puis couvre PIN, ARQC, ARPC,
  EOD, repeat, reversal et final advice. Quatre DE55/ARQC distincts sont
  obligatoires pour respecter l'anti-rejeu ATC.
- 2026-07-30 : validation syntaxique PowerShell reussie. L'execution de
  precontrole s'arrete correctement avant toute transaction car les
  variables de recette ne sont pas chargees dans cette session : base/LMK,
  outbox, identite TPE, TAK/TPK/PVK/MDK, carte/PIN, quatre vecteurs EMV et
  prochain bloc TAK ANSI X9.17. Aucune valeur n'a ete inventee ou affichee.
- 2026-07-30 : non-regression agregee finale reussie :
  63 tests `sg-common`, 3 DMAS Member, 11 SWAM Member,
  3 Mastercard SMS, 34 WayPosServer et 14 simulateur, soit 128 tests sans
  echec. `sg-mc-sms-issuer` compile avec succes sans test propre.
- 2026-07-30 : validation locale complementaire des cles fournies. Le
  simulateur reconstruit les TAMK/TPMK triple longueur depuis leurs trois
  composantes, retrouve respectivement les KCV `51C71D` et `95B446`, puis
  importe avec succes une TAK sous TAMK et une TPK sous TPMK via ANSI X9.17.
  La reference passe a 129 tests sans echec.
- 2026-07-30 : variante Git Bash du harnais ajoutee dans
  `tests/waypos/Invoke-WayPosE2E.sh`. Elle reprend les memes variables,
  controles, appels REST, criteres MAC/key change et scenarios que la
  variante PowerShell. `bash -n` reussi et precontrole lance sous Git Bash :
  arret fail-closed avant transaction sur variables de recette absentes.
- 2026-07-30 : guide operateur RECETTE ajoute dans
  `tests/waypos/INSTRUCTIONS_RECETTE_DU_REPO_A_LA_FINALISATION.md`.
  Il couvre recuperation Git, prerequis, 129 tests, build, demarrage,
  choix PowerShell/Git Bash, E2E, diagnostic et verdict final assaini.
- 2026-07-30 : catalogue Basic/Extended precise et couvert par une matrice
  parametree de 34 operations. Cashback, utility payment, cash by code,
  refund, credit, credit voucher, purchase return et cash to card ne sont
  plus confondus sous des noms generiques.
- 2026-07-30 : provisioning PVK/MDK durci. Comme TAK/TPK, les cles sous LMK
  sont desormais validees par recalcul HSM du KCV avant persistence.
- 2026-07-30 : matrice de validation creee dans
  `documents/design/waypos/VALIDATION_MATRIX.md`. Elle distingue le code
  complet, les implementations partielles, les refus volontaires et les
  preuves E2E manquantes, sans masquer les ecarts DMAS/DMC.
- 2026-07-30 : route locale durcie en mode fail-closed. Mini statement,
  cash by code, loyalty, utility/bill payment, inquiries specialises et
  processing codes inconnus retournent RC96 avant tout acces carte ou
  mouvement de fonds tant que leurs services reels ne sont pas raccordes.
- Point exact de reprise du jalon 5 :
  1. charger les variables reelles enumerees dans
     `tests/waypos/Invoke-WayPosE2E.ps1` ou
     `tests/waypos/Invoke-WayPosE2E.sh`, sans les commiter ;
  2. demarrer PostgreSQL, WayPosServer et wayPosSimulator avec le meme
     environnement ;
  3. executer le harnais soit avec `powershell.exe -NoProfile
     -ExecutionPolicy Bypass -File
     .\tests\waypos\Invoke-WayPosE2E.ps1`, soit depuis Git Bash avec
     `./tests/waypos/Invoke-WayPosE2E.sh` ;
  4. conserver les resultats et corriger tout ecart reel avant de cloturer
     le jalon 5 ;
  5. si les secrets ne sont toujours pas disponibles, poursuivre les
     ecarts P1 de `documents/design/waypos/VALIDATION_MATRIX.md`, en
     commencant par les operations Basic specialisees.

## Raccordement Issuing du 2026-07-31

- La route locale `00000` de `WayPosServer` délègue maintenant la décision au
  module `sg-card-issuing` via le client REST commun.
- L'hôte, le port, le chemin et les timeouts sont lus depuis
  `issuing_interface_endpoint` pour le type `SERVER_POS`.
- Une indisponibilité Issuing retourne un résultat `UNKNOWN/91` retryable ;
  aucune approbation locale de repli n'est produite.
- Les 34 tests `sg-way-pos-server` passaient avec ce raccordement lors de la
  validation finale. Les 15 tests du simulateur passent aussi ; l'E2E
  connecté du jalon 5 reste non exécuté.

## E2E interne Issuing du 2026-07-31

- `sg-issuing-internal-e2e` exerce la route ServerPOS `00000` jusqu'au moteur
  de decision Issuing par un appel JSON/REST HTTP reel sur boucle locale.
- Un repeat identique restitue le meme code d'autorisation avec
  `replayed=true` et sans second debit.
- Le test EMV reutilise le moteur M/Chip 4 CVN10 deja present dans
  `sg-common` et le flux local WayPos : ARQC verifie, ATC memorise, repeat ATC
  refuse et ARPC restitue en tag 91.
- Cette preuve est interne et sans PostgreSQL/processus separes ; elle ne
  remplace pas le harnais connecte du jalon 5 avec les secrets de recette.
- Non-regression dependante : 132 tests, 0 echec, `BUILD SUCCESS` le
  2026-07-31 a 11:36:35 +01:00.
- Le code suivi contient CVN10, pas CVN01. Le premier travail restant cote
  Issuing/WayPos est le raccordement protege de l'ARPC CVN10 au coeur Issuing,
  puis l'E2E connecte PostgreSQL/processus/payShield.

## Reprise du harnais Issuing/WayPos du 2026-07-31

- L'ancien lancement connecte avait demarre Issuing et WayPosServer sur
  PostgreSQL, puis WayPosServer a subi un arret JVM par manque de memoire
  native. Cet arret n'est pas un echec fonctionnel ISO, et aucune transaction
  E2E n'avait encore ete executee.
- Le lanceur Git Bash `tests/issuing/start-connected-services.sh` borne
  maintenant les JVM et active un profil HTTP local `connected-e2e` lie a
  `127.0.0.1`.
- Les 66 tests communs, 29 tests Issuing et 34 tests WayPosServer passent, soit
  129 tests sans echec. Le packaging des quatre services connectes reussit.
- Les secrets et la carte de recette ne sont pas charges dans la nouvelle
  session. Les scripts s'arretent correctement avant lancement/transaction ;
  aucune valeur fictive n'a ete introduite.
- Premier travail restant : recharger les variables reelles, demarrer les
  services, provisionner les trois emetteurs, puis ajouter et executer le pilote
  transactionnel ServerPOS/SWAM/DMAS. L'E2E connecte reste non execute.
- La configuration minimale est maintenant centralisee dans le modele
  `tests/issuing/connected-e2e.env.example`. Sa copie locale sous
  `runtime/issuing-connected-e2e/connected-e2e.env` est ignoree par Git et
  chargee automatiquement par le harnais.
- La copie locale est renseignee pour l'environnement de test avec accord
  explicite de l'utilisateur. Issuing, WayPosServer, SWAM Issuer et DMAS
  Mastercard demarrent, et le provisionnement BANK1/300853/002202 reussit.
  Les services restent actifs. Le pilote de transactions financieres connecte
  reste le premier travail non termine ; l'E2E financier n'est pas encore
  revendique.

## Test connecte wayPosSimulator vers WayPosServer du 2026-07-31

- Non-regression relancee sur `sg-common`, `sg-way-pos-server` et
  `sg-way-pos-simulator` : 117 tests, 0 echec, `BUILD SUCCESS` a
  16:03:10 +01:00.
- Packaging hors ligne des deux applications : `BUILD SUCCESS` a
  16:06:11 +01:00.
- Defaut de demarrage reproduit sur le JAR simulateur sans option :
  l'auto-configuration DataSource/Liquibase est active alors que le simulateur
  ne possede ni datasource ni driver. Le test a continue avec les exclusions
  Spring passees uniquement en ligne de commande ; le code n'a pas ete modifie.
- WayPosServer REST/ISO et wayPosSimulator REST ont demarre respectivement sur
  `8530/8531` et `8532`.
- Premier echange avant provisionnement : `0800/930000 -> 0810`, RC03
  `Unknown or disabled terminal`, comportement fail-closed attendu.
- Terminal local de test `TERM0001` / marchand `MERCHANT0000001` provisionne
  avec MAC non requis pour ne pas inventer de cle cryptographique absente.
- Echange connecte valide : `0800/930000 -> 0810`, RC00, batch `000001`,
  correlation STAN preservee, temps observe 43 ms.
- Cette preuve valide TCP, framing jPOS, packager, correlation et traitement
  systeme Simulator/ServerPOS. Elle ne valide pas encore le flux financier,
  le MAC, le PIN, ARQC/ARPC ni le key change reel.
- Les deux processus Java ont ete arretes apres le test ; ports `8530`, `8531`
  et `8532` liberes. Aucun commit ni push effectue.
- Premier travail non termine : corriger l'auto-configuration du simulateur,
  ajouter un test de demarrage du contexte sans datasource, puis executer le
  flux financier connecte avec les vecteurs cryptographiques de recette.

## Scripts Git Bash ServerPOS / simulateur / RKI du 2026-07-31

- Ajout d'un parcours operateur separe dans `tests/waypos/gitbash/` :
  demarrage ServerPOS, demarrage simulateur, premier `0800/960000` d'echange
  RKI, second `0800/960000` de sign/confirmation, puis arret controle.
- Le sign demande par l'utilisateur est traite comme la confirmation des
  statuts de cles importees, et non comme un `0800/930000` de sign-on.
- Le simulateur accepte maintenant simultanement une TAMK et une TPMK de
  recette avec leurs identifiants distincts. La compatibilite avec l'ancienne
  configuration a cle maitre unique est conservee.
- L'auto-configuration DataSource/JPA/Liquibase inutile au simulateur est
  exclue dans le code. Un profil REST local `connected-e2e`, lie a
  `127.0.0.1`, permet les appels curl du harnais.
- Nouvel endpoint `POST /api/simulator/v1/key-change/confirm` : il refuse la
  confirmation sans statuts importes et envoie exactement une demande de
  confirmation MACee lorsqu'un premier echange a reussi.
- La configuration locale ignoree par Git contient maintenant la LMK WayPos,
  l'identite TPE et les TAMK/TPMK de test fournies. `WAY_POS_TAK_HEX` reste
  vide : la TAK initiale coherente avec le profil ServerPOS n'a pas ete
  fournie et n'a pas ete remplacee par une valeur fictive.
- Validation syntaxique des six scripts : `bash -n`, succes.
- Test reel du script ServerPOS : REST `8530` et ISO `8531` demarres, base
  connectee et LMK WayPos correcte chargee. Le script d'arret a ensuite
  libere `8530`, `8531` et `8532`.
- Test fail-closed du demarrage simulateur : arret attendu avant connexion sur
  `Variable requise absente: WAY_POS_TAK_HEX`.
- Non-regression agregee : 136 tests, 0 echec, `BUILD SUCCESS` a
  16:43:33 +01:00. Packaging ServerPOS/simulateur : `BUILD SUCCESS` a
  16:35:00 +01:00.
- Aucun processus reste actif. Aucun commit ni push effectue.
- Premier travail non termine : renseigner la TAK initiale et provisionner le
  meme profil terminal cote ServerPOS, puis executer les deux scripts RKI et
  obtenir RC00 avec MAC valide pour l'echange et sa confirmation.

## Validation RKI Git Bash connectee du 2026-07-31

- Ajout de `bootstrap-rki-test.sh` et d'un endpoint strictement limite au
  profil `connected-e2e`, active uniquement par
  `WAY_POS_LOCAL_TEST_BOOTSTRAP_ENABLED=true`.
- Ajout de `tail-waypos-logs.sh` pour suivre simultanement les consoles
  ServerPOS et simulateur avec `tail -f`.
- Le bootstrap ne recoit et ne retourne aucune cle par REST. Il charge la TAK
  initiale de test sous la LMK WayPos, genere une TAK sous TAMK et une TPK sous
  TPMK dans la frontiere HSM, puis place les deux cles en attente.
- Configuration locale completee avec la TAK de test autorisee. Les TAMK et
  TPMK fournies restent les cles maitres de transport, jamais de production.
- Tests communs + ServerPOS executes avant packaging : 102 tests, 0 echec.
  Le packaging standard a ete bloque par l'ancien Java utilisateur gardant le
  JAR ouvert ; un JAR Spring Boot classe `bootstrap` a ete produit avec succes
  sans supprimer le processus par contournement.
- Chaine connectee executee avec succes : bootstrap accepte, ServerPOS
  `8530/8531`, simulateur `8532`, premier `0800/960000` RC00 et MAC valide,
  TAK/TPK importees, second `0800/960000` de sign/confirmation RC00 et MAC
  valide.
- Verification base apres le sign : TAK `ACTIVE`, TPK `ACTIVE`, profil
  `TERM0001` avec TAK/TPK presentes sous LMK.
- Bootstrap rendu repetable : chaque execution genere de nouveaux identifiants
  de lot TAK/TPK et conserve l'historique des cles precedemment confirmees.
- Apres cette adaptation, non-regression commune + ServerPOS relancee :
  102 tests, 0 echec ; packaging classe `bootstrap2` reussi a 17:09:34.
- Les ports `8530`, `8531` et `8532` sont libres apres l'arret. Deux processus
  Java lances depuis des sessions Git Bash distinctes restent visibles sans
  ecoute reseau car Windows refuse leur terminaison inter-session ; fermer les
  terminaux parents ou executer `stop-waypos.sh` depuis la session proprietaire.
- Premier travail non termine : aucun pour le parcours RKI local. Le prochain
  jalon reste l'E2E financier PIN/ARQC/ARPC avec les vecteurs de recette.

## Sauvegarde explicite de session du 2026-07-31 a 17:15

Etat runtime au moment de la demande de sauvegarde utilisateur :

- ServerPOS deja actif : REST `8530` retourne HTTP 200 ; ISO utilise `8531`.
- POS Simulator deja actif : REST `8532` retourne HTTP 200.
- Ne pas relancer `start-serverpos.sh`, `bootstrap-rki-test.sh` ou
  `start-pos-simulator.sh` dans cet etat.
- Le profil `TERM0001` a ete realigne sur la derniere TAK confirmee afin de
  rester coherent avec la TAK presente en memoire du simulateur actif.
- Le lot local TAK/TPK existant a ete remis explicitement en attente pour la
  reprise : base verifiee avec `TAK:PENDING` et `TPK:PENDING`.
- Premier travail non termine exact : executer, depuis Git Bash :

  ```bash
  bash ./tests/waypos/gitbash/rki-exchange.sh
  bash ./tests/waypos/gitbash/rki-sign-confirm.sh
  ```

- Pour suivre les deux services depuis un second Git Bash :

  ```bash
  bash ./tests/waypos/gitbash/tail-waypos-logs.sh
  ```

- La version ServerPOS actuellement en memoire precede l'amelioration du
  bootstrap repetable. La version corrigee est deja compilee dans
  `sg-way-pos-server/target/sg-way-pos-server-1.0.0-SNAPSHOT-bootstrap2.jar` ;
  `start-serverpos.sh` la choisira automatiquement au prochain vrai demarrage.
- Au prochain demarrage complet, utiliser l'ordre suivant : stop, start
  ServerPOS, bootstrap, start simulateur, RKI exchange, RKI sign/confirm.
- Les processus Java actifs ont ete crees dans des sessions Git Bash
  distinctes. Windows peut refuser leur arret depuis une autre session ;
  lancer `stop-waypos.sh` depuis le terminal proprietaire ou fermer les
  terminaux parents.
- Derniers tests apres le bootstrap repetable : 68 tests `sg-common` et
  34 tests ServerPOS, soit 102 tests sans echec, `BUILD SUCCESS` a 17:09:10.
  Le parcours connecte complet precedent avait deja obtenu RC00/MAC valide
  pour l'echange et le sign/confirmation.
- Scripts Git Bash presents : `_common.sh`, `start-serverpos.sh`,
  `bootstrap-rki-test.sh`, `start-pos-simulator.sh`, `rki-exchange.sh`,
  `rki-sign-confirm.sh`, `tail-waypos-logs.sh` et `stop-waypos.sh`.
- Aucun commit ni push effectue.

## Verification et sauvegarde Git du 2026-08-02

- Reprise WayPos effectuee apres lecture complete du present document,
  controle de `git status`, de l'historique recent et des taches Codex :
  aucune autre tache agente concurrente n'etait active sur le depot.
- Aucun processus n'ecoutait sur les ports WayPos `8530`, `8531` et `8532`.
- La configuration locale ignoree par Git contient tous les prerequis du
  parcours RKI et le fichier LMK reference existe. Aucune valeur n'a ete
  affichee ni ajoutee au depot.
- Le harnais financier complet reste fail-closed : 34 variables de recette
  reelles sont absentes, dont les cles sous LMK, la carte/PIN et les quatre
  vecteurs EMV ARQC/ATC distincts. Aucune valeur fictive n'a ete creee.
- Les tests ServerPOS et le RKI n'ont pas ete relances : l'utilisateur a
  confirme que cette validation etait deja terminee. La validation syntaxique
  Git Bash des scripts WayPos, Issuing et Way4 a reussi avec `bash -n`.
- Developpements sauvegardes dans les commits locaux suivants :
  - `be85ac3 feat(waypos): add connected RKI workflow` ;
  - `6d8c8f6 test(issuing): add connected environment harness` ;
  - `ae36465 docs(swam): update LIS session resume` ;
  - `6d49a7f docs(way4): add merchant contract loading kit`.
- Le commit 3DS local `c9e6e4c` reste egalement en attente de push.
- Les fichiers de cles LMK/ZMK, sauvegardes PostgreSQL, specifications
  proprietaires, traces, caches et repertoires temporaires ont ete exclus des
  commits.
- Aucun push n'a ete effectue.
- Premier travail WayPos non termine : E2E financier reel
  PIN/ARQC/ARPC-repeat/reversal/advice/reconciliation-EOD, uniquement apres
  fourniture des vrais elements de recette.
