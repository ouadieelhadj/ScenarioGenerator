# Architecture du module cartes et issuing

## Statut

Premier incrément de conception exécutable, établi à partir du document
`CADRAGE_MODULE_CARTES_ET_EMISSION_MONETIQUE.md` version 0.3.

Ce document ne ferme pas les décisions qui exigent encore un choix métier,
réglementaire, HSM ou Core Banking.

## Décisions données par le propriétaire du projet

### D-01 — Autorisation émetteur

Le module `sg-card-issuing` est propriétaire de la décision d'autorisation
émetteur. Il est le seul composant autorisé à :

- décider une approbation, une approbation partielle ou un refus ;
- créer, incrémenter, capturer, libérer ou expirer un hold ;
- modifier les compteurs de limites issuer ;
- produire le code d'autorisation interne ;
- demander au HSM la vérification PIN/EMV et la génération ARPC ;
- corréler completion, advice, reversal et remboursement à l'opération
  originale.

WayPos, SWAM, DMAS et les futurs connecteurs restent propriétaires de leur
transport, dialecte ISO, MAC réseau, gestion de session et mapping des codes.
Ils ne dupliquent pas la décision issuer.

### Utilisation par le pré-clearing

Les modules de pré-clearing et clearing disposent d'un contrat distinct de
validation. Une validation de pré-clearing :

- résout l'identifiant de paiement à la date présentée ;
- recherche l'autorisation et ses événements ;
- compare montant, devise et références ;
- détecte doublon, présentation tardive et incohérence ;
- retourne un verdict et une liste d'écarts.

Elle ne crée pas une nouvelle autorisation, ne réserve pas de fonds et ne
modifie pas les compteurs. Une comptabilisation de clearing éventuelle sera
une commande séparée, idempotente et auditée.

## Frontières

```text
WayPosServer -----\
SWAM --------------> contrat issuing normalisé ---> sg-card-issuing
DMAS -------------/                                  |       |
                                                      |       +--> HSM
Pré-clearing ------> contrat de validation ----------+       +--> Core Banking
                                                              +--> Outbox
```

Le cœur issuing ne dépend d'aucune classe jPOS ni d'aucun dialecte réseau.
Les contrats partagés résident dans `sg-common`.

## Contrats initiaux

- `POST /api/issuing/v1/authorizations`
- `POST /api/issuing/v1/pre-clearing/validations`
- `GET /api/issuing/v1/capabilities`
- `GET /api/issuing/v1/health`

Chaque commande porte :

- `schemaVersion` ;
- `issuerId` et `callerId` ;
- `transactionId`, `correlationId` et `idempotencyKey` ;
- un identifiant de paiement typé ;
- des références originales lorsque l'opération est secondaire ;
- un montant en unité mineure et une devise ISO 4217 ;
- un contexte terminal, marchand et sécurité sans secret en clair.

Le même triplet `(callerId, operation, idempotencyKey)` avec une empreinte
différente doit être refusé. Un rejeu identique doit restituer la décision
initiale.

## Sécurité

- aucun PIN clair ni clé claire dans le processus applicatif ;
- PIN block opaque et traduit vers le domaine de clé issuer avant contrôle ;
- PAN jamais journalisé ; les `toString()` des contrats sont expurgés ;
- CVV2/CVC2/CAV2 jamais persisté après l'autorisation ;
- PAN persistant uniquement dans un coffre approuvé ou sous forme de
  référence, masque et empreinte non réversible ;
- refus fermé si le HSM, le Core Banking ou une règle obligatoire n'est pas
  raccordé ;
- mTLS et identité de service exigés entre adaptateurs et issuing.

## Ports encore à raccorder

- `FundingAuthorizationPort` : solde, hold, débit, crédit et capture ;
- `CardSecurityPort` : PIN, CVV/iCVV, ARQC, ARPC et issuer scripts ;
- `RiskDecisionPort` : fraude et conformité en temps réel ;
- `PanVaultPort` : génération, résolution et cycle de vie PAN/token ;
- `AccountingPort` : écritures et rapprochement ;
- `EventPublisherPort` : publication depuis transactional outbox.

Une implémentation indisponible ne doit jamais être remplacée par une
approbation de démonstration.

Le premier parcours d'émission virtuelle applique ce principe : le cœur
transmet au coffre uniquement les références issuer/contrat/produit et les
identifiants de corrélation/idempotence. Le coffre renvoie une référence
opaque, un masque et une expiration. L'appel externe est hors transaction SQL,
puis l'instrument, son `payment_identifier` et l'événement outbox sont
persistés atomiquement. Sans adaptateur de coffre réel, le parcours retourne
une indisponibilité explicite.

## Décisions encore ouvertes

- propriétaire du solde et du ledger entre issuing et Core Banking ;
- cartes débit, crédit, prépayées et différées retenues en V1 ;
- mono-émetteur ou multi-émetteur ;
- cycle exact d'expiration des holds ;
- source des compteurs et stratégie de contention ;
- STIP, SAF et retour au nominal ;
- rôle contractuel exact de SWAM et portée de DMAS ;
- juridiction, exigences PCI et règles de conservation ;
- HSM, coffre PAN, profils EMV et hiérarchie de clés ;
- SLA, volumétrie, RPO et RTO.

Le modèle est préparé avec `issuerId` dans tous les contrats afin de ne pas
fermer prématurément l'option multi-émetteur.
