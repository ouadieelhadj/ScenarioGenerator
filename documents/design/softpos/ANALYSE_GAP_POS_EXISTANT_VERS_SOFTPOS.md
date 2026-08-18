# Analyse d'écart — POS existant vers SoftPOS

**Projet :** FuturPayment SoftPOS

**Date :** 18 août 2026
**Statut :** cadrage initial, aucun code SoftPOS développé

## 1. Conclusion

Le socle actuel permet de réutiliser le traitement monétique, mais ne constitue
pas encore une solution SoftPOS. `WayPosServer` et `wayPosSimulator` couvrent le
protocole POS, le routage et les scénarios de transaction. Il manque toute la
frontière mobile sécurisée : application Android, SDK contactless certifié,
enrôlement du téléphone, authentification de l'opérateur, attestation de
l'appareil et API publique SoftPOS.

La solution recommandée est donc :

```text
Carte ou wallet sans contact
        ↓ NFC
Application FuturPayment SoftPOS + SDK certifié
        ↓ HTTPS/API à la demande
Façade SoftPOS du serveur POS
        ↓ contrat interne normalisé
WayPosServer
        ↓ ISO 8583 / route existante
Switch membre et réseaux d'autorisation
```

L'application porte trois canaux d'acceptation distincts :
`NFC`, `QR_MPM` (QR présenté par le commerçant) et `QR_CPM` (QR présenté par
le client). Le canal QR possède son propre connecteur et ses propres règles de
routage ; il ne doit pas être présenté comme automatiquement couvert par la
conformité PCI MPoC du canal NFC.

Le téléphone n'ouvre pas de connexion ISO 8583 avec le Switch. Chaque opération
utilise une requête API à la demande. Le SDK certifié traite le NFC, le kernel
EMV, la saisie PIN éventuelle et les données sensibles dans sa frontière de
sécurité.

## 2. Éléments réellement réutilisables

| Capacité existante | Preuve dans le dépôt | Réutilisation SoftPOS |
|---|---|---|
| Serveur POS transactionnel | `sg-way-pos-server` | Conservé comme moteur de traitement et de routage |
| Connexion à la demande | `WayPosSimulatorClient.exchange()` ouvre, traite et ferme une session | Cohérent avec le fonctionnement SoftPOS à la demande |
| ISO 8583 et framing | jPOS, `WayPosPackager`, `WayPosLengthChannel` | Conservés côté serveur uniquement |
| Routage local/réseaux | `PosRoutingService`, `PosRouteResolver`, connecteurs | Conservé derrière la façade SoftPOS |
| Idempotence et journal | `PosJournalService`, `pos_authorizations` | Étendu avec l'identité SoftPOS et l'état mobile |
| Reprise et reversal | `PosRecoveryService`, `pos_outbox` | Réutilisés avec une API de consultation de statut |
| EMV, ARQC/ARPC | services WayPos et contrat interne | Réutilisés si le SDK fournit les données autorisées |
| PIN et clés | services HSM/LMK/RKI existants | Non exposés au mobile ; responsabilité à partager avec le SDK certifié |
| Batch et réconciliation | services WayPos existants | Conservés côté serveur, adaptés au modèle SoftPOS |
| Simulateur et scénarios | `sg-way-pos-simulator`, tests WayPos | Transformés en banc de test du backend SoftPOS et du Switch |
| Administration commerçant/TPE | Merchant Portal et profils terminal | À raccorder, sans recréer le référentiel commerçant |
| Fraud Monitoring | plateforme et Gateway Fraud existants | Appel synchrone avant décision, selon la politique banque |

## 3. Matrice des écarts

| Domaine | Situation actuelle | Écart | Priorité |
|---|---|---|---|
| Application mobile | Aucun projet Android/Gradle ni manifeste Android | Application Kotlin, parcours marchand, NFC et reçus absents | P0 |
| Kernel contactless | Aucun SDK SoftPOS/MPoC | Choisir et intégrer un SDK certifié ; ne pas fabriquer le kernel | P0 externe |
| Paiement QR | Aucun contrat ni route QR dans le périmètre SoftPOS initial | Ajouter `QR_MPM` et `QR_CPM`, génération/lecture, expiration, signature et routage dédiés | P1 |
| API mobile | `/api/routing/v1/transactions` est un contrat interservices | Créer une API SoftPOS versionnée qui n'accepte jamais PAN/PIN en clair | P0 |
| Sécurité API | Le seul filtre explicite est limité au profil `connected-e2e` et autorise tout | OAuth2/OIDC, contrôle de scopes, mTLS interservices, limitation de débit | P0 |
| Configuration sensible | Valeurs de développement par défaut présentes dans `application.yml` | Supprimer tout secret par défaut avant une recette partagée | P0 |
| Multibanque | Le profil terminal porte TID et MID, mais pas banque/membre/PDV | Ajouter `memberId`, établissement, PDV et partitionnement des accès | P0 |
| Type de terminal | Le profil ServerPOS ne distingue pas TPE physique et SoftPOS | Ajouter `terminalType = PHYSICAL_POS | SOFTPOS` et migrer l'existant vers `PHYSICAL_POS` | P0 |
| Enrôlement appareil | Absent | Activation, liaison appareil–terminal–opérateur, révocation et renouvellement | P0 |
| Attestation | Absente | Vérification application/appareil, anti-root, anti-overlay et politique de risque | P0 |
| Opérateur | Absent | Identité, rôle, session, MFA selon politique et fermeture à distance | P0 |
| Cycle terminal | Seulement activation du profil TPE | PENDING, ACTIVE, SUSPENDED, REVOKED, COMPROMISED | P0 |
| Contrat SDK | Absent | Adaptateur indépendant de l'éditeur et modèle de résultat opaque | P0 |
| Paiement mobile | Le contrat interne contient PAN, expiration, PIN block et EMV | Traduire uniquement un résultat SDK protégé vers le modèle interne | P0 |
| Statut transaction | Réponse synchrone et journal interne | Endpoint de statut idempotent pour timeout/reprise mobile | P0 |
| Reçu | Absent | Reçu numérique sans données sensibles, renvoi et preuve d'acceptation | P1 |
| Remboursement/annulation | Moteur POS partiellement disponible | API et règles d'habilitation SoftPOS dédiées | P1 |
| Réconciliation | Présente côté POS traditionnel | Définir si elle est par terminal logique, commerçant ou acquéreur | P1 |
| Configuration distante | Absente | Version minimale, AID/CAPK via SDK, limites, fonctionnalités et blocage | P1 |
| Supervision | Logs et journal POS disponibles | Santé mobile, versions, attestation, latence et taux d'échec | P1 |
| Frontend membre | Le frontend global ne possède pas de sous-module SoftPOS membre | Ajouter l'administration SoftPOS dans FuturPayment Switch, filtrée par `memberId` et distincte de l'application Android commerçant | P0 |
| Fraude | Pas d'appel démontré depuis ServerPOS | Appel Fraud, comportement ALERT/CHALLENGE/HOLD/BLOCK et fallback | P1 |
| Offline | Non cadré pour SoftPOS | Désactivé au départ ; toute activation exige règles SDK/réseaux spécifiques | Hors premier périmètre |
| Certification | Tests POS et MTIP existants, mais aucun dossier SoftPOS | PCI MPoC, EMV/contactless et L3 acquéreur/réseaux à planifier | P0 externe |

## 4. Décisions d'architecture proposées

Architecture de référence confirmée :

```text
Application SoftPOS
        ↓ Internet / HTTPS
Backend SoftPOS sécurisé
        ↓ canal privé authentifié
POServer
        ↓
Système de traitement des transactions
```

1. Créer une **façade SoftPOS séparée** ; ne pas exposer le contrat de routage
   interne à Internet ou à l'application Android.
2. Conserver `WayPosServer` comme autorité de routage, d'idempotence, de
   reprise et de journalisation monétique.
3. Intégrer un **SDK SoftPOS certifié** derrière une interface fournisseur.
   Le changement de fournisseur ne doit pas modifier le cœur POS.
4. Utiliser un identifiant opaque de transaction SDK. Aucun PAN complet, PIN,
   clé ou donnée NFC brute ne transite dans les journaux ou API métier.
5. Porter `memberId`, `merchantId`, `outletId`, `terminalId`, `deviceId` et
   `operatorId` dans le contexte signé, puis les revalider côté serveur.
6. Conserver le fonctionnement **online et à la demande** pour le premier lot.
7. Déporter les opérations lourdes ou non critiques hors du chemin synchrone,
   sans rendre asynchrone la décision d'autorisation.
8. Étendre `pos_terminal_profiles` avec un `terminal_type` obligatoire. Les
   terminaux actuels deviennent `PHYSICAL_POS`; seuls les terminaux issus du
   provisioning SoftPOS prennent la valeur `SOFTPOS`.
9. Porter un `acceptance_channel` obligatoire avec les valeurs
   `NFC`, `QR_MPM` ou `QR_CPM`. Le canal détermine le validateur, le connecteur
   et la politique de sécurité appliqués ; il ne change pas l'identité MID/TID.
10. Le Backend SoftPOS assure enrôlement, authentification, attestation,
    association MID/TID, supervision, risques, audit et conversion du contrat
    mobile. Il ne manipule jamais de PIN, de clé ou de donnée carte sensible en
    clair : ces éléments restent dans les frontières SDK/HSM certifiées.
11. Le Backend SoftPOS doit fournir **deux modes d'intégration** vers POServer,
    sélectionnables par banque et environnement :
    `REST_JSON` via API HTTPS sécurisée et `ISO8583_PERSISTENT` via une liaison
    ISO 8583 permanente. Le dialecte, framing, MAC, clés, sign-on, echo,
    reconnexion, timeouts et reprises du mode ISO doivent être validés.
12. Une connexion directe Application SoftPOS → POServer reste interdite par
    défaut. Une solution fournisseur couvrant PCI MPoC ne suffit pas à elle
    seule : toute exception exige une nouvelle validation formelle de
    l'exposition réseau, de l'attestation, de la supervision et du partage des
   responsabilités.
13. Une route possède un seul mode primaire à un instant donné. Un basculement
    entre REST et ISO n'est jamais un simple renvoi : après un timeout ou un
    état inconnu, le Backend doit consulter l'état, conserver la même identité
    et la même clé d'idempotence, puis appliquer la stratégie repeat/reversal
    avant toute resoumission afin d'empêcher un double débit.
14. L'administration SoftPOS est un sous-module du frontend **FuturPayment
    Switch/Membre**. Un utilisateur membre ne voit que ses commerçants, PDV,
    TID, appareils, opérateurs, transactions, alertes et routes. L'application
    Android utilisée par le commerçant reste une application séparée.

## 5. Données nouvelles minimales

- `softpos_applications` : version, signature, environnement et statut ;
- `softpos_devices` : appareil pseudonymisé, banque, terminal, état et dernière
  attestation ;
- `softpos_operators` : référence d'identité, commerçant, rôles et statut ;
- `softpos_activations` : code à usage unique haché, expiration et consommation ;
- `softpos_sessions` : session, appareil, opérateur et révocation ;
- `softpos_transactions` : référence mobile, référence SDK opaque, état,
  canal d'acceptation, idempotence et lien `pos_authorizations` ;
- `softpos_qr_sessions` : mode MPM/CPM, payload protégé ou empreinte,
  expiration, statut et référence réseau ;
- `softpos_integrity_events` : verdicts non sensibles et décisions ;
- `softpos_receipts` : données de reçu masquées et preuve d'émission ;
- `softpos_remote_config` : version minimale, fonctions et politique par banque.
- `softpos_poserver_routes` : banque, environnement, mode primaire
  `REST_JSON`/`ISO8583_PERSISTENT`, endpoint non sensible, timeouts, politique
  de reprise et statut ;
- `softpos_iso_sessions` : banque, connexion logique, état sign-on/echo,
  dernière activité et compteur de reconnexions, sans clé ni secret.

Le profil ServerPOS existant reçoit aussi `terminal_type`, `member_id` et
`outlet_id`. `device_id` reste dans le domaine SoftPOS : un TID est une identité
monétique, pas l'identifiant physique du téléphone.

Toutes ces tables doivent être partitionnées logiquement par membre/banque et
protégées par des contraintes d'unicité incluant cette identité.

## 6. Dépendances qui ne peuvent pas être inventées

- fournisseur et SDK SoftPOS/MPoC retenus ;
- pays, banques acquéreuses et marques à certifier ;
- prise en charge du PIN sur COTS et limites sans PIN ;
- format exact du résultat protégé remis par le SDK ;
- responsabilité des clés, HSM et certificats ;
- règles d'enrôlement des commerçants, opérateurs et appareils ;
- politique offline éventuelle ;
- réseaux QR, formats MPM/CPM, règles de signature et responsabilité du
  paiement QR ;
- environnement de certification et cartes de test.

Le développement du backend, des contrats et du simulateur peut commencer
avant le choix final du SDK grâce à un adaptateur simulé. Une transaction NFC
réelle et une déclaration de conformité production restent impossibles sans le
SDK et le parcours de certification.

## 7. Références normatives et industrielles

- PCI SSC — Mobile Payments on COTS (MPoC) :
  https://www.pcisecuritystandards.org/standards/mobile-payments-on-cots-mpoc/
- EMVCo — EMV Mobile et processus d'évaluation :
  https://www.emvco.com/emv-technologies/mobile/
- EMVCo — TapToMobile :
  https://www.emvco.com/news/emvco-launches-new-testing-process-to-support-the-use-of-taptomobile-devices-for-contactless-payment-acceptance/
- EMVCo — QR Code Payments, modes MPM et CPM :
  https://www.emvco.com/emv-technologies/qr-codes/
- Android — Play Integrity API :
  https://developer.android.com/google/play/integrity/overview
- Exemple d'architecture API/SDK — Adyen Tap to Pay Android :
  https://docs.adyen.com/point-of-sale/mobile-android/build/tap-to-pay/

## 8. Verdict

- **GO cadrage et développement du socle avec SDK simulé.**
- **NO-GO paiement NFC réel** tant que le fournisseur SDK et ses contrats ne
  sont pas disponibles.
- **NO-GO production** tant que sécurité, migration, HSM, certification et L3
  ne sont pas validés sur l'environnement cible.
