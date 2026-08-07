# Derniere tache Codex active

- Nom : `Merchant Portal mobile - increment 1`
- Projet : `D:\MoneyCore\ScenarioGenerator`
- Branche : `codex/AddingFrontendMerchantPortal`
- Document de reprise : `REPRISE_MERCHANT_PORTAL.md`

Ce fichier sert uniquement a retrouver le chantier et son journal. L'etat reel
doit toujours etre reconstruit depuis Git, les fichiers, les processus, les
ports, les artefacts et les resultats de tests.

## Jalon courant

- Backend MVP termine et valide : 107 tests, 0 echec, deux E2E PostgreSQL
  immediat/batch vers Acquiring.
- Premier increment frontend implemente en reutilisant le socle Angular des
  deux frontends existants, sans dupliquer l'application.
- Cible dediee `merchant-portal-web`, port de developpement `4230` et API
  onboarding `8570`.
- Activation publique, connexion partagee, creation du prospect par le
  commercial, tableau de bord par role et lecture du squelette dossier.
- Workflow Maker/Checker existant reutilise ; controles frontend alignes sur
  les roles ou permissions du backend.
- Bundle isole : aucun composant Switch, SwitchLab, Visa, administration ou
  deploiement n'est embarque.
- Validation : builds Merchant Portal, Switch et SwitchLab reussis ; 3 E2E
  Chromium Merchant Portal passes ; JSON et `git diff --check` valides.
- Preuve : `tests/frontend/PROOF_OF_TEST_MERCHANT_PORTAL_INCREMENT_1_2026-08-07.md`.
- Premier travail non termine : enrichir le contrat de lecture/ecriture du
  dossier puis implementer l'assistant complet et les pieces KYC.
- Application mobile conservee pour le lot 2 selon le cadrage.
- Lot mobile demarre le 7 aout 2026 : Ionic `8.8.17` et Capacitor `8.5.0`
  ajoutes au workspace Angular existant.
- Cible `merchant-mobile` creee sur le port `4240`, avec activation, connexion,
  accueil par role, creation du prospect Commercial et lecture du dossier
  Commercant. Elle reutilise les API identite `8080` et onboarding `8570`.
- Build Angular mobile development : SUCCESS, sortie
  `sg-frontend/dist/merchant-mobile`.
- Projet natif Capacitor cree sous `sg-frontend/android` et synchronise.
- Premier increment mobile termine a 5/5 : SDK Android API 36 installe sous
  `E:\Android\Sdk`, cache Gradle sous `E:\Android\GradleCache` et APK debug
  genere.
- APK de preuve :
  `tests/frontend/artifacts/futurpayment-merchant-mobile-1.0-debug.apk` ;
  SHA-256 `6FF2231C952A97DDD41956F04E123A28DB7CEFEF691C9D12BE71C38B7A466E5A`.
- Validation : 3 E2E Chromium format mobile passes, Gradle
  `testDebugUnitTest` BUILD SUCCESS, signature v2 et bundle isole verifies.
- Limites : installation sur appareil reel non executee, intent filter du deep
  link externe et stockage Android Keystore encore a implementer.
- Preuve mobile :
  `tests/frontend/PROOF_OF_TEST_MERCHANT_MOBILE_INCREMENT_1_2026-08-07.md`.
- Recette integree finale des trois canaux terminee : Commercant Web,
  Commercial Web et Mobile, chacun avec Maker, revue KYC, Checker, JSON
  canonique batch et provisionnement reel Acquiring.
- Resultats : dossiers `ONB-29CBB112`, `ONB-405A108D`, `ONB-38832F25`, tous
  `PROVISIONED`, MID `100000000000004` a `100000000000006`, TID `10000002` a
  `10000007`.
- Guide et preuve :
  `tests/merchant-onboarding/GUIDE_UTILISATEUR_PROOF_OF_TEST_3_CANAUX_2026-08-07.md`.
- Non-regression finale du perimetre backend relancee apres cette recette :
  **107 tests, 0 echec, 0 erreur, 0 ignore**, `BUILD SUCCESS`.
- Le commit backend `edf4673` existe localement ; son push attend encore la
  confirmation explicite vers le remote GitHub affiche a l'utilisateur.
- Lire `REPRISE_MERCHANT_PORTAL.md` avant toute suite.
