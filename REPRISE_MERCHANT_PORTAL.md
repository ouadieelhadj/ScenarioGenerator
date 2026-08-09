# Reprise - Portail d'Affiliation Commercant

Derniere mise a jour : 8 aout 2026

## Perimetre confirme

Regle de recette demandee par l'utilisateur le 9 aout 2026 : avant toute
campagne, verifier des le debut les services, ports, donnees, comptes et
variables secretes necessaires. Demander immediatement tout prerequis absent,
avant d'executer les tests, et ne jamais decouvrir ou signaler cette absence
seulement dans le bilan final. Les valeurs secretes ne sont pas consignees en
clair ; seuls leurs noms et leur disponibilite sont notes.

- Lot 1 : backend d'onboarding puis portail web.
- Lot 2 : application mobile Android demarree apres le Lot 1 web.
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

Branche backend : `codex/AddingBackendPortal`

Branche frontend courante : `codex/AddingFrontendMerchantPortal`

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

## Frontend - increment 1 termine

Le frontend reutilise l'application Angular partagee existante. Une cible
dediee `merchant-portal-web` a ete ajoutee sans recopier les infrastructures
d'authentification, themes, langues, layout ou workflow.

- port de developpement : `4230` ;
- URL backend onboarding : `http://localhost:8570` en developpement ;
- activation publique du compte invite ;
- connexion avec le backend identite existant ;
- creation d'un prospect par un Commercial, sans choix de mot de passe ;
- lien d'activation affiche une seule fois apres la reponse API ;
- tableau de bord adapte aux roles Commercant, Commercial et Checker/BO ;
- lecture reelle du squelette dossier par UUID et comportement ferme si le
  contrat de lecture detaille n'est pas disponible ;
- reutilisation des ecrans Maker/Checker existants ;
- filtrage des routes et menus par role ou permission, conforme au backend ;
- bundle separe de Switch et SwitchLab.

Validation du 7 aout 2026 :

- build `merchant-portal-web` : SUCCESS ;
- non-regression build `futurpayment-switch` : SUCCESS ;
- non-regression build `futurpayment-switchlab` : SUCCESS ;
- E2E Chromium : **3 passes, 0 echec** ;
- recherche dans le bundle : aucun chunk Switch, SwitchLab, Visa,
  administration ou deploiement ;
- JSON de configuration/traduction valides et `git diff --check` propre.

Preuve detaillee :
`tests/frontend/PROOF_OF_TEST_MERCHANT_PORTAL_INCREMENT_1_2026-08-07.md`.

## Premier travail non termine

Le prochain increment doit etendre le contrat backend de lecture et mise a
jour du dossier afin d'implementer l'assistant complet : entreprise,
representants, activite, points de vente, produits, documents KYC,
complements, revue et soumission. Restent hors MVP : multi-PDV avance,
QR/SoftPOS, tarification detaillee, GED/antivirus reel, notifications et
application mobile (lot 2).

## Mobile Android - premier increment termine

Le 7 aout 2026, l'utilisateur a autorise le demarrage du lot mobile. Une
premiere interruption a ete demandee pendant le telechargement du SDK, puis le
travail a repris au point exact consigne.

Etat reel atteint :

- `@ionic/angular` 8.8.17 et Capacitor 8.5.0 installes ;
- cible Angular `merchant-mobile`, port `4240` ;
- pages activation, connexion, accueil par role, prospect Commercial et
  dossier Commercant ;
- memes contrats/services API que le portail web, sans nouveau backend ;
- JWT mobile conserve uniquement en memoire dans cet increment, jamais dans
  `localStorage` ;
- build development `merchant-mobile` reussi ;
- projet natif `sg-frontend/android` cree par Capacitor et assets synchronises ;
- SDK, plateforme API 36 et Build Tools 36 installes sous `E:\Android\Sdk` ;
- cache Gradle place sous `E:\Android\GradleCache` ;
- APK debug compile et signe par la cle Android Debug ;
- package `com.moneycore.merchantportal`, version `1.0`, minSdk 24,
  targetSdk 36 ;
- 3 E2E format mobile passes et tests Android Gradle `BUILD SUCCESS` ;
- bundle mobile controle sans composant Switch, SwitchLab, Visa,
  administration ou deploiement.

APK de preuve :
`tests/frontend/artifacts/futurpayment-merchant-mobile-1.0-debug.apk`.

SHA-256 :
`6FF2231C952A97DDD41956F04E123A28DB7CEFEF691C9D12BE71C38B7A466E5A`.

Preuve detaillee :
`tests/frontend/PROOF_OF_TEST_MERCHANT_MOBILE_INCREMENT_1_2026-08-07.md`.

Limites explicites : l'APK n'a pas encore ete installe sur un appareil reel ;
le deep link Android externe attend le domaine final et son intent filter ; le
stockage persistant securise par Android Keystore n'est pas encore raccorde.
Dans cet increment, le JWT reste uniquement en memoire et disparait a la
fermeture de l'application.

Premier travail mobile non termine : ajouter le stockage Keystore, configurer
le domaine/deep link, installer l'APK sur un appareil de recette, raccorder les
pieces KYC camera/fichiers lorsque le contrat backend detaille sera disponible,
puis produire un APK release signe avec la cle de l'organisation.

## Recette integree finale des trois canaux

Un harnais PowerShell reproductible a valide trois parcours complets avec la
base PostgreSQL locale, Merchant Onboarding et Acquiring : Commercant Web,
Commercial Web et Mobile. Chaque parcours couvre Maker, trois pieces KYC,
revue Back-office, validation KYC, Checker distinct, generation du JSON
canonique batch et provisionnement Acquiring.

Resultat : **3/3 PROVISIONED**, jobs `SUCCEEDED`, MID
`100000000000004` a `100000000000006` et TID `10000002` a `10000007`.

La non-regression Maven finale, relancee apres la recette sur `sg-common`,
`sg-deployment-core`, `sg-generator-orchestrator`, `sg-acquiring` et
`sg-merchant-onboarding`, se termine par **BUILD SUCCESS : 107 tests, 0
echec, 0 erreur, 0 ignore**.

Harnais, JSON et guide utilisateur/Proof of Test :
`tests/merchant-onboarding/run-three-channel-e2e.ps1` et
`tests/merchant-onboarding/GUIDE_UTILISATEUR_PROOF_OF_TEST_3_CANAUX_2026-08-07.md`.

Limite importante : l'instance sandbox Acquiring desactive sa securite Spring
automatique, car l'adaptateur Onboarding n'a pas encore d'authentification
service-a-service. mTLS ou OAuth2 client credentials est requis avant
production. Les instances temporaires `8550`/`8570` ont ete arretees.

Les iterations de mise au point du harnais ont laisse dans la base locale des
dossiers techniques hors preuve, dont un essai ayant expose le refus HTTP 401
interservices. Le jeu de recette faisant foi est exclusivement constitue des
trois references `ONB-29CBB112`, `ONB-405A108D` et `ONB-38832F25` ci-dessus ;
les donnees techniques n'ont pas ete supprimees afin de conserver l'audit.

Le cadrage de developpement frontend est disponible dans
`CADRAGE_FRONTEND_MERCHANT_PORTAL.md`. Il couvre le portail Angular du Lot 1 et
prepare explicitement le Lot 2 mobile Android : Ionic Angular/Capacitor, deux
profils Commercant/Commercial, contrats partages, securite native et livraison
APK/AAB signee. Le code mobile reste differe au Lot 2, mais il est inclus dans
l'architecture et le decoupage.

## Processus

Les instances temporaires E2E Acquiring `8550` et Merchant Onboarding `8570`
ont ete lancees uniquement pour la validation et arretees en fin de session.
Les services utilisateur deja actifs sur 8080, 8090, 4210 et 4220 n'ont pas
ete modifies.

## Increment complet Web et Mobile du 8 aout 2026

Le MVP frontal est maintenant raccorde aux contrats backend complets : dossier
du compte courant, saisie entreprise/activite/reglement/produit/PDV/TPE, upload
binaire de pieces, soumission KYC, revue Back-office avec consultation des
fichiers, complements/rejet/validation, Maker/Checker et provisioning
immediat ou batch avec affichage MID/TID.

Le backend stocke les fichiers PDF/JPEG/PNG dans un repertoire configurable
avec references opaques, limite de 20 Mo, empreinte SHA-256 calculee cote
serveur, controle de chemin et autorisations proprietaire/relecteur.

Le mobile reutilise le meme dossier et les memes API. Il accepte camera ou
fichier, chiffre la session au moyen d'Android Keystore/AES-GCM dans un plugin
Capacitor natif et bloque captures et apercus recents avec `FLAG_SECURE`.

Validation finale : **109 tests Maven**, **5 E2E Web**, **3 E2E Mobile** et
Gradle `testDebugUnitTest assembleDebug`, tous sans echec. APK Wi-Fi :
`tests/frontend/artifacts/futurpayment-merchant-mobile-wifi-192.168.1.86-debug.apk`,
SHA-256 `9EAA9253D1CCE9BC5D3C14896450A44EAB3378CD5398C62B6BF55BA35A65E094`.

La recette integree PostgreSQL/Acquiring a ete rejouee le 9 aout apres
preflight des prerequis : 3/3 dossiers `PROVISIONED`, jobs `SUCCEEDED`, MID
`100000000000010` a `100000000000012` et TID `10000014` a `10000019`.
Dix captures de reussite (Web et Mobile) sont integrees au guide des trois
canaux. Voir `tests/frontend/PROOF_OF_TEST_MERCHANT_PORTAL_COMPLETE_2026-08-08.md`.

Reste avant production : auth interservices, notification SMS/e-mail,
GED/antivirus, referentiels et fonctions avancees (multi-PDV, tarification,
contrats/signature), gateway HTTPS/session Web durcie, deep link verifie,
signature release/AAB et recette appareils reels.
