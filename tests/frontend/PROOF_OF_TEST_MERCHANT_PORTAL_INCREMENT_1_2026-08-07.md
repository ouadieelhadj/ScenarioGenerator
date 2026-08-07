# Proof of Test - Merchant Portal frontend - Increment 1

Date : 7 aout 2026

Branche : `codex/AddingFrontendMerchantPortal`

## Perimetre valide

- cible Angular dediee `merchant-portal-web` ;
- activation publique du compte commercant invite ;
- creation du prospect par le Commercial via le contrat API reel ;
- tableau de bord commercant avec echec ferme si la lecture dossier manque ;
- maintien des frontends Switch et SwitchLab ;
- absence de composants monetiques dans le bundle Merchant Portal.

Les appels HTTP d'onboarding sont interceptes dans les E2E navigateur afin de
tester deterministiquement le frontend et son contrat sans modifier les
services utilisateur actifs. Ce document ne presente donc pas ces trois tests
comme un E2E integre backend/PostgreSQL.

## Builds

Depuis `sg-frontend` :

```bash
npm.cmd run build:merchant-portal -- --configuration development
npm.cmd run build:switch -- --configuration development
npm.cmd run build:switchlab -- --configuration development
```

Resultat : **3 builds SUCCESS**.

- Merchant Portal : total initial 1,71 MB ;
- Switch : total initial 1,72 MB ;
- SwitchLab : total initial 1,73 MB.

## Tests automatiques Chromium

```powershell
$env:SG_FRONTEND_DIST='dist/merchant-portal-web/browser'
$env:SG_FRONTEND_TEST_PORT='4237'
$env:E2E_REPORT_FOLDER='playwright-report-merchant-portal'
node tools/run-playwright.mjs e2e/merchant-portal-first-increment.spec.ts
```

Resultat : **3 passed en 5,9 s, 0 echec**.

| Test | Resultat |
|---|---|
| Activation depuis le lien public | PASS |
| Creation du prospect sans mot de passe par le Commercial | PASS |
| Tableau de bord ferme si `/me/dossier` est absent | PASS |

## Separation des produits

Une recherche a ete executee sur les fichiers JavaScript et source maps du
bundle `dist/merchant-portal-web/browser` pour les composants SwitchLab,
Switch Acquiring/Interfaces/Member, Visa/Clearing, administration et
deploiement.

Resultat : `MERCHANT_BUNDLE_ISOLATION_OK`.

Les seuls chunks fonctionnels Merchant identifies sont notamment : activation,
prospect, dashboard, dossier, login, workflow, aide et page interdite.

## Controles statiques

- parsing de `angular.json`, `package.json` et des traductions fr/en/es :
  `JSON_PARSE_OK` ;
- `git diff --check -- sg-frontend` : aucune anomalie.

## Limite explicite

L'assistant detaille du dossier et le televersement KYC ne sont pas declares
termines. Le contrat backend courant ne restitue pas encore tous les champs
persistes necessaires. Le frontend affiche donc un squelette en lecture et
reste ferme plutot que d'inventer des donnees ou des reponses backend.
