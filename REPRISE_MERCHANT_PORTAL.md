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

## Increment juridique et multi-PDV du 10 aout 2026

### Retour d'experience obligatoire

La campagne a d'abord assimile a tort la reussite des 121 tests Java a une
validation presque complete de l'increment, alors que la migration n'avait pas
encore ete executee sur PostgreSQL reel. Cette conclusion prematuree a reporte
la detection de l'anomalie metier Acquiring.

Regle permanente issue de cette erreur : lorsqu'un increment contient une
migration ou un backfill, les tests Java ne suffisent jamais a prononcer sa
validation. Le verdict exige, avant communication au validateur, le preflight
des prerequis, une sauvegarde, la premiere application, le rejeu, les
comptages/empreintes avant-apres, les controles d'absence de perte et de
doublon, puis toutes les invariantes metier sur les donnees PostgreSQL reelles.
Si une seule invariance reste en anomalie, le verdict est `NO-GO`.

La session `Developpement Portal Commercant`
(`019fd736-217b-7560-8fe4-44efbe97bcda`) a repris l'increment 1 du plan
Commercant/PDV. Elle a implemente le modele juridique complet, le profil legal,
les representants et beneficiaires, les adresses structurees, le multi-PDV,
l'adaptation sans doublon du PDV v1, l'API v2, les referentiels minimaux et les
migrations V3/V4 Merchant Onboarding/Acquiring.

La reprise a corrige la restitution du RIB et de l'identite du responsable de
PDV, puis a separe le format d'erreur v2 enrichi du contrat historique v1.

Validation executee :

- porte ciblee : 15 tests, 0 echec, 0 erreur, 0 ignore ;
- non-regression `sg-common`, `sg-acquiring`, `sg-merchant-onboarding` :
  121 tests, 0 echec, 0 erreur, 0 ignore ;
- `git diff --check` propre hors avertissements de conversion LF/CRLF.

Preuve et matrice :
`tests/merchant-onboarding/PROOF_OF_TEST_MERCHANT_INCREMENT_1_2026-08-10.md`.

PostgreSQL 18 a ensuite ete demarre directement depuis
`D:\MoneyCore\PostgreSQL\18`. Une sauvegarde `pg_dump` a ete prise, puis V3 et
V4 ont ete appliquees et rejouees en transaction avec arret sur erreur.

Resultat : 16 dossiers historiques, 16 PDV et 16 mappings distincts ; au rejeu,
0 creation, 16 ignores, 0 erreur ; aucune empreinte historique modifiee et
aucun doublon. Tous les dossiers Onboarding ont exactement un principal actif.

Une anomalie reelle bloque toutefois la porte : le commercant Acquiring actif
`885d1af8-2f05-465a-832a-6a91ae613da3` possede un contrat et une boutique
e-commerce mais aucun PDV. Il n'est lie a aucun dossier Onboarding ni recu de
provisioning permettant de reconstruire une adresse et des contacts reels.
Aucun PDV fictif n'a ete cree. Premier travail non termine : obtenir la donnee
metier du PDV, la reconcilier, puis rejouer la requete d'unicite du principal.
Le verdict reste NO-GO increment 2 et l'increment 2 n'a pas commence.

Apres reconciliation, la campagne complete doit etre reprise de bout en bout :
porte ciblee, non-regression agregee, controle PostgreSQL V3/V4 reproductible
et verification finale de zero anomalie. Aucun GO ne doit etre deduit d'un
rejeu effectue avant la correction de la donnee metier.

## Developpement des increments 2 a 5 du 10 aout 2026

Sur decision explicite de l'utilisateur, le developpement des increments 2 a
5 a continue malgre la porte metier non levee de l'increment 1. Cette decision
ne transforme pas le NO-GO de validation en GO.

Le perimetre livre comprend les produits et demandes TPE/e-commerce par PDV,
le provisionnement v2 objet par objet, les outbox transactionnelles, OAuth2
Client Credentials entre services, les referentiels administrables, les packs
tarifaires versionnes, les derogations Maker/Checker et le module dedie
`sg-way4-aura-connector`. Celui-ci resout des bindings AURA dates/versionnes,
genere un XML deterministe, controle l'empreinte du XSD avant validation et
persiste les empreintes de fichier/application. Acquiring alimente le
connecteur par une outbox avec reservation courte, bail de deux minutes,
reprise apres crash, backoff et finalisation transactionnelle distincte de
l'appel HTTP. La soumission dans WAY4 reste desactivee par defaut.

Le profil de recette est `runtime/merchant-portal-e2e/.env`. Il ne contient
aucun secret et reference le runtime plateforme pour charger les identifiants
DB en memoire.

Validation automatique : 134 tests, aucun echec ni erreur. La validation
PostgreSQL a utilise une sauvegarde restauree dans une base temporaire pour
comparer toutes les tables : 135 avant, 153 apres, aucune table en diminution,
18 nouvelles tables et uniquement le referentiel Onboarding passant de 8 a 17
lignes. La chaine de six migrations V4 a V6 et WAY4 V1 a passe deux replays.

La recette positive Portal vers Acquiring vers WAY4 ne peut pas encore etre
executee sans inventer de donnees : un commercant actif reste sans PDV
principal, une anomalie Acquiring reste non reconciliee, aucun binding AURA
actif n'est present et les autorites MID/TID restent `UNDECIDED`. Les fichiers
runtime inspectes ne contiennent pas non plus la configuration d'un issuer
OAuth2 de recette. Aucun contournement de securite et aucune donnee fictive ne
doivent etre utilises pour masquer ces absences.

Document de cadrage increment 5 :
`D:\LanaCash\OpenWay\installationOCI\commercial-portal-conformite\CADRAGE_INCREMENT_5_ADAPTATION_MERCHANT_PORTAL_WAY4_AURA.md`.
XSD principal : `offline/WAY4ApplFile.xsd`, sous
`D:\LanaCash\OpenWay\installationOCI\chargementxmlway4\schemas\xsd\xsd`,
SHA-256 `F76E4927B2365B6A7B9FA9B7EE1B0CF28C87313CDE724BD6C6484673D0E8A680`.

Preuve detaillee :
`tests/merchant-onboarding/PROOF_OF_TEST_MERCHANT_INCREMENTS_2_5_2026-08-10.md`.

## Découplage Portal vers WAY4 du 11 août 2026

Le parcours WAY4 ne traverse plus FuturPayment Acquiring. Le code d'export et
l'outbox WAY4 ont été retirés de `sg-acquiring`. Merchant Onboarding émet
désormais directement un événement `way4.export.requested` vers le connecteur,
indépendamment de `merchant.provisioning.requested` destiné à Acquiring.

Le contrat direct fournit le commerçant, le règlement, les PDV, les demandes
TPE et un `Application/RegNumber` stable. Le XML ne renseigne ni identifiant
client WAY4, ni numéro de contrat commerçant, ni `ContractNumber` TPE/TID. Le
connecteur possède une allocation MID persistante, fermée par défaut jusqu'à
approbation des règles AURA réelles. Les dossiers v1 incomplets restent sur le
flux Acquiring historique sans valeur juridique inventée.

La validation courante totalise 135 tests réussis sans échec : `sg-common` 77,
`sg-acquiring` 26, `sg-merchant-onboarding` 30 et connecteur 2. Les migrations
V7 Onboarding et V2 Connecteur ont été appliquées puis rejouées sur PostgreSQL
18 après sauvegarde : 16 dossiers avant/après, 0 doublon, 0 orphelin et aucune
allocation MID ou donnée métier fictive.

Le raccordement du fichier retour reste le premier travail non terminé : le
format et le canal réels, les habilitations et le callback OAuth2 doivent être
confirmés. Aucun import WAY4 ni E2E positif n'a été exécuté. Voir
`tests/merchant-onboarding/PROOF_OF_TEST_PORTAL_DIRECT_WAY4_2026-08-11.md`.

## Corrections des remarques validateur du 11 aout 2026

Les six demandes ont ete implementees sans reintegrer WAY4 dans Acquiring :
maintien des exports WAY4 en attente lorsque le connecteur est desactive,
trace des erreurs dans l'etat WAY4, tests OAuth2 complets, destination metier
`FUTURPAYMENT`/`WAY4`/`BOTH`, routage selectif et lien HTTPS unique avec Android
App Link et repli Web. Le contrat de mise a jour v1 reste inchangé ; un endpoint
dedie selectionne la destination et la v2 la porte dans son contrat.

La campagne reconstruite depuis des repertoires `target` supprimes totalise
147 tests Java reussis sans echec ni erreur : common 77, Acquiring 26,
Onboarding 37, connecteur 7. Les builds Web et mobile sont reussis. Capacitor a
ete synchronise et l'APK debug a ete assemblee ; le manifeste fusionne contient
`autoVerify=true`, le package `com.moneycore.merchantportal` et la route HTTPS
`/activation`. L'empreinte du certificat debug correspond a `assetlinks.json`.

PostgreSQL 18.4 a ete sauvegarde dans
`runtime/merchant-portal-e2e/backups/before-validator-six-fixes-20260811-121740.dump`.
V7 Onboarding, V2 Connecteur et V8 Onboarding ont passe la premiere application
et le rejeu. Les comptages sont restes 16 dossiers, 0 outbox, 0 etat WAY4 et 0
allocation MID. Les controles donnent 0 doublon RegNumber/MID/idempotence, 0
orphelin et 0 dossier Onboarding sans exactement un principal actif.

Deux portes externes restent explicites : le commercant Acquiring actif
`885d1af8-2f05-465a-832a-6a91ae613da3` n'a toujours aucun PDV reel, et le DNS
public `portal.futurpayment.com` ne se resout pas encore. Aucun backfill fictif
ni import WAY4 n'a ete lance.

Le validateur a accorde le GO technique pour cloturer le developpement et
publier ce perimetre par commit/push selectif. Le NO-GO E2E reel reste
maintenu. Premier travail non termine apres publication : fourniture du PDV
reel et deploiement DNS/HTTPS de `assetlinks.json`, puis une seule recette E2E
apres levee de tous les prerequis WAY4/AURA et OAuth2.

Preuve detaillee :
`tests/merchant-onboarding/PROOF_OF_TEST_VALIDATOR_SIX_REMARKS_2026-08-11.md`.
