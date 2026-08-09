# Derniere tache Codex active

- Nom : `Merchant Portal Web et Mobile - parcours complet MVP`
- Projet : `D:\MoneyCore\ScenarioGenerator`
- Branche : `codex/AddingFrontendMerchantPortal`
- Document de reprise : `REPRISE_MERCHANT_PORTAL.md`

Ce fichier identifie le chantier. L'etat reel doit toujours etre reconstruit
depuis Git, les fichiers, les processus, les ports, les artefacts et les tests.

## Etat au 9 aout 2026

- Regle permanente de campagne : effectuer le preflight complet des acces,
  secrets, services et donnees avant le premier test ; demander tout element
  manquant immediatement, jamais dans le bilan final. Ne pas consigner les
  valeurs secretes en clair.

- Web : activation, Commercial, dossier Commercant complet, upload documentaire
  binaire, KYC Back-office, Maker/Checker, provisioning immediat/batch et
  affichage MID/TID.
- Mobile : memes dossiers et API, activation, profils Commercant/Commercial,
  pieces camera/fichier, KYC/Maker, stockage de session chiffre par Android
  Keystore et captures bloquees par `FLAG_SECURE`.
- APK Wi-Fi :
  `tests/frontend/artifacts/futurpayment-merchant-mobile-wifi-192.168.1.86-debug.apk`,
  SHA-256 `9EAA9253D1CCE9BC5D3C14896450A44EAB3378CD5398C62B6BF55BA35A65E094`.
- Validation : 109 tests Maven, 5 E2E Web, 3 E2E Mobile et Gradle
  `testDebugUnitTest assembleDebug`, tous sans echec.
- Preuve :
  `tests/frontend/PROOF_OF_TEST_MERCHANT_PORTAL_COMPLETE_2026-08-08.md`.
- La recette PostgreSQL/Acquiring des trois canaux a ete rejouee le 9 aout :
  3/3 `PROVISIONED`, jobs `SUCCEEDED`, MID `100000000000010` a
  `100000000000012`, TID `10000014` a `10000019`.
- Dix captures de reussite ont ete generees par 5 tests Web et 3 tests Mobile
  et integrees au guide utilisateur/Proof of Test des trois canaux.
- Premier travail non termine : industrialisation production (authentification
  interservices, notification, GED/antivirus, referentiels/contrats, gateway
  HTTPS, signature release et recette appareils reels).
