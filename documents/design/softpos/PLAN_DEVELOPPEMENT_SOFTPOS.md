# Plan de développement — FuturPayment SoftPOS

**Date :** 18 août 2026
**Base :** analyse du code `sg-way-pos-server`, `sg-way-pos-simulator`, du
frontend et de `POS_REPRISE.md`.

## 1. Résultat cible

Une application Android multibanque permettant à un commerçant enrôlé
d'accepter un paiement `NFC`, `QR_MPM` ou `QR_CPM` sur un téléphone compatible,
en appelant à la demande la façade SoftPOS du serveur POS. Le serveur POS
transforme ensuite la demande normalisée vers son traitement ISO 8583 existant
ou vers le connecteur QR configuré.

Le résultat ne pourra être qualifié de SoftPOS bancaire de production qu'après
intégration d'un SDK certifié et réussite des validations de sécurité et de
réseau applicables.

## 2. Modules proposés

| Module | Responsabilité |
|---|---|
| `sg-softpos-api-contracts` | Contrats versionnés sans dépendance Android ni donnée carte claire |
| `sg-softpos-backend` | Façade mobile, enrôlement, sessions, attestation, reçus et orchestration |
| `sg-way-pos-server` | Routage et traitement monétique interne existant ; extensions ciblées uniquement |
| `sg-way-pos-simulator` | Réseau POS et scénarios de non-régression existants |
| `sg-softpos-sdk-simulator` | Faux fournisseur strict de laboratoire, jamais activable en production |
| `sg-softpos-android` | Application Kotlin, parcours marchand et adaptateur SDK certifié |
| `sg-frontend` | Sous-module d'administration SoftPOS dans FuturPayment Switch/Membre, pas le parcours NFC commerçant |

`sg-softpos-backend` fait fonctionnellement partie de la solution ServerPOS,
mais reste un processus séparé afin de ne pas exposer le moteur ISO et ses API
d'administration aux appareils mobiles.

## 3. Ordre de réalisation

### Lot 0 — Décisions et contrat de sécurité

- sélectionner le SDK ou établir une liste courte de fournisseurs ;
- fixer banques, pays, marques, PIN, appareils et versions Android ;
- répartir les responsabilités PCI MPoC, HSM, clés et certificats ;
- figer les deux contrats Backend SoftPOS → POServer : `REST_JSON` sous
  HTTPS/mTLS et `ISO8583_PERSISTENT` sur liaison permanente sécurisée ;
- confirmer le dialecte ISO, framing, network management, MAC, clés, timeouts,
  repeat, reversal et règles de reconnexion ;
- figer les parcours achat, annulation, remboursement, timeout et reçu ;
- fixer les réseaux QR, les modes `QR_MPM`/`QR_CPM` et leur responsabilité ;
- définir les données que le SDK a le droit de remettre au backend.

**Porte de validation :** architecture de confiance et matrice de
responsabilités approuvées. Le développement avec simulateur peut avancer si
le fournisseur n'est pas encore choisi, mais aucune hypothèse propriétaire ne
doit entrer dans le contrat métier.

### Lot 1 — Contrats, modèle et migrations

- créer `sg-softpos-api-contracts` ;
- définir les états appareil, activation, session et transaction ;
- définir `acceptanceChannel = NFC | QR_MPM | QR_CPM` dans les contrats et la
  persistance ;
- ajouter les migrations des tables SoftPOS ;
- ajouter la séparation `memberId/merchantId/outletId/terminalId` ;
- enrichir le profil ServerPOS avec
  `terminalType = PHYSICAL_POS | SOFTPOS` ;
- migrer idempotemment tous les profils existants vers `PHYSICAL_POS` sans
  modifier leur TID, MID, batch ou état ;
- créer le lien unique entre transaction SoftPOS et `pos_authorizations` ;
- créer la configuration de route POServer par banque et environnement avec
  un mode primaire `REST_JSON` ou `ISO8583_PERSISTENT` ;
- définir l'idempotence et la consultation après timeout ;
- produire une spécification OpenAPI sans PAN, PIN block ni clés.

**Tests obligatoires :** migrations répétables, conservation des TPE existants,
distinction `PHYSICAL_POS/SOFTPOS`, contraintes multibanques, contrats JSON,
absence de champs interdits et compatibilité de version.

### Lot 2 — Façade backend SoftPOS

- créer `sg-softpos-backend` ;
- OAuth2/OIDC pour l'opérateur et identité de service vers ServerPOS ;
- activation à usage unique et liaison cryptographique de l'appareil ;
- contrôle d'attestation et politique fail-closed configurable ;
- endpoints session, paiement, statut, annulation, remboursement et reçu ;
- endpoints de création d'un QR commerçant et de soumission d'un QR client ;
- validation QR, expiration, anti-rejeu et adaptateur réseau QR ;
- adaptateur interne vers `PosRoutingService` ;
- adaptateur REST/JSON avec mTLS, OAuth2 de service, corrélation et
  `Idempotency-Key` ;
- adaptateur ISO 8583 permanent avec pool par banque, sign-on, echo, reconnexion
  bornée, corrélation STAN/RRN, MAC et gestion d'état ;
- sélection de l'adaptateur par configuration et basculement contrôlé sans
  resoumission aveugle ;
- limites de débit, anti-rejeu, corrélation et audit ;
- configuration distante et version minimale de l'application.

**Porte de validation :** deux banques de test ne peuvent lire, activer ou
rejouer les données l'une de l'autre ; un timeout répété ne produit jamais un
second débit. Les mêmes achats, refus et reversals doivent passer séparément
par `REST_JSON` puis par `ISO8583_PERSISTENT`.

### Lot 3 — Application Android et SDK simulé

- créer l'application Kotlin avec architecture modulaire ;
- connexion opérateur et activation du téléphone ;
- contrôle NFC, compatibilité appareil et intégrité ;
- saisie montant, présentation carte, résultat et reçu ;
- affichage d'un QR commerçant et lecture d'un QR présenté par le client ;
- adaptateur `SoftPosPaymentSdk` indépendant du fournisseur ;
- implémenter un SDK simulé limité au build laboratoire ;
- stockage Android chiffré des seuls jetons autorisés ;
- aucune capture d'écran ni copie presse-papiers sur les écrans sensibles.

**Porte de validation :** scénario complet Android simulé → Backend →
ServerPOS → simulateur/Switch, avec achat approuvé, refus, timeout et reversal.

### Lot 4 — Exploitation, fraude et administration

- raccordement Fraud Monitoring avant la décision finale ;
- politiques par banque pour ALERT, CHALLENGE, HOLD et BLOCK ;
- gestion à distance ACTIVE/SUSPENDED/REVOKED/COMPROMISED ;
- sous-module SoftPOS dans le frontend Membre/Switch : tableau de bord,
  commerçants, PDV, MID/TID, appareils, opérateurs, transactions, reçus,
  alertes, routes REST/ISO et actions autorisées ;
- filtrage serveur obligatoire par `memberId` et rôles ; le filtrage visuel du
  frontend ne constitue jamais une protection suffisante ;
- tableaux de bord appareils, transactions, versions et échecs d'intégrité ;
- supervision, métriques, alertes, audit immuable et purge ;
- procédures de rotation, révocation, incident et reprise ;
- campagne de charge et de résilience sur deux machines distinctes.

**Porte de validation :** fraude indisponible, ServerPOS indisponible, reprise
réseau, révocation immédiate et montée en charge ont chacun un comportement
documenté, testé et sans approbation de secours fictive.

### Lot 5 — SDK réel et homologation

- intégrer le SDK certifié dans l'adaptateur prévu ;
- raccorder l'environnement HSM/certificats requis par le fournisseur ;
- exécuter achats contactless, CDCVM et PIN selon les capacités retenues ;
- couvrir Visa, Mastercard et les réseaux/pays du périmètre ;
- exécuter sécurité mobile, compatibilité appareils et L3 acquéreur ;
- livrer SBOM, signatures, procédures de mise à jour et dossier de preuves.

**Porte finale :** aucune mise en production avant validation formelle du
fournisseur, de l'acquéreur, des marques et des exigences PCI/EMV applicables.

## 4. API fonctionnelle minimale

```text
POST /api/softpos/v1/activations/consume
POST /api/softpos/v1/sessions
POST /api/softpos/v1/integrity/verdicts
GET  /api/softpos/v1/configuration
POST /api/softpos/v1/payments
GET  /api/softpos/v1/payments/{clientTransactionId}
POST /api/softpos/v1/payments/{clientTransactionId}/cancel
POST /api/softpos/v1/payments/{clientTransactionId}/refunds
GET  /api/softpos/v1/payments/{clientTransactionId}/receipt
POST /api/softpos/v1/qr/merchant-presented
POST /api/softpos/v1/qr/consumer-presented
GET  /api/admin/softpos/v1/poserver-routes
PUT  /api/admin/softpos/v1/poserver-routes/{memberId}/{environment}
```

La demande de paiement transporte le montant, la devise, une clé
d'idempotence, le contexte signé de l'appareil et un objet opaque produit par
le SDK. Elle ne transporte jamais un PAN complet, un PIN clair, une clé ou un
cryptogramme destiné à être journalisé.

## 5. Scénarios de validation de bout en bout

1. activation valide et consommation unique du code ;
2. refus d'un appareil non conforme, révoqué ou avec application altérée ;
3. achat contactless approuvé et reçu masqué ;
4. achat refusé par l'émetteur ;
5. double clic et rejeu avec une seule transaction financière ;
6. timeout avant/après envoi, consultation du statut puis reversal adapté ;
7. annulation et remboursement avec habilitation ;
8. décision Fraud ALERT, CHALLENGE, HOLD et BLOCK ;
9. isolement complet entre deux banques et deux commerçants ;
10. rotation/révocation et version mobile devenue obligatoire ;
11. perte réseau, reprise contrôlée et absence de données sensibles en log ;
12. campagne du simulateur POS réutilisée comme non-régression du Switch.
13. génération, expiration et paiement d'un QR `QR_MPM` sans double débit ;
14. lecture, validation et paiement d'un QR `QR_CPM` sans conserver le payload
    sensible dans les journaux ;
15. preuve que la conformité MPoC est attribuée au canal NFC et n'est pas
    déclarée automatiquement pour les flux QR.
16. même transaction fonctionnelle exécutée sur une route `REST_JSON`, puis
    sur une route de test indépendante `ISO8583_PERSISTENT` ;
17. perte de la liaison ISO, reconnexion/sign-on et reprise sans double débit ;
18. timeout REST avec état inconnu, consultation et interdiction d'un renvoi
    automatique par ISO sans résolution préalable.
19. connexion de trois utilisateurs membres et preuve que chacun ne voit et ne
    commande que les objets SoftPOS de sa propre banque.

## 6. Ce qui peut démarrer sans SDK réel

- contrats API et migrations ;
- backend d'enrôlement et de sécurité ;
- séparation multibanque ;
- adaptateur ServerPOS ;
- application Android hors kernel ;
- SDK de laboratoire ;
- scénarios, reçus, supervision et fraude.

## 7. Ce qui exige le SDK et les partenaires

- lecture NFC réelle et kernel contactless ;
- PIN sur téléphone et protections associées ;
- format cryptographique réel de la transaction ;
- gestion réelle des clés du composant SoftPOS ;
- listes d'appareils et versions supportées ;
- certification PCI MPoC/EMV et campagnes L3 ;
- autorisation de mise en production.

## 8. Stratégie Git et protection du chantier actuel

Le dépôt est actuellement sur la branche
`codex/fraud-monitoring-platform-gateway` avec de nombreux changements Fraud
non commités. Le développement SoftPOS ne doit pas commencer dans cet état.
Après validation du présent plan :

1. sauvegarder/valider le chantier Fraud existant ;
2. créer une branche dédiée, proposée : `codex/softpos-platform` ;
3. ajouter uniquement les modules et fichiers SoftPOS ;
4. maintenir la non-régression `sg-common`, ServerPOS et simulateur ;
5. ne jamais commiter un SDK propriétaire, une clé ou un secret sans règle de
   distribution explicitement approuvée.

## 9. Livrables attendus

- matrice exigence → code → test → preuve ;
- OpenAPI et modèle de données ;
- application Android et guide d'installation ;
- backend et procédures d'exploitation ;
- simulateur SDK et scénarios automatiques ;
- rapport de sécurité et inventaire des dépendances ;
- dossier de certification et procès-verbal E2E.
