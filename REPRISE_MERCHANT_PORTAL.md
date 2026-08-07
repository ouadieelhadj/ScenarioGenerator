# Reprise - Portail d'Affiliation Commercant

Derniere mise a jour : 7 aout 2026

## Perimetre confirme

- Lot 1 : backend d'onboarding puis portail web.
- Lot 2 : application mobile, sans code mobile dans le lot courant.
- Le commercial cree le prospect et l'invitation du compte commercant.
- Le commercant remplit et soumet son propre dossier.
- Le Maker/Checker existant du frontend doit etre reutilise.
- Le batch n'est pas un canal d'onboarding. Il traite apres approbation les
  dossiers cumules, avec le meme JSON canonique que l'appel API unitaire.
- Acquiring reste autoritatif pour le commercant, les contrats, MID et TID.
- Aucun appel direct du portail vers WAY4 ou vers un reseau monetique.

Source fonctionnelle non modifiee : le PDF sous
`documents/Portail Affiliation Commercant/`.

## Etat implemente

Branche : `codex/AddingBackendPortal`

### Nouveau module `sg-merchant-onboarding`

- compte portail sans stockage de mot de passe, avec etat
  `INVITATION_PENDING` et reference optionnelle vers l'identite existante ;
- dossier d'onboarding persiste et transitions protegees ;
- creation du prospect par le commercial et saisie par le commercant ;
- soumission, approbation/rejet Maker/Checker, maker different du checker ;
- API `/api/workflow/requests/mine` et `/api/workflow/approvals/mine` ;
- provisionnement immediat ou mise en file batch ;
- export batch comme collection du meme contrat JSON canonique ;
- traitement de 1 a 1000 dossiers, resultat par dossier et reprise des echecs ;
- cle d'idempotence stable `merchant-onboarding:{caseId}` ;
- adaptateur HTTP desactive par defaut et echec ferme : aucun MID/TID fictif.

Port REST par defaut : `8570`.

### Extension `sg-acquiring`

- endpoint `POST /api/internal/acquiring/v1/merchant-onboarding` ;
- creation du commercant, du point de vente, du contrat commercant et des
  contrats TPE dans une transaction ;
- attribution des MID (15 chiffres) et TID (8 chiffres) par Acquiring ;
- recu persiste par cle d'idempotence et empreinte SHA-256 ;
- rejeu sans duplication et rejet d'une cle reutilisee avec un autre contenu ;
- preparation des TID logiques sans inventer de numero de serie ou de TPE
  physique. L'affectation du materiel reste une etape Acquiring separee.

### Dernier lot backend termine

- authentification JWT partagee avec l'identite existante et controle RBAC ;
- aucun identifiant d'appelant n'est accepte depuis un en-tete declaratif ;
- invitation d'identite a usage unique, token stocke uniquement sous forme
  SHA-256, expiration 48 h et activation avec politique de mot de passe ;
- KYC persiste avec soumission, complements, validation et rejet ;
- pieces versionnees par references de stockage opaques, controle du type,
  de la taille et de l'empreinte SHA-256 ;
- separation deposant/relecteur et lecture dediee des dossiers/pieces par le
  Back-office et le Checker ;
- provisionnement Acquiring immediat ou batch post-validation ;
- rejeu immediat idempotent et reprise explicite des jobs batch en echec.

## Migrations SQL

- `sql/merchant-onboarding/V1__create_merchant_onboarding.sql`
- `sql/merchant-onboarding/V2__add_kyc_documents_and_rbac.sql`
- `sql/acquiring/V3__create_onboarding_provisioning.sql`
- `sql/21_merchant_identity_invitation.sql`

Les quatre migrations ont ete appliquees et validees le 7 aout 2026 sur la
base locale PostgreSQL `scenariogenerator`. Les droits des tables et sequences
du nouveau perimetre ont ete accordes a `scenario_user`.

## Validation executee

Commande agregee avec le Maven embarque, cache local et modules
`sg-generator-orchestrator,sg-acquiring,sg-merchant-onboarding -am test`.

Resultat final du 7 aout 2026 a 16:42 : `BUILD SUCCESS` en 2 min 38 s.

- `sg-common` : 77 tests, 0 echec ;
- `sg-deployment-core` : 6 tests, 0 echec ;
- `sg-generator-orchestrator` : 1 test, 0 echec ;
- `sg-acquiring` : 17 tests, 0 echec ;
- `sg-merchant-onboarding` : 6 tests, 0 echec.

Total : **107 tests, 0 echec, 0 erreur, 0 ignore**.

Preuve detaillee :
`tests/merchant-onboarding/PROOF_OF_TEST_BACKEND_2026-08-07.md`.

Deux parcours E2E reels ont ete executes avec PostgreSQL, JWT signes de roles
distincts et appel HTTP vers Acquiring :

- immediat : `ONB-8FFF2B3F`, statut `PROVISIONED`, KYC `VALIDATED`,
  MID `100000000000000`, TID `10000000` ;
- batch : `ONB-EAE21FF5`, passage `QUEUED_FOR_PROVISIONING`, job `SUCCEEDED`,
  statut final `PROVISIONED`, MID `100000000000003`, TID `10000001`.

Deux jeux de donnees negatifs ont aussi confirme l'echec ferme sur produit
inconnu ou appartenant a un autre acquereur ; ils restent traces en
`PROVISIONING_FAILED`. L'E2E n'a pas redemarre l'identite utilisateur sur 8080 :
l'invitation/activation est couverte par le test de service, tandis que les
E2E utilisent des JWT sandbox signes correctement.

## Fichiers du perimetre

- `pom.xml` ;
- `sg-merchant-onboarding/**` ;
- les nouveaux controller, domaine, repository, services et test onboarding
  sous `sg-acquiring` ;
- les quatre migrations SQL ;
- `CURRENT_CODEX_TASK.md`, `SESSION_RESUME.md` et ce document.

Les autres modifications et fichiers non suivis du worktree sont hors
perimetre et doivent etre preserves.

## Premier travail non termine

Le backend MVP du lot courant est termine. Le prochain travail autorise est le
frontend web du Portail d'Affiliation, raccorde aux API ci-dessus et au
Maker/Checker existant. Restent hors MVP backend et devront etre cadres avant
implementation : multi-PDV avance, QR/SoftPOS, tarification detaillee,
stockage GED/antivirus reel, notifications et application mobile (lot 2).

## Processus

Les instances temporaires E2E Acquiring `8550` et Merchant Onboarding `8570`
ont ete lancees uniquement pour la validation et arretees en fin de session.
Les services utilisateur deja actifs sur 8080, 8090, 4210 et 4220 n'ont pas
ete modifies.
