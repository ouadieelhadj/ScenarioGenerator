# Génération de transactions variées — à réintégrer dans le Test

> Sauvegarde de la logique de `GeneratorService` (supprimé avec le sous-système Campagne).
> Objectif futur : enrichir le paramétrage du **Test** pour générer des transactions
> variées (au lieu d'un message fixe répété), puis les jouer en **TPS** sur le load test.

## Idée cible
Le `config` JSON du Test accepte, par champ DE, soit une valeur **fixe**, soit une **règle de variation** :
```json
{
  "DE002_PAN":   {"binRange": "51-55", "panLength": 16},
  "DE004_AMOUNT":{"min": 1000, "max": 50000},
  "DE018_MCC":   {"fixed": "5999"},
  "DE022":       {"channel": "POS"}
}
```
À l'exécution, le moteur de charge résout chaque champ (fixe ou généré) par transaction.

## Logique de génération (extraite de GeneratorService)

### PAN (DE2)
- BIN précis (`bin.code`) ou plage "51-55" / "2221-2720" (tirage aléatoire dans la plage)
- complété à `panLength` (défaut 16) avec chiffres aléatoires
- **clé de Luhn** sur le dernier chiffre
- défaut sans BIN : préfixe 51-55

### Montant (DE4)
- aléatoire dans [amountMin, amountMax] (défauts 1000..50000 centimes)

### Processing code (DE3) selon txType
purchase=00, withdrawal=01, purchase_cashback=09, cash_disbursement=17,
refund=20, payment=28, balance_inquiry=30, transfer=40 → puis "+0000"

### POS entry mode (DE22) selon canal
ECOMMERCE/VAD=010, ATM=051, POS=051 (puce)

### POS condition (DE25) selon canal
ECOMMERCE/VAD=59, ATM=02, POS=00

### Dates/heures
DE7 transmission = MMddHHmmss ; DE12 = HHmmss ; DE13 = MMdd ; DE14 expiry = now + 1..4 ans (yyMM)

### DE43 (nom/ville/pays) = 40 car : [nom 22][ ][ville 13][ ][pays 3]
- noms : SHOP/STORE/MARKET/CAFE/RESTAURANT/HOTEL/STATION/PHARMA + numéro
- villes par pays : FR(PARIS,LYON,MARSEILLE,LILLE,NICE), BE, ES, DE
- pad à largeur fixe

### Autres
- DE32 acquirer = "00000111111"
- DE37 RRN = 12 chiffres aléatoires
- DE41 terminal = "TERM%04d" ; DE42 merchant = "MERCH%010d"
- DE49 currency : EUR=978, USD=840, GBP=826
- STAN : compteur séquentiel %06d (désormais centralisé dans DmasNetworkUtil)

### Sélection des champs
`campaign.selectedFields` (CSV "DE2,DE4,...") ; si vide → tous les champs activés
du catalogue `iso_field_catalog` (findByEnabledTrueOrderByDisplayOrderAsc).

## Tables de référence conservées (NON supprimées)
- `iso_field_catalog` (IsoFieldCatalog) — catalogue des champs ISO
- `bin_range` (BinRange) — plages de BIN
Ces deux entités/tables restent utiles pour la génération variée future.

## Réintégration suggérée
1. Étendre le parsing du `config` Test (LoadTestOrchestrationService) pour détecter
   les règles {min,max}/{binRange}/{fixed} par champ.
2. Porter les méthodes de génération ci-dessus (genPan+Luhn, genAmount, procCode,
   posEntry/Condition, genDe43...) dans un util réutilisable.
3. Le moteur de charge (acquéreur) génère chaque 0100 en résolvant les champs variables.
