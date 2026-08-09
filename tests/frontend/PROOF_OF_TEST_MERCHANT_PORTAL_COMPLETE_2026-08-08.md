# Proof of Test - Merchant Portal Web et Mobile MVP

Date d'execution : 8 aout 2026
Branche : `codex/AddingFrontendMerchantPortal`

## Perimetre valide

- activation du compte invite ;
- creation d'un prospect par le Commercial ;
- reprise et saisie du dossier par le Commercant sur Web et Mobile ;
- upload binaire PDF/JPEG/PNG, stockage opaque, empreinte SHA-256 et lecture
  autorisee des justificatifs ;
- soumission et revue KYC Back-office ;
- separation Maker/Checker, approbation ou rejet ;
- provisioning immediat ou batch et affichage des MID/TID Acquiring ;
- session mobile chiffree avec Android Keystore et protection `FLAG_SECURE`.

## Resultats automatiques

| Niveau | Resultat |
|---|---:|
| Maven `sg-common` | 77/77 |
| Maven `sg-deployment-core` | 6/6 |
| Maven `sg-generator-orchestrator` | 1/1 |
| Maven `sg-acquiring` | 17/17 |
| Maven `sg-merchant-onboarding` | 8/8 |
| Total backend | **109/109, 0 echec, 0 erreur, 0 ignore** |
| Playwright portail Web | **5/5** |
| Playwright interface Mobile | **3/3** |
| Android Gradle | **BUILD SUCCESSFUL** |

Commande backend :

```powershell
& 'D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd' `
  -o -nsu -f pom.xml `
  -pl sg-generator-orchestrator,sg-acquiring,sg-merchant-onboarding -am test `
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

Les builds Angular `merchant-portal-web --configuration development` et
`merchant-mobile --configuration wifi` sont termines sans erreur. Les suites
Playwright sont `merchant-portal-first-increment.spec.ts` et
`merchant-mobile-first-increment.spec.ts`.

## APK de recette Wi-Fi

- fichier : `artifacts/futurpayment-merchant-mobile-wifi-192.168.1.86-debug.apk` ;
- taille : `8 367 286` octets ;
- SHA-256 :
  `9EAA9253D1CCE9BC5D3C14896450A44EAB3378CD5398C62B6BF55BA35A65E094` ;
- API cible : PC de recette `192.168.1.86`, identite `8080`, onboarding `8570` ;
- nature : APK debug, non destine a la production.

## Recette integree Acquiring

Le harnais `tests/merchant-onboarding/run-three-channel-e2e.ps1` a deja valide
le 7 aout les trois canaux Commercant Web, Commercial Web et Mobile jusqu'au
JSON canonique et au provisioning Acquiring : 3/3 dossiers `PROVISIONED`, jobs
`SUCCEEDED`, MID et TID reels de la sandbox. La preuve detaillee est dans
`tests/merchant-onboarding/GUIDE_UTILISATEUR_PROOF_OF_TEST_3_CANAUX_2026-08-07.md`.

La recette a ete rejouee integralement le 9 aout avec les prerequis verifies
avant le premier test et une cle JWT aleatoire temporaire. Resultat : **3/3
PROVISIONED**, jobs `SUCCEEDED`, dossiers `ONB-198B8A1C`, `ONB-D9DAE641` et
`ONB-64B09020`, MID `100000000000010` a `100000000000012` et TID `10000014` a
`10000019`. Aucun secret n'a ete inscrit dans Git.

Pour une nouvelle relance, charger les variables dans le terminal sans les
inscrire dans Git puis executer :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  tests/merchant-onboarding/run-three-channel-e2e.ps1
```

## Captures d'ecran

Dix captures de reussite sont integrees au
`GUIDE_UTILISATEUR_PROOF_OF_TEST_3_CANAUX_2026-08-07.md` : activation Web et
Mobile, Commercial Web et Mobile, dossier Commercant Web et Mobile, KYC
Back-office, Checker, batch et resultat MID/TID. Les suites dediees donnent
**5/5 Web et 3/3 Mobile**.

## Ecarts restant avant conformite de production

1. authentifier Onboarding vers Acquiring par mTLS ou OAuth2 client
   credentials ; la recette sandbox desactive encore la securite Acquiring ;
2. raccorder l'envoi d'invitations et de complements a un fournisseur SMS ou
   e-mail ;
3. remplacer le stockage documentaire local par la GED cible avec antivirus,
   retention, suppression logique et journal d'acces ;
4. livrer les referentiels produits/MCC/modeles TPE, le multi-PDV avance, la
   tarification et les contrats/signatures prevus par le cadrage ;
5. exposer Web et API derriere une gateway HTTPS et remplacer la session Web
   sandbox par une session BFF/cookie HttpOnly ou une politique de jetons
   courts avec renouvellement/revocation ;
6. configurer le domaine de deep link Android verifie, signer l'APK/AAB avec
   la cle organisation et effectuer Appium/recette sur appareils reels ;
7. completer OpenAPI/client genere, arabe/RTL, accessibilite, charge et cas
   negatifs de securite.

Conclusion : le parcours MVP executable est valide automatiquement. Il est
pret pour recette fonctionnelle, mais les sept points ci-dessus restent des
conditions de conformite et d'exploitation en production.
