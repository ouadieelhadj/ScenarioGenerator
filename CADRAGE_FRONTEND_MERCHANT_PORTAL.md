# Cadrage technique - Frontend Portail d'Affiliation Commercant

Date : 7 aout 2026

Statut : MVP Web/Mobile implemente et valide ; industrialisation production restante

Mise a jour du 8 aout 2026 : le parcours MVP Web et Mobile, les pieces KYC,
la revue Back-office, Maker/Checker, le provisioning immediat/batch, le
stockage Keystore Android et l'APK debug Wi-Fi sont implementes. La preuve et
les ecarts de production sont consolides dans
`tests/frontend/PROOF_OF_TEST_MERCHANT_PORTAL_COMPLETE_2026-08-08.md`.

## 1. Objet et lots

Ce document cadre les deux interfaces du parcours d'affiliation :

- **Lot 1 - Web** : portail Angular pour le commercant, le commercial, le
  Back-office et le Checker ;
- **Lot 2 - Mobile** : application Android livrable sous forme d'APK, avec
  profils Commercant et Commercial. Son architecture est preparee des le Lot 1,
  mais son code n'est pas realise dans le premier lot.

Les deux canaux utilisent le meme backend `sg-merchant-onboarding`, la meme
identite, le meme dossier, les memes regles KYC, le meme Maker/Checker et le
meme contrat de provisionnement Acquiring. L'application mobile ne doit jamais
dupliquer le moteur metier dans l'APK.

Source fonctionnelle : expression de besoin Merchant Portal, version 1.0,
49 pages, sous `documents/Portail Affiliation Commerçant/`.

## 2. Decisions d'architecture

### 2.1 Portail web

Le portail web devient une cible Angular dediee du workspace `sg-frontend` :

- nom de cible propose : `merchant-portal-web` ;
- port de developpement propose : `4230` ;
- sortie : `dist/merchant-portal-web` ;
- point d'entree : `src/main.merchant-portal.ts` ;
- routes : `src/app/app.routes.merchant-portal.ts` ;
- code produit : `MERCHANT_PORTAL` dans `PortalProductCode` ;
- composants Angular standalone, formulaires reactifs, PrimeNG 18 et theme
  ScenarioGenerator existant ;
- internationalisation preparee en francais et arabe, avec anglais optionnel ;
- interface responsive, utilisable sur ordinateur et tablette.

Le portail est separe physiquement de Switch et SwitchLab. Aucun composant
SwitchLab ne doit apparaitre dans son bundle, ses routes ou son menu.

### 2.2 Application mobile APK - Lot 2

La cible recommandee est **Ionic Angular avec Capacitor pour Android**. Ce choix
permet de conserver Angular/TypeScript et d'acceder aux fonctions natives sans
transformer le portail web en simple WebView non maitrisee.

Livrables mobiles prevus :

- APK debug installable pour la recette interne ;
- APK release signe pour une distribution controlee hors store, si retenue ;
- AAB signe pour une publication Google Play eventuelle ;
- empreinte SHA-256, version, notes de version et rapport de tests par binaire.

Le mobile reutilise les DTO, le client OpenAPI, les validateurs purs, les
traductions et les regles d'affichage partagees. Il possede ses propres ecrans
Ionic et ne reutilise pas directement les pages PrimeNG du Back-office web.

References de choix technique : documentation officielle Ionic Angular,
Capacitor et Android sur la generation de binaires signes.

### 2.3 Packages partages

Le Lot 1 doit preparer trois couches independantes de l'interface :

1. `merchant-onboarding-contracts` : DTO generes depuis OpenAPI ;
2. `merchant-onboarding-client` : appels HTTP, correlation et gestion d'erreur ;
3. `merchant-onboarding-rules` : validateurs sans dependance Angular DOM.

Le web et le mobile consommeront ces packages. Aucun secret, token, fichier KYC
ou etat de session ne doit se trouver dans une bibliotheque partagee.

## 3. Canaux et profils

| Canal | Profil | Responsabilite |
|---|---|---|
| Web Lot 1 | Commercial | Creer le prospect et l'invitation, suivre son portefeuille |
| Web Lot 1 | Commercant | Activer son compte, saisir, documenter et soumettre son dossier |
| Web Lot 1 | Back-office | Controler les pieces, demander des complements, statuer sur le KYC |
| Web Lot 1 | Checker | Approuver ou rejeter, puis choisir le provisionnement autorise |
| Web Lot 1 | Operateur batch | Visualiser les dossiers approuves, lancer/reprendre un lot |
| Mobile Lot 2 | Commercant | Realiser le meme auto-onboarding depuis Android |
| Mobile Lot 2 | Commercial | Creer/accompagner un prospect et saisir un dossier habilite |

Le batch n'est pas un canal d'onboarding. Il intervient uniquement apres KYC et
approbation Maker/Checker.

## 4. Parcours fonctionnels

### 4.1 Commercial - Web Lot 1

1. Connexion avec le role `COMMERCIAL`.
2. Consultation de son portefeuille de prospects.
3. Creation du prospect : login, email et acquereur.
4. Generation de l'invitation d'identite.
5. Transmission du lien d'activation par un canal de notification securise.
6. Suivi du dossier, relance et lecture de son statut.
7. Accompagnement du commercant uniquement dans le perimetre habilite.

### 4.2 Commercant - Web Lot 1

1. Ouverture du lien d'activation et definition du mot de passe.
2. Connexion avec le role `MERCHANT`.
3. Reprise d'un brouillon existant.
4. Saisie guidee : identite, entreprise, activite, MCC, compte de reglement,
   point de vente, produits et nombre de terminaux.
5. Depot des pieces obligatoires et remplacement versionne si necessaire.
6. Controle de completude et recapitulatif.
7. Soumission KYC.
8. Traitement des complements demandes.
9. Soumission Maker/Checker apres validation KYC.
10. Consultation du statut jusqu'au resultat Acquiring et affichage du MID/TID
    uniquement lorsqu'ils sont retournes par Acquiring.

### 4.3 Back-office et Checker - Web Lot 1

1. Consultation de la file des dossiers a traiter.
2. Ouverture du dossier et de ses pieces depuis les routes de revue dediees.
3. Acceptation ou rejet motive de chaque piece.
4. Validation KYC, rejet ou demande de complements.
5. Consultation de la demande dans `Mes validations`.
6. Approbation ou rejet en respectant la separation Maker/Checker.
7. Provisionnement immediat ou mise en file batch selon l'habilitation.

### 4.4 Mobile Android - Lot 2

Le mobile doit offrir deux espaces determines par le JWT :

- **Commercant** : activation par deep link, saisie guidee, prise de photo ou
  selection de documents, complements, soumission et suivi ;
- **Commercial** : creation du prospect, invitation, saisie/accompagnement et
  suivi du portefeuille autorise.

Le Back-office lourd, l'administration et l'execution des batches restent sur
le web au premier increment mobile. Une validation Checker mobile pourra etre
ajoutee ulterieurement apres une analyse de risque et une authentification
renforcee.

## 5. Ecrans et routes Web Lot 1

| Route proposee | Ecran | Profils |
|---|---|---|
| `/activation` | Activation de l'invitation | Public avec token |
| `/login` | Authentification | Tous |
| `/merchant/dashboard` | Avancement, complements, resultat | Commercant |
| `/merchant/dossier/:id/identity` | Identite et type de commercant | Commercant |
| `/merchant/dossier/:id/business` | Activite, MCC, reglement | Commercant |
| `/merchant/dossier/:id/outlets` | PDV et chaine | Commercant/Commercial |
| `/merchant/dossier/:id/products` | TPE/e-commerce/QR/SoftPOS | Commercant/Commercial |
| `/merchant/dossier/:id/documents` | Pieces et versions | Commercant |
| `/merchant/dossier/:id/review` | Recapitulatif et soumission | Commercant |
| `/commercial/prospects` | Portefeuille et filtres | Commercial |
| `/commercial/prospects/new` | Creation/invitation | Commercial |
| `/backoffice/onboarding` | File KYC | Back-office/Checker |
| `/backoffice/onboarding/:id` | Revue dossier/pieces | Back-office/Checker |
| `/workflow/my-operations` | Demandes du Maker | Profils habilites |
| `/workflow/my-approvals` | Approbations/rejets | Checker |
| `/backoffice/onboarding/batches` | Lots, resultats et reprise | Back-office/Admin |

Les ecrans non couverts par une API reelle restent fermes avec un message
explicite. Le frontend ne genere aucune liste, decision KYC, approbation,
reference documentaire, MID ou TID fictif.

## 6. Raccordement aux API existantes

Base identite actuelle : REST 8080.
Base Merchant Onboarding actuelle : REST 8570.
Base Acquiring : appelee uniquement par le backend onboarding.

| Action frontend | API disponible |
|---|---|
| Connexion | `POST /auth/login` |
| Activation commercant | `POST /auth/merchant-invitations/activate` |
| Creation prospect/invitation | `POST /api/merchant-onboarding/v1/prospects` |
| Lien identite administratif | `POST /api/merchant-onboarding/v1/accounts/{id}/identity-link` |
| Lecture/modification dossier | `GET/PUT /api/merchant-onboarding/v1/dossiers/{id}` |
| Ajout/liste des metadonnees de pieces | `POST/GET /api/merchant-onboarding/v1/dossiers/{id}/documents` |
| Soumission KYC | `POST /api/merchant-onboarding/v1/dossiers/{id}/kyc/submit` |
| Revue d'une piece | `POST /api/merchant-onboarding/v1/documents/{id}/review` |
| Dossier/pieces pour controle | `GET /api/merchant-onboarding/v1/review/dossiers/{id}[/documents]` |
| Complements/validation/rejet KYC | `POST .../kyc/complements`, `validate`, `reject` |
| Soumission Maker | `POST /api/merchant-onboarding/v1/dossiers/{id}/submit` |
| Files workflow | `GET /api/workflow/requests/mine`, `approvals/mine` |
| Approbation/rejet Checker | `POST /api/workflow/approvals/{id}/approve|reject` |
| Provisionnement | `POST /api/merchant-onboarding/v1/dossiers/{id}/provision?mode=...` |
| Export/lancement batch | `GET .../batches/pending`, `POST .../batches/run` |

Le navigateur ne doit pas appeler plusieurs ports techniques en production. Le
deploiement cible doit exposer identite et onboarding derriere un BFF/gateway
HTTPS commun, avec routage par chemin et propagation controlee du JWT et du
`X-Correlation-ID`.

## 7. Complements backend requis avant les ecrans complets

Les API suivantes ne sont pas encore presentes et constituent des prerequis de
developpement, pas des donnees a simuler dans le frontend :

1. listes paginees et filtrees des prospects/dossiers par portefeuille et role ;
2. endpoint `me` permettant au commercant de retrouver son dossier sans UUID
   transmis par le navigateur ;
3. stockage documentaire reel : upload binaire ou URL signee, lecture securisee,
   antivirus, suppression logique et journal d'acces ;
4. referentiels type de commercant, pays, MCC, acquereurs, produits, modeles TPE,
   agences, mandataires et champs/pieces dynamiques ;
5. CRUD multi-PDV/chaine distinct du PDV unique du MVP ;
6. tarification, exceptions et formules ;
7. generation, telechargement, signature et upload des contrats TPE,
   e-commerce et CRC ;
8. historique/audit du dossier et commentaires ;
9. notifications d'invitation, complements et changements de statut ;
10. pagination et detail des jobs batch, rapport de rejets et reprise ciblee ;
11. mot de passe oublie, renouvellement et revocation de sessions ;
12. specification OpenAPI versionnee pour generer les clients Web et Mobile.

Le Lot 1 doit commencer par les ecrans raccordables au backend actuel, puis
ouvrir les fonctions avancees uniquement apres livraison de ces contrats.

## 8. Securite et donnees sensibles

### 8.1 Web

- HTTPS obligatoire hors poste local ;
- guards par role et permission, sans considerer le masquage d'un bouton comme
  une autorisation ;
- interdiction de mettre PAN, PIN, cle, token d'activation ou document KYC dans
  les logs, URL, analytics ou erreurs ;
- le stockage actuel du JWT dans `localStorage` est reserve au sandbox. La cible
  doit utiliser une session BFF par cookie `HttpOnly`, `Secure`, `SameSite`, ou
  des access tokens courts avec renouvellement protege ;
- CSP, protection XSS/CSRF selon le mode de session, validation stricte des
  fichiers et correlation de bout en bout ;
- aucune piece KYC en Base64 dans le JSON metier.

### 8.2 Mobile APK

- token conserve dans le stockage securise adosse a Android Keystore, jamais
  dans Preferences/localStorage en clair ;
- deep links d'activation a usage unique, controles par domaine ;
- capture d'ecran et apercu dans les applications recentes a interdire sur les
  pages contenant des pieces sensibles si la politique le demande ;
- pas de conservation durable des photos KYC dans la galerie ;
- nettoyage des fichiers temporaires apres upload ;
- biometrie uniquement pour deverrouiller une session locale protegee, jamais
  comme remplacement de l'identite serveur ;
- certificate pinning a evaluer avec une strategie de rotation ;
- detection root/hooking comme signal de risque, sans decision silencieuse ;
- permissions minimales : camera et fichiers uniquement au moment du besoin.

## 9. UX, accessibilite et responsive

- assistant de saisie avec sauvegarde explicite et barre d'avancement ;
- resume des erreurs par etape et focus sur le premier champ invalide ;
- statut du dossier traduit en langage metier, avec horodatage ;
- demande de complements affichant le motif et les pieces concernees ;
- tailles tactiles, contraste WCAG AA, navigation clavier et lecteurs d'ecran ;
- support des textes longs et preparation RTL pour l'arabe ;
- aucune couleur seule pour communiquer une decision KYC ou un rejet ;
- mode reseau lent avec reprise des appels idempotents et sans double soumission.

Le mobile reste **online-first**. Un brouillon local chiffre limite pourra etre
etudie, mais aucune photo KYC ni decision Maker/Checker ne sera disponible hors
ligne dans le premier APK.

## 10. Strategie de tests

### 10.1 Web Lot 1

- tests unitaires des validateurs, stores, services et guards ;
- tests de contrats HTTP a partir d'OpenAPI ;
- tests composants des formulaires et matrices de champs ;
- Playwright par profil : Commercial, Commercant, Back-office et Checker ;
- test Maker different du Checker ;
- test complements puis nouvelle version d'une piece ;
- E2E provisionnement immediat et batch jusqu'au MID/TID retourne ;
- tests 401/403, expiration JWT, double clic, rejeu, erreurs 4xx/5xx ;
- tests responsive, clavier, contraste et traductions ;
- controle des bundles pour garantir la separation Switch/SwitchLab/Portal.

### 10.2 Mobile APK Lot 2

- tests unitaires et composants Ionic ;
- tests Web des parcours partages avant empaquetage ;
- tests Appium sur emulateur et appareils Android reels ;
- activation deep link, camera, selection de fichier, perte reseau et reprise ;
- verification qu'aucun document sensible ne reste dans les caches ;
- test installation/mise a jour APK, signature, versionCode/versionName ;
- test du binaire release avec backend de recette et preuve horodatee.

Chaque lot produit un Proof of Test versionne avec versions frontend/backend,
commit, environnement, cas executes, captures utiles et anomalies restantes.

## 11. Decoupage de realisation

### Lot 1 - Portail web

1. **Fondations** : cible Angular, produit/menu/routes, base URL onboarding,
   modeles generes, permissions et theme.
2. **Identite et commercial** : activation, connexion, creation/invitation du
   prospect et suivi minimal.
3. **Dossier commercant** : assistant, sauvegarde, recapitulatif et statuts.
4. **Documents et KYC** : upload reel, versions, revue et complements.
5. **Maker/Checker** : extension de l'inbox existante, detail, approbation/rejet.
6. **Provisionnement** : immediat, batch, resultats MID/TID et reprise.
7. **Industrialisation** : traductions, accessibilite, securite, E2E et preuve.

### Lot 2 - Application mobile Android/APK

1. **Socle mobile** : Ionic Angular, Capacitor, environnements, stockage
   securise, client API partage et pipeline Android.
2. **Activation et authentification** : deep links, session, deconnexion et
   verrouillage local optionnel.
3. **Parcours Commercant** : dossier, pieces camera/fichiers, complements et
   suivi.
4. **Parcours Commercial** : prospect, invitation, accompagnement et
   portefeuille habilite.
5. **Fonctions natives** : push, appareil photo, gestion des fichiers et
   securite du cache.
6. **Livraison** : APK/AAB signes, recette appareils, durcissement et Proof of
   Test mobile.

## 12. Definition of Done

Le Lot 1 est termine lorsque :

- tous les ecrans MVP consomment des API reelles ;
- le commercant peut activer son compte, saisir, documenter et soumettre ;
- le Back-office traite le KYC et les complements ;
- le Checker approuve/rejette via le Maker/Checker existant ;
- les modes immediat et batch affichent le resultat Acquiring reel ;
- les tests unitaires, contrats, Playwright et E2E sont verts ;
- la separation des bundles est prouvee ;
- le Proof of Test et le guide de demarrage sont livres.

Le Lot 2 est termine lorsque les deux profils mobiles fonctionnent sur Android,
que l'APK release est signe et installe sur des appareils de recette, que les
donnees sensibles sont protegees et que le meme dossier peut etre commence sur
un canal puis repris sur l'autre sans divergence.

## 13. Premier increment a developper

Le premier increment frontend recommande est :

1. ajouter la cible `merchant-portal-web` sans modifier les bundles Switch et
   SwitchLab ;
2. ajouter la configuration gateway/onboarding et les DTO du backend actuel ;
3. implementer activation, connexion et creation du prospect ;
4. implementer le squelette du tableau de bord et de l'assistant dossier ;
5. ajouter les premiers tests unitaires et Playwright de separation.

Le code mobile ne commence pas dans ce premier increment, mais les contrats et
validateurs crees doivent etre compatibles avec son futur client Ionic Angular.

## 14. References techniques officielles

- [Ionic Angular](https://ionicframework.com/angular) ;
- [Capacitor](https://capacitorjs.com/docs) ;
- [Android - generer un APK ou un bundle signe](https://developer.android.com/build/build-for-release) ;
- [Android App Bundle](https://developer.android.com/guide/app-bundle).
