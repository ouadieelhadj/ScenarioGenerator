# Proof of Test - Merchant Portal mobile - Increment 1

Date : 7 aout 2026

Branche : `codex/AddingFrontendMerchantPortal`

## Binaire produit

- fichier : `tests/frontend/artifacts/futurpayment-merchant-mobile-1.0-debug.apk` ;
- taille : 6 791 433 octets ;
- SHA-256 : `6FF2231C952A97DDD41956F04E123A28DB7CEFEF691C9D12BE71C38B7A466E5A` ;
- package : `com.moneycore.merchantportal` ;
- label : `FuturPayment Merchant` ;
- versionCode : `1` ;
- versionName : `1.0` ;
- minSdk : `24` ;
- targetSdk/compileSdk : `36`.

`apksigner` confirme une signature Android Debug valide par le schema APK v2.
Ce binaire est destine a la recette interne et n'est pas un APK release signe
par l'organisation.

## Environnement de construction

- Angular 18.2 ;
- Ionic Angular 8.8.17 ;
- Capacitor 8.5.0 ;
- JDK 21 sous `D:\MoneyCore\jdk-21.0.11` ;
- SDK Android, API 36 et Build Tools 36 sous `E:\Android\Sdk` ;
- cache Gradle sous `E:\Android\GradleCache` ;
- Gradle limite a 768 Mo et un worker.

## Builds et tests

### Build Angular mobile

```bash
npm.cmd run build:merchant-mobile -- --configuration development
```

Resultat : SUCCESS. Sortie `dist/merchant-mobile/browser`.

### Synchronisation Capacitor et APK

Les assets ont ete synchronises dans le projet `sg-frontend/android`, puis
`assembleDebug` a produit `app-debug.apk`. Un second passage hors ligne a
confirme `:app:assembleDebug UP-TO-DATE`.

### Tests Android Gradle

```powershell
./gradlew.bat --no-daemon testDebugUnitTest
```

Resultat final : **BUILD SUCCESSFUL en 33 s**.

Le premier passage force en mode offline a seulement signale l'absence de
JUnit 4.13.2 dans le cache. La dependance a ensuite ete telechargee et le test
a passe ; cet incident etait un manque de dependance locale, pas un echec de
compilation ou de comportement.

### Parcours automatiques mobiles

```powershell
$env:SG_FRONTEND_DIST='dist/merchant-mobile/browser'
$env:SG_FRONTEND_TEST_PORT='4247'
node tools/run-playwright.mjs e2e/merchant-mobile-first-increment.spec.ts
```

Viewport : 390 x 844. Resultat final : **3 passed en 7,2 s**.

| Parcours | Resultat |
|---|---|
| Activation du compte depuis la route mobile | PASS |
| Creation du prospect par le Commercial | PASS |
| Consultation du dossier partage par le Commercant | PASS |

Les API sont interceptees au niveau navigateur pour valider les ecrans et les
contrats de facon deterministe. Ces tests ne sont donc pas presentes comme un
E2E integre avec les backends et PostgreSQL.

### Non-regression et isolation

- build `merchant-portal-web` : SUCCESS ;
- recherche de chunks Switch, SwitchLab, Visa, administration et deploiement
  dans le bundle mobile : `MOBILE_BUNDLE_ISOLATION_OK` ;
- parsing `angular.json`, `package.json` et `package-lock.json` :
  `JSON_PARSE_OK` ;
- `git diff --check` sur le perimetre mobile : aucune anomalie.

## Securite et limites

- aucun nouveau backend mobile : memes API identite `8080` et onboarding
  `8570` que le portail web ;
- aucun token ecrit dans `localStorage` : session memoire uniquement ;
- aucun secret ni document KYC embarque dans le binaire ;
- l'installation sur un appareil Android reel n'a pas ete executee dans cette
  session ;
- le stockage persistant Android Keystore reste a raccorder ;
- l'intent filter du deep link externe attend le domaine final ;
- camera, choix de fichiers et televersement KYC attendent le contrat backend
  detaille et feront partie du prochain increment.
