# Reprise - Module cartes et issuing

## État du chantier

- Branche : `codex/adding-issuing-module`.
- Phase : Lot 0 technique et premier incrément de la Phase 1.
- Document d'autorité :
  `E:/Way4-Knowledge-Base/03_Guides/Issuing/CADRAGE_MODULE_CARTES_ET_EMISSION_MONETIQUE.md`,
  version 0.3, lu intégralement avant développement.
- Premier incrément publié au commit `005c95e`
  (`Add card issuing foundation`).

## Décisions utilisateur

1. Le futur module issuing est propriétaire de l'autorisation émetteur.
2. `WayPosServer`, les interfaces SWAM et les interfaces DMAS doivent pouvoir
   appeler le même cœur issuing.
3. Les modules de pré-clearing doivent pouvoir consulter le module pour leurs
   vérifications.
4. Une validation de pré-clearing est en lecture seule : elle ne crée pas une
   nouvelle autorisation et ne débite pas une seconde fois.

## Réalisé

### Architecture et contrats

- Architecture documentée dans
  `documents/design/issuing/ARCHITECTURE.md`.
- Module Maven/Spring Boot `sg-card-issuing` ajouté au réacteur.
- Contrats réseau-neutres partagés ajoutés dans
  `sg-common/.../common/issuing/` :
  - autorisation issuer ;
  - statut de décision ;
  - identifiant de paiement typé ;
  - validation de pré-clearing et verdict.
- Les `toString()` des requêtes sensibles sont expurgés afin de ne pas
  journaliser PAN, PIN block ou données EMV.
- Endpoints initiaux :
  - `POST /api/issuing/v1/authorizations` ;
  - `POST /api/issuing/v1/pre-clearing/validations` ;
  - `GET /api/issuing/v1/capabilities` ;
  - `GET /api/issuing/v1/health`.
- Les en-têtes d'idempotence et de corrélation doivent correspondre au corps.
- Tant que HSM, Core Banking, cartes et règles ne sont pas raccordés, les
  autorisations restent en `UNKNOWN / ISSUER_DEPENDENCIES_NOT_READY`.
  Aucune approbation fictive n'est possible.

### Premier domaine persistant

- Produit carte versionné avec états `DRAFT -> APPROVED -> ACTIVE`.
- Contrat carte avec parcours maker-checker
  `DRAFT -> PENDING_APPROVAL -> ACTIVE`.
- Instrument carte relié à un identifiant de paiement contenant le PAN clair,
  un token opaque unique, le PAN masqué et la date d'expiration, conformément
  à la décision explicite du propriétaire du projet du 2026-07-31.
- Activation d'un instrument interdite si le contrat n'est pas actif.
- Transactional outbox créée pour les événements produit/contrat.
- Création produit et contrat idempotente :
  - même clé et même empreinte : restitution de l'objet ;
  - même clé et payload différent : conflit.
- Maker et checker doivent être différents.
- Les répétitions de transitions déjà appliquées ne publient pas un deuxième
  événement.
- Migration :
  `sql/issuing/V1__create_card_issuing_foundation.sql`.
- API d'administration initiale :
  - création, approbation et activation produit ;
  - création, soumission et approbation contrat.

### Émission virtuelle sécurisée

- Port historique `PanVaultPort` conservé pour le parcours de génération
  externe ; un second parcours d'enregistrement accepte le PAN clair décidé
  par le propriétaire et génère localement un token opaque aléatoire.
- Endpoint :
  `POST /api/admin/issuing/v1/contracts/{id}/cards/virtual`.
- Un contrat et son produit doivent être actifs avant l'appel au coffre.
- L'appel externe au coffre PAN est exécuté sans transaction SQL ouverte.
- La réservation coffre utilise la même clé d'idempotence que l'émission.
- L'instrument, le `payment_identifier` et l'événement `CardRequested` sont
  ensuite persistés atomiquement.
- Un rejeu retourne le même instrument sans rappeler le coffre.
- Même clé d'idempotence avec un autre contrat : conflit.
- La représentation `ProtectedPan` expurge la référence de coffre.
- En l'absence d'adaptateur réel, le port retourne explicitement HTTP 503 ;
  aucune génération locale ou fictive de PAN n'est utilisée.
- Migration append-only :
  `sql/issuing/V2__create_payment_identifier_and_issuance.sql`.

## Sécurité

- Le PAN clair est persisté dans `issuing_payment_identifier.pan_clear` selon
  la décision explicite du propriétaire. Il n'est jamais écrit dans les logs,
  événements outbox ou représentations `toString()`.
- PIN block, cryptogramme et secret HSM ne sont jamais persistés en clair.
- Aucun PIN/EMV/solde réel n'est simulé.
- Les dépendances non raccordées provoquent un refus fermé ou un état
  indisponible explicite.
- Le cœur issuing ne dépend pas de jPOS ; les dialectes restent la
  responsabilité de WayPos, SWAM et DMAS.

## Validation

Commande exécutée :

```text
D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd
  -o -nsu -f pom.xml -pl sg-card-issuing -am test
  -Dmaven.repo.local=D:\MoneyCore\.m2\repository
```

Résultat final du 2026-07-31 :

- `sg-common` : 65 tests, 0 échec, 0 erreur ;
- `sg-card-issuing` : 11 tests, 0 échec, 0 erreur ;
- total : 76 tests sans échec ;
- `BUILD SUCCESS` ;
- `git diff --check` réussi.

## Décisions encore ouvertes

- propriétaire du solde et du ledger entre issuing et Core Banking ;
- produits V1 : débit, crédit, prépayé, débit différé ;
- mono-émetteur ou multi-émetteur ;
- cycle d'expiration des holds ;
- source des compteurs et stratégie de contention ;
- STIP, SAF et retour au nominal ;
- rôle contractuel exact de SWAM et portée DMAS ;
- juridiction et exigences réglementaires ;
- HSM, coffre PAN, profils EMV et hiérarchie de clés ;
- SLA, volumétrie, RPO et RTO.

## Premier travail non terminé

1. Raccorder une implémentation réelle de `PanVaultPort` au coffre choisi et
   exécuter le parcours contre cette dépendance.
2. Ajouter les tables `authorization`,
   `authorization_event`, `authorization_hold`, `limit_counter` et le
   registre d'idempotence financier durable.
3. Définir et raccorder `FundingAuthorizationPort` au propriétaire réel du
   solde ; sans cette décision, aucune approbation financière réelle.
4. Définir et raccorder `CardSecurityPort` au HSM pour PIN, ARQC et ARPC.
5. Remplacer le service fail-closed par le moteur de décision réel seulement
   après les points 2 à 4.
6. Ajouter ensuite les adaptateurs WayPos, SWAM et DMAS, puis le validateur
   de pré-clearing réel.

## Fichiers de la session

- `AGENTS.md`
- `REPRISE_ISSUING.md`
- `pom.xml`
- `documents/design/issuing/ARCHITECTURE.md`
- `sg-common/src/main/java/com/staging/sg/common/issuing/`
- `sg-common/src/test/java/com/staging/sg/common/issuing/`
- `sg-card-issuing/`
- `sql/issuing/V1__create_card_issuing_foundation.sql`
- `sql/issuing/V2__create_payment_identifier_and_issuance.sql`

## Pause du 2026-07-31 - parametrage des interfaces en base

Travail interrompu immediatement a la demande de l'utilisateur, avant test,
commit ou push.

Exigence ajoutee :

- tous les ports/interfaces Issuing doivent etre parametres en base par
  emetteur : ServerPOS, SWAM, DMAS, pre-clearing, HSM, Core Banking, coffre
  PAN et bus d'evenements ;
- la configuration comprend direction, protocole, hote, port, chemin,
  timeouts, profil TLS et parametres non secrets ;
- les secrets ne sont jamais stockes en clair : seule une reference de
  coffre peut etre conservee ;
- une configuration absente ou inactive doit provoquer un echec ferme.

Modifications locales non testees et non commitees :

- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/api/CardManagementController.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/api/CreateIssuingInterfaceRequest.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/api/IssuingInterfaceRepresentation.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/domain/IssuingInterfaceDirection.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/domain/IssuingInterfaceEndpoint.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/domain/IssuingInterfaceProtocol.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/domain/IssuingInterfaceStatus.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/domain/IssuingInterfaceType.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/repository/IssuingInterfaceEndpointRepository.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/service/IssuingEndpointResolver.java`
- `sg-card-issuing/src/main/java/com/staging/sg/card/issuing/service/IssuingInterfaceService.java`

Premier travail a la reprise :

1. relire et verifier ces modifications locales ;
2. ajouter la migration SQL append-only du registre d'interfaces ;
3. ajouter les tests du cycle draft/approval/activation, de l'idempotence,
   du remplacement de version active et du fail-closed ;
4. brancher chaque adaptateur sortant sur `IssuingEndpointResolver` ;
5. reprendre ensuite le journal d'autorisation et le moteur de decision.

Aucun test n'avait ete execute avant la reprise. Les fichiers non suivis hors
Issuing sont restes intacts.

## Reprise du 2026-07-31 - registre d'interfaces valide

- Registre versionne par emetteur et type d'interface ajoute.
- Types couverts : ServerPOS, SWAM, DMAS, pre-clearing, HSM, Core Banking,
  coffre PAN et bus d'evenements.
- Cycle maker-checker `DRAFT -> APPROVED -> ACTIVE`, avec desactivation.
- Une seule version active par `(issuer_id, interface_type)`.
- Hote, port, protocole, chemin, timeouts, TLS et parametres non secrets sont
  stockes en base.
- Les champs de parametres portant un nom de secret sont refuses ; seule une
  `secret_reference` expurgee est admise.
- L'absence de configuration active provoque un echec ferme via
  `IssuingEndpointResolver`.
- Migration append-only :
  `sql/issuing/V3__create_issuing_interface_registry.sql`.
- API d'administration :
  creation, approbation, activation et desactivation sous
  `/api/admin/issuing/v1/interfaces`.

Validation :

- `sg-common` : 65 tests, 0 echec, 0 erreur ;
- `sg-card-issuing` : 17 tests, 0 echec, 0 erreur ;
- total : 82 tests sans echec ;
- `BUILD SUCCESS` le 2026-07-31 a 09:17:52 +01:00.

Premier travail non termine apres ce jalon :

1. brancher les adaptateurs sortants reels sur `IssuingEndpointResolver` ;
2. ajouter le journal durable d'autorisation et son idempotence financiere ;
3. definir les ports HSM/Core Banking sans approbation de repli ;
4. implementer le moteur issuer et le validateur pre-clearing.

### Jalon du 2026-07-31 - journal d'autorisation et ports

- Journal durable `issuing_authorization` avec unicite par transaction et
  idempotence `(issuer, caller, idempotency_key)`.
- Empreinte differente sous la meme cle d'idempotence refusee.
- Evenements de decision append-only dans `issuing_authorization_event`.
- Structures SQL ajoutees pour `issuing_authorization_hold` et
  `issuing_limit_counter`.
- Ports definis : `PaymentIdentifierResolutionPort`,
  `FundingAuthorizationPort` et `CardSecurityPort`.
- Les implementations par defaut Core Banking et HSM retournent
  `UNAVAILABLE`; la resolution coffre echoue explicitement.
- Aucun PAN clair, PIN block, cryptogramme ou secret n'est ajoute au journal.
- Migration append-only :
  `sql/issuing/V4__create_authorization_journal.sql`.

Validation du jalon :

- `sg-common` : 65 tests, 0 echec, 0 erreur ;
- `sg-card-issuing` : 20 tests, 0 echec, 0 erreur ;
- total : 85 tests sans echec ;
- `BUILD SUCCESS` le 2026-07-31 a 09:31:06 +01:00.

Premier travail non termine :

1. implementer l'orchestrateur de decision issuer sur le journal et les
   ports, avec controle carte/contrat/produit ;
2. ne persister une approbation qu'apres confirmation Core Banking reelle ;
3. implementer le validateur pre-clearing en lecture seule ;
4. raccorder les adaptateurs reels a leurs configurations actives en base.

### Jalon du 2026-07-31 - moteur issuer et pre-clearing

- `IssuerDecisionService` remplace le bean fail-closed initial.
- Resolution de l'identifiant par coffre puis controles successifs :
  instrument actif, expiration, contrat actif, produit actif, devise et
  service autorise.
- PIN/EMV delegues exclusivement a `CardSecurityPort`.
- Autorisation et transaction financiere deleguees a
  `FundingAuthorizationPort`.
- Une approbation n'est journalisee qu'apres reponse positive du financement.
- Une dependance indisponible retourne `UNKNOWN`, retryable, sans approbation
  locale ni mutation financiere fictive.
- L'idempotence restitue une decision terminale deja journalisee et refuse
  une meme cle avec une empreinte differente.
- Le traitement EMV reste volontairement indisponible tant que l'ARPC
  protege ne peut pas etre restitue identiquement lors d'un rejeu.
- `PreClearingValidationService` compare en lecture seule identifiant,
  statut, montant approuve, devise et code d'autorisation.
- Le pre-clearing ne contacte ni HSM ni Core Banking et porte toujours
  `financialMutationPerformed=false`.

Validation finale :

- `sg-common` : 65 tests, 0 echec, 0 erreur ;
- `sg-card-issuing` : 23 tests, 0 echec, 0 erreur ;
- total : 88 tests sans echec ;
- `BUILD SUCCESS` le 2026-07-31 a 09:47:59 +01:00.

Blocages externes restants :

1. protocoles, endpoints actifs en base et acces reels du coffre PAN, HSM et
   Core Banking ;
2. format protege/persistable de la reponse EMV ARPC et des issuer scripts ;
3. specifications de mapping des adaptateurs ServerPOS, SWAM et DMAS ;
4. environnement E2E et donnees de recette reelles.

## Processus

Aucun processus Maven lancé par cette session ne reste actif.

### Jalon du 2026-07-31 - PAN/token, adaptateurs et Core Banking

- Décision utilisateur appliquée : conservation du PAN clair dans la base
  propriétaire Issuing, sans journalisation.
- Token opaque `pan_tok_*` généré aléatoirement et relié bijectivement au PAN
  par deux contraintes uniques par émetteur.
- Résolution d'une carte par PAN ou par token vers le même identifiant interne.
- Endpoint d'enregistrement :
  `POST /api/admin/issuing/v1/contracts/{id}/cards`.
- Migration append-only :
  `sql/issuing/V5__add_clear_pan_token_mapping.sql`.
- Client Issuing REST commun piloté par `issuing_interface_endpoint`.
- La route locale ServerPOS `00000`, SWAM Issuer et DMAS Mastercard délèguent
  maintenant leur décision au module `sg-card-issuing`. Une indisponibilité
  ne produit jamais d'approbation locale.
- Adaptateur Core Banking JSON/REST piloté par l'endpoint actif en base,
  avec contrôle strict de corrélation et délais configurés.
- Sandbox Core Banking persistant et idempotent, activable uniquement par
  `CARD_ISSUING_CORE_BANKING_SANDBOX_ENABLED=true`.
- Migration append-only :
  `sql/issuing/V6__create_core_banking_sandbox.sql`.
- Transport HSM payShield TCP/TLS ajouté avec framing `BINARY_2_BE` ou
  `ASCII_4` et taille maximale issus de `parameters_json`. Le contenu des
  commandes et réponses n'est pas journalisé.
- Les codes et champs PIN/ARQC payShield ne sont pas inventés : leur profil
  applicatif reste à charger depuis le guide Core Host Commands correspondant
  au modèle et au firmware HSM retenus.

Validation finale du jalon :

- `sg-common` : 66 tests sans échec ;
- `sg-mc-dmas-member` : 3 tests sans échec ;
- `sg-mc-dmas-mastercard` : 1 test sans échec ;
- `sg-swam-issuer` : 1 test sans échec ;
- `sg-swam-acquirer` : 11 tests sans échec ;
- `sg-mc-sms-acquirer` : 3 tests sans échec ;
- `sg-card-issuing` : 28 tests sans échec ;
- `sg-way-pos-server` : 34 tests sans échec ;
- `sg-way-pos-simulator` : 15 tests sans échec ;
- total : 162 tests sans échec ; `sg-mc-sms-issuer` compile sans test propre.
- Le passage agrégé a atteint le timeout dans le dernier module après
  validation de tous les précédents ; le simulateur a ensuite été relancé
  avec `-am` et a terminé en `BUILD SUCCESS`.

Premier travail non terminé :

1. terminer et valider le profil applicatif HSM PIN/ARQC à partir du guide
   payShield autorisé et des métadonnées de carte (PVV/clé EMV) ;
2. exécuter les migrations V5/V6 sur PostgreSQL puis tester le sandbox REST
   connecté ;
3. compléter les mappings réseau advice/reversal et le rejeu ARPC ;
4. exécuter l'E2E ServerPOS, SWAM et DMAS contre le processus Issuing réel.
