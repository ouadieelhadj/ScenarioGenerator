# Matrice JSON → XML — candidat WAY4 CARSDB minimal

| Source ou règle | Valeur source | Destination XML | Valeur générée |
|---|---|---|---|
| `applicationRegNumber` | `ONB-198B8A1C` | Application client / `RegNumber` | `ONB-198B8A1C` |
| règle stable compte | racine du dossier | Contrat commerçant / `Application/RegNumber` | `ONB-198B8A1C-ACCOUNT` |
| règle stable adresse | racine du dossier | Adresse / `Application/RegNumber` | `ONB-198B8A1C-ADDRESS` |
| règle stable TPE | racine + ordinal 1 | Contrat TPE / `Application/RegNumber` | `ONB-198B8A1C-TPE-001` |
| `merchantType` | `RESIDENT` | Client / `ClientType` | `M_RES` |
| catégorie confirmée | commerçant | Client et contrats / `ClientCategory` | `Commercial` |
| `registrationNumber` | `RC-merchant-web-5DC9528C` | ClientInfo / `RegNumber` | identique |
| `legalName` | `Commerce Commercant Web 5DC9528C` | ClientInfo / `CompanyName` | identique |
| `tradingName` | `Boutique Commercant Web` | ClientInfo / `ShortName`, `CompanyTradeName` | identique |
| `country` | `MA` | adresses / `Country` | `MAR` |
| `settlement.currency` | `504` | contrats / `Currency` | `MAD` |
| `mcc` | `5411` | DeviceInfo / `SIC` | `5411` |
| produit contrat commerçant | mapping CARSDB | Product / `ProductCode1` | `AROUTLET` |
| schéma contrat commerçant | mapping CARSDB | Product / `AccountScheme` | `ARAS` |
| pack contrat commerçant | mapping CARSDB | Product / `ServicePack` | `CAA` |
| type d'adresse | mapping CARSDB | Address / `AddressType` | `OWS_PS` |
| produit TPE | mapping CARSDB | Product / `ProductCode1` | `ARPOS` |
| schéma TPE | mapping CARSDB | Product / `AccountScheme` | `ARAS` |
| pack TPE | mapping CARSDB | Product / `ServicePack` | `ARPOS-R-MAIN` |
| `modelCode` | `FEITIAN` | DeviceRecord / `DeviceType` | `FEITIAN_OW_NATIVE` |
| PDV `sourceOutletId` | `f8f61ca4-3bf0-3c43-9ec4-b941a6442693` | DeviceInfo / `MerchantID` | `990001000000001` |
| TPE `sourceRequestId` + ordinal | `14904900-9615-3bad-a19b-41a26d241348:1` | contrat TPE / `ContractIDT/ContractNumber` | `99000001` |
| dossier commerçant | `ONB-198B8A1C` | contrat commerçant / `ContractIDT/ContractNumber` | `LCAR00000001` |

Les identifiants internes WAY4 ne sont pas renseignés. Les trois numéros externes sont persistés par l'allocateur CARSDB et non calculés dans le générateur XML.
