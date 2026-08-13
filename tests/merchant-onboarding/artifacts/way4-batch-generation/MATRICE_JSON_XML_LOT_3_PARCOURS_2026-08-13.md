# Matrice JSON vers XML WAY4 - trois parcours Portal
Date : 13 aout 2026

## Applications produites

| Parcours | Source JSON | Application racine | Contrat commercant | Contrats TPE |
|---|---|---|---|---|
| Commercant Web | `merchant-web-canonical-acquiring.json` | `ONB-198B8A1C` | `ONB-198B8A1C-ACCOUNT` | `ONB-198B8A1C-TPE-001` |
| Commercial Web | `commercial-web-canonical-acquiring.json` | `ONB-D9DAE641` | `ONB-D9DAE641-ACCOUNT` | `ONB-D9DAE641-TPE-001` a `ONB-D9DAE641-TPE-002` |
| Mobile | `mobile-canonical-acquiring.json` | `ONB-64B09020` | `ONB-64B09020-ACCOUNT` | `ONB-64B09020-TPE-001` a `ONB-64B09020-TPE-003` |

Chaque racine possede aussi une application adresse suffixee `-ADDRESS`.

## Correspondance des champs

| JSON source | Regle | XML cible |
|---|---|---|
| `onboardingReference` | valeur stable, sans transformation | `Application/RegNumber` racine et prefixe des objets enfants |
| `registrationNumber` | reprise exacte | `Client/ClientInfo/RegNumber` |
| `legalName` | reprise exacte | `CompanyName` et `ContractName` du contrat commercant |
| `tradingName` | reprise exacte | `ShortName` et `CompanyTradeName` |
| `country=MA` | binding configurable `COUNTRY/MA` | `Country=MAR` |
| `settlementCurrency=504` | binding configurable `CURRENCY/504` | `Currency=MAD` et `DefaultCurr=MAD` |
| `mcc=5411` | binding configurable `MCC/5411` | `DeviceInfo/SIC=5411` |
| `outlet.code` | reprise exacte | prefixe de `ContractName` TPE |
| `outlet.name` | reprise exacte | `ContractName` TPE |
| `outlet.address` | seule adresse disponible, reprise sans rue inventee | `AddressLine1` et `DeviceRecord/Location` |
| `outlet.terminalCount` | expansion ordonnee | un contrat TPE par occurrence, suffixes `001..n` |
| `productId` | cle source des bindings configurables | produit compte et produit POS resolus hors du generateur |

## Valeurs par parcours

| Champ | Commercant Web | Commercial Web | Mobile |
|---|---|---|---|
| RC | `RC-merchant-web-5DC9528C` | `RC-commercial-web-27B851CF` | `RC-mobile-1C3919EE` |
| Raison sociale | `Commerce Commercant Web 5DC9528C` | `Commerce Commercial Web 27B851CF` | `Commerce Application Mobile 1C3919EE` |
| Nom commercial | `Boutique Commercant Web` | `Boutique Commercial Web` | `Boutique Application Mobile` |
| Code PDV | `OUT-5DC9528C` | `OUT-27B851CF` | `OUT-1C3919EE` |
| Nom PDV | `Point de vente Commercant Web` | `Point de vente Commercial Web` | `Point de vente Application Mobile` |
| Adresse disponible | `Casablanca` | `Casablanca` | `Casablanca` |
| Nombre de TPE | 1 | 2 | 3 |

## Donnees volontairement absentes

| Donnee | Traitement |
|---|---|
| Identifiant interne client WAY4 | non genere |
| Numero contrat commercant | `ContractIDT` absent |
| Numero contrat TPE / TID | `ContractIDT` et `ContractNumber` absents |
| MID | `MerchantID` absent tant qu'aucune plage WAY4 n'est approuvee |
| Adresse de rue non presente dans le JSON | aucune rue inventee ; la valeur disponible `Casablanca` est conservee |
| Modele TPE detaille non present dans le JSON | `DeviceType` provient du binding configurable en attente de validation AURA |

## Bindings

Le generateur ne porte pas directement les codes `MERCHANT`, `Commercial`,
`ACQACC`, `ACQ`, `STANDARD`, `POS` ou `PAYMENT`. Le chemin applicatif les
resout depuis `aura_binding`. Pour cette preuve hors import, ils sont charges
depuis `way4-bindings-pending-aura-validation.json`, marque
`validatedForImport=false`. La validation fonctionnelle restera bloquee tant
que les valeurs extraites de la base AURA de recette ne les auront pas
remplacees ou confirmees.
