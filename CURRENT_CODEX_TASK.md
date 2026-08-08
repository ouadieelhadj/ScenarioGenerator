# Derniere tache Codex active

- Nom : `Merchant Portal Web et Mobile - parcours complet MVP`
- Projet : `D:\MoneyCore\ScenarioGenerator`
- Branche : `codex/AddingFrontendMerchantPortal`
- Document de reprise : `REPRISE_MERCHANT_PORTAL.md`

Ce fichier identifie le chantier. L'etat reel doit toujours etre reconstruit
depuis Git, les fichiers, les processus, les ports, les artefacts et les tests.

## Etat au 8 aout 2026

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
- La recette PostgreSQL/Acquiring precedente des trois canaux reste validee
  dans son guide du 7 aout. Elle n'a pas ete rejouee le 8 aout car les secrets
  `MERCHANT_E2E_DB_PASSWORD` et `MERCHANT_E2E_JWT_SECRET` n'etaient pas charges.
- Premier travail non termine : industrialisation production (authentification
  interservices, notification, GED/antivirus, referentiels/contrats, gateway
  HTTPS, signature release et recette appareils reels).
