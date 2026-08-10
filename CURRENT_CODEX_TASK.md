# Derniere tache Codex active

- Nom : `Developpement Portal Commercant`
- Projet : `D:\MoneyCore\ScenarioGenerator`
- Branche : `codex/AddingFrontendMerchantPortal`
- Document de reprise : `REPRISE_MERCHANT_PORTAL.md`
- Session : `019fd736-217b-7560-8fe4-44efbe97bcda`

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

## Reprise increment 1 du 10 aout 2026

- Erreur de validation a ne pas reproduire : la reussite des tests Java a ete
  presentee trop tot comme une validation de l'increment avant l'execution de
  la porte PostgreSQL sur les donnees reelles. Pour tout increment comportant
  une migration ou un backfill, aucun statut `VALIDE` et aucun passage au lot
  suivant ne doivent desormais etre annonces avant le preflight complet, la
  premiere application, le rejeu idempotent, les controles d'integrite metier
  et la conservation des donnees sur PostgreSQL reel.

- Modele juridique, profil legal, representants, beneficiaires, adresses et
  multi-PDV implementes dans Merchant Onboarding et Acquiring.
- API v2 ajoutee sans modifier le contrat v1 ; erreurs enrichies isolees en v2.
- Migrations additives V3 Merchant Onboarding et V4 Acquiring preparees.
- Porte ciblee : 15 tests, aucun echec.
- Non-regression : 121 tests, aucun echec.
- Preuve :
  `tests/merchant-onboarding/PROOF_OF_TEST_MERCHANT_INCREMENT_1_2026-08-10.md`.
- PostgreSQL 18 a ete demarre depuis `D:\MoneyCore\PostgreSQL\18`.
- V3 et V4 ont ete appliquees puis rejouees avec succes : 16 PDV Onboarding,
  16 mappings distincts, 0 doublon, empreintes historiques inchangees.
- Une anomalie reelle bloque encore la porte : le commercant Acquiring actif
  `885d1af8-2f05-465a-832a-6a91ae613da3` possede un contrat et un store mais
  aucun PDV. Aucune source ne permet un backfill sans inventer de donnees.
- Premier travail non termine : obtenir et appliquer la donnee metier reelle
  de ce PDV, puis rejouer le controle `exactement un principal actif`.
- Une nouvelle recette de bout en bout doit etre executee apres reconciliation,
  en incluant la non-regression Java et toute la porte PostgreSQL V3/V4. Une
  recette rejouee avant reconciliation ne peut que confirmer le `NO-GO`.
- L'utilisateur a ensuite autorise explicitement le developpement des
  increments 2 a 5 sans attendre la levee de la porte metier de l'increment 1.
  Cette autorisation de developper ne vaut pas validation ni GO formel.

## Etat des increments 2 a 5 au 10 aout 2026

- Increments 2 a 4 implementes : offres/produits/TPE/e-commerce multi-PDV,
  API v2, outbox transactionnelle OAuth2 Client Credentials, referentiels,
  tarification versionnee et Maker/Checker des derogations.
- Increment 5 implemente jusqu'a la porte d'integration : module
  `sg-way4-aura-connector`, mapping versionne, generation deterministe,
  validation contre le XSD WAY4 reel, API interne OAuth2 et outbox Acquiring
  avec bail/reprise apres crash. La soumission WAY4 reste fermee par defaut.
- Profil de test sans secret : `runtime/merchant-portal-e2e/.env`, ignore par
  Git ; les secrets DB restent charges depuis le runtime plateforme.
- Validation automatique consolidee : 134 tests, 0 echec, 0 erreur
  (`sg-common` 77, `sg-acquiring` 28, `sg-merchant-onboarding` 27,
  `sg-way4-aura-connector` 2).
- PostgreSQL reel : sauvegarde prise, premiere application reussie, deux
  replays complets reussis, 135 tables avant et 153 apres, aucune diminution,
  une seule table existante augmentee (`onboarding_reference_value`, 8 vers
  17), 18 nouvelles tables, aucun doublon d'idempotence.
- Defaut de rejeu V4 detecte puis corrige : le seed referentiel ne tente plus
  un INSERT lorsque la cle existe apres ajout des colonnes obligatoires V6.
- Blocages de recette positive encore reels : 1 commercant actif sans PDV
  principal, 1 anomalie Acquiring non reconciliee, 0 binding AURA actif,
  autorites MID/TID encore `UNDECIDED` et aucune configuration OAuth2 de
  recette presente dans les fichiers runtime inspectes.
- Aucun PDV, binding AURA, identifiant MID/TID ou secret fictif n'a ete cree.
  Le developpement est pret pour la recette ciblee, mais le verdict reste
  `NO-GO` pour une integration WAY4 positive et pour tout GO formel.
- Preuve :
  `tests/merchant-onboarding/PROOF_OF_TEST_MERCHANT_INCREMENTS_2_5_2026-08-10.md`.
