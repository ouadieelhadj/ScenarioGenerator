# Proof of Test - Backend Portail d'Affiliation Commercant

Date : 7 aout 2026
Branche : `codex/AddingBackendPortal`
Base : PostgreSQL locale `scenariogenerator`

## Verdict

**BACKEND MVP VALIDE**

- build Maven agrege : `BUILD SUCCESS` ;
- 107 tests, 0 echec, 0 erreur, 0 ignore ;
- E2E immediat Onboarding vers Acquiring : OK ;
- E2E batch post-validation vers Acquiring : OK ;
- controles d'echec ferme sur produit invalide : OK.

## Regression automatisee

Commande executee :

```powershell
& 'D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd' `
  -o -nsu -f pom.xml `
  -pl sg-generator-orchestrator,sg-acquiring,sg-merchant-onboarding -am test `
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

Resultat final a 16:42 :

| Module | Tests | Echecs | Erreurs | Ignores |
|---|---:|---:|---:|---:|
| sg-common | 77 | 0 | 0 | 0 |
| sg-deployment-core | 6 | 0 | 0 | 0 |
| sg-generator-orchestrator | 1 | 0 | 0 | 0 |
| sg-acquiring | 17 | 0 | 0 | 0 |
| sg-merchant-onboarding | 6 | 0 | 0 | 0 |
| **Total** | **107** | **0** | **0** | **0** |

Temps Maven total : 2 min 38 s.

## E2E PostgreSQL et HTTP

Les appels utilisent des JWT sandbox signes avec des sujets et roles distincts
`COMMERCIAL`, `MERCHANT`, `BACK_OFFICE` et `CHECKER`. Aucun secret ni token
n'est reproduit dans cette preuve.

### Provisionnement immediat

| Preuve | Valeur |
|---|---|
| Dossier | `ONB-8FFF2B3F` |
| Case ID | `8fff2b3f-f081-4c85-8509-91cb87486beb` |
| KYC | `VALIDATED` |
| Statut final | `PROVISIONED` |
| Merchant ID | `9ced9853-d710-4a71-8354-a5092676464c` |
| MID | `100000000000000` |
| TID | `10000000` |
| Erreur | aucune |

### Provisionnement batch post-validation

| Preuve | Valeur |
|---|---|
| Dossier | `ONB-EAE21FF5` |
| Case ID | `eae21ff5-4f02-4fb2-9089-70a0f453b3d3` |
| KYC | `VALIDATED` |
| Mise en file | `QUEUED_FOR_PROVISIONING` |
| Job final | `SUCCEEDED` |
| Statut final | `PROVISIONED` |
| Merchant ID | `05933c68-22c7-454f-8874-bc3a06d4e428` |
| MID | `100000000000003` |
| TID | `10000001` |
| Erreur | aucune |

### Tests negatifs observes

Deux dossiers ont ete volontairement presentes avec un produit inconnu ou un
produit appartenant a un autre acquereur. Acquiring a refuse la creation et les
jobs sont restes traces en `PROVISIONING_FAILED`/`FAILED`. Aucun MID/TID fictif
n'a ete attribue.

## Migrations validees

- `sql/merchant-onboarding/V1__create_merchant_onboarding.sql` ;
- `sql/merchant-onboarding/V2__add_kyc_documents_and_rbac.sql` ;
- `sql/acquiring/V3__create_onboarding_provisioning.sql` ;
- `sql/21_merchant_identity_invitation.sql`.

## Limites explicites

- le backend identite deja actif sur 8080 n'a pas ete redemarre pendant l'E2E ;
  l'invitation/activation est validee par test automatise de service ;
- les JWT E2E sont des JWT sandbox signes, pas des tokens emis en direct par
  l'instance identite utilisateur ;
- GED/antivirus reel, notifications, multi-PDV avance, QR/SoftPOS et mobile
  restent hors de ce MVP.

## Processus apres test

Les instances temporaires Acquiring 8550 et Merchant Onboarding 8570 ont ete
arretees. Les processus utilisateur deja presents sur 8080, 8090, 4210 et 4220
n'ont pas ete modifies.
