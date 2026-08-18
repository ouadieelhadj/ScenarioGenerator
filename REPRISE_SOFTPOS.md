# Reprise — FuturPayment SoftPOS

## État au 18 août 2026 — socle développé

- Demande : étudier l'écart entre le POS existant et un module SoftPOS, puis
  préparer le plan de développement.
- Le socle SoftPOS a été développé sur la branche dédiée
  `codex/softpos-platform`, dans le worktree
  `D:\MoneyCore\ScenarioGenerator-softpos`.
- `POS_REPRISE.md` a été lu intégralement et le code de `sg-way-pos-server`,
  `sg-way-pos-simulator`, les contrats communs et le frontend ont été audités
  en lecture seule.
- Le dépôt contient désormais un projet Android SoftPOS, un contrat de SDK
  indépendant du fournisseur et un SDK de laboratoire explicitement interdit
  en production.
- Le serveur POS et le simulateur fonctionnent déjà à la demande : le client
  ouvre une session TCP pour la transaction puis la ferme.
- Le contrat `/api/routing/v1/transactions` est interne et ne doit pas être
  exposé au mobile.
- Le profil ServerPOS devra porter
  `terminalType = PHYSICAL_POS | SOFTPOS`. La migration affectera
  `PHYSICAL_POS` aux terminaux existants sans changer leurs TID/MID ; les
  nouveaux terminaux SoftPOS porteront `SOFTPOS`.
- Le périmètre d'acceptation comprend désormais trois canaux distincts :
  `NFC`, `QR_MPM` et `QR_CPM`. Le QR dispose de contrats et d'un routage
  dédiés ; sa conformité ne doit pas être assimilée automatiquement à PCI
  MPoC, qui cadre le canal mobile sur COTS concerné.
- Deux frontends sont distingués : l'application Android commerçant et le
  sous-module d'administration SoftPOS intégré à FuturPayment Switch/Membre.
  Ce dernier est isolé par `memberId` et rôles côté backend.

## Décision proposée

Architecture : application Android + SDK certifié → API HTTPS SoftPOS → façade
SoftPOS séparée → WayPosServer → Switch/réseaux. WayPosServer, son routage, son
journal, ses reprises et le simulateur sont réutilisés.

Cette architecture est confirmée comme base : l'application exposée à Internet
ne contacte jamais directement POServer. Le Backend SoftPOS porte enrôlement,
authentification, attestation, association MID/TID, supervision, risque, audit
et conversion. Le Backend doit supporter les deux modes configurables par
banque/environnement : `REST_JSON` sécurisé et `ISO8583_PERSISTENT` sur liaison
permanente. Un seul mode est primaire par route et aucun basculement après
timeout ne peut resoumettre aveuglément une transaction. Les PIN, clés et
données sensibles en clair restent hors du backend.

Le SDK contactless certifié est une dépendance externe obligatoire pour tout
paiement NFC réel. Un SDK simulé peut débloquer le développement du backend et
des parcours de laboratoire.

## Documents produits

- `documents/design/softpos/ANALYSE_GAP_POS_EXISTANT_VERS_SOFTPOS.md` ;
- `documents/design/softpos/PLAN_DEVELOPPEMENT_SOFTPOS.md`.

## Développements réalisés

- contrats partagés mobiles/backend et interface fournisseur
  `SoftPosPaymentSdk` ;
- backend SoftPOS multibanque : enrôlement, attestation, terminaux, routes,
  transactions, idempotence et isolation par `memberId` ;
- connecteurs Backend SoftPOS vers POServer en `REST_JSON` et
  `ISO8583_PERSISTENT` ;
- extension ServerPOS avec `terminalType = PHYSICAL_POS | SOFTPOS` et façade
  interne SoftPOS ;
- migrations PostgreSQL SoftPOS et migration idempotente des terminaux POS ;
- sous-module d'administration SoftPOS dans le frontend Switch/Membre et son
  proxy BFF ;
- squelette Android sécurisé, canaux `NFC`, `QR_MPM` et `QR_CPM`, avec
  abstraction du SDK de paiement ;
- simulateur de SDK limité au laboratoire afin de développer sans présenter
  une acceptation NFC certifiée.

## Statut

- Socle simulé développé ; validation technique à consigner avant livraison.
- NO-GO NFC réel sans SDK certifié.
- NO-GO production sans sécurité, migration, HSM, homologation et L3.

## Limites et premier travail non terminé

- Intégrer le SDK PCI MPoC réel retenu après POC fournisseur. Le candidat
  présélectionné est MineSec MineHades, sans décision contractuelle définitive.
- Raccorder le service d'attestation/monitoring réel ; la production doit
  rester fermée sans verdict cryptographiquement vérifié.
- Valider le dialecte ISO POS, le sign-on/echo, les MAC et les échanges de clés
  avec POServer et le HSM de l'environnement cible.
- Exécuter l'E2E sur processus et bases séparés ainsi que le build Android dans
  un environnement disposant du SDK Android.
- Réaliser les homologations PCI MPoC, schémas et L3 avant toute production.

## Tests et Git

- La campagne Maven agrégée du 18 août 2026 valide 137 tests sans échec :
  `sg-common` 77, BFF 1, contrats SoftPOS 2, ServerPOS 49, simulateur SDK 2 et
  backend SoftPOS 6. Le test ciblé du canal ISO persistant a été relancé après
  correction de son nettoyage jPOS : 1 test, zéro échec et aucune exception de
  fermeture.
- Le build Angular Switch du 18 août 2026 a réussi et a produit le chunk
  paresseux `softpos-workspace-component`.
- Le projet Android n'a pas été compilé dans cette campagne : le SDK Android et
  un SDK PCI MPoC réel ne sont pas encore disponibles dans l'environnement.
- Aucun secret, PAN complet, PIN clair ou clé claire ne doit être ajouté au
  commit.
