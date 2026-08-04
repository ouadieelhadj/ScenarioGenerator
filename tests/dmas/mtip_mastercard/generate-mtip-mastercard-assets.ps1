param(
    [string]$TseRoot = 'C:\Users\Admin\AppData\Local\Temp\mtip_mastercard_codex_20260804\tse',
    [string]$OutputDirectory = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'

function Normalize-TseText([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return '' }
    return ($Value -replace '&cl', ':' -replace '&eq', '=' -replace '&cm', ',' -replace '<BR>', ' | ' -replace '\];\[Action=', ' | ' -replace '^\[Action=', '' -replace '\]$', '').Trim()
}

function Sql-Literal([string]$Value) {
    if ($null -eq $Value) { return "''" }
    return "'" + ($Value -replace "'", "''") + "'"
}

function Parse-CardApplications($CardNode) {
    $byApplication = [ordered]@{}
    foreach ($match in [regex]::Matches([string]$CardNode.tags, '\[Tag=(?<key>[^:]+):Value=(?<value>[^\]]*)\]')) {
        $key = $match.Groups['key'].Value
        $value = $match.Groups['value'].Value
        if ($key -match '^(Application_\d+)\.(.+)$') {
            $application = $Matches[1]
            $field = $Matches[2]
        } else {
            $application = 'Application_1'
            $field = $key
        }
        if (-not $byApplication.Contains($application)) { $byApplication[$application] = [ordered]@{} }
        $byApplication[$application][$field] = $value
    }
    foreach ($application in $byApplication.Keys) {
        $values = $byApplication[$application]
        if ($values.PAN) {
            [pscustomobject]@{
                Application = $application
                Brand = [string]$values.Brand
                PAN = [string]$values.PAN
                PIN = [string]$values.PIN
                CvmList = [string]$values.CVM_List
            }
        }
    }
}

$required = 'ruleset.xml', 'selected.xml', 'testrun.xml'
foreach ($name in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $TseRoot $name))) {
        throw "Fichier TSE requis absent: $(Join-Path $TseRoot $name)"
    }
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

[xml]$rules = Get-Content -LiteralPath (Join-Path $TseRoot 'ruleset.xml') -Raw
[xml]$selectedXml = Get-Content -LiteralPath (Join-Path $TseRoot 'selected.xml') -Raw
[xml]$run = Get-Content -LiteralPath (Join-Path $TseRoot 'testrun.xml') -Raw

# Preserve the EMV observations already extracted from the ISO 8583 logs.
$oldInventoryPath = Join-Path $OutputDirectory 'mtip_card_inventory.csv'
$observedByPan = @{}
if (Test-Path -LiteralPath $oldInventoryPath) {
    foreach ($row in Import-Csv -LiteralPath $oldInventoryPath) {
        # source_files is populated only for PANs actually observed in the ISO logs.
        # This prevents generated DMAS defaults from becoming "observed" on rerun.
        if ($row.pan -and $row.aid -and $row.source_files) { $observedByPan[$row.pan] = $row }
    }
}

$ruleTests = @{}
foreach ($node in $rules.SelectNodes('//Test')) { $ruleTests[[string]$node.name] = $node }
$cards = @{}
foreach ($node in $rules.SelectNodes('//card')) { $cards[[string]$node.id] = $node }

$resultSummary = @{}
foreach ($group in ($run.SelectNodes('//Results/Test') | Group-Object { [string]$_.TestName })) {
    $passes = @($group.Group | Where-Object { [string]$_.Result -ieq 'pass' }).Count
    $notTested = @($group.Group | Where-Object { [string]$_.Result -ieq 'not tested' }).Count
    $resultSummary[$group.Name] = [pscustomobject]@{ Checks = $group.Count; Passes = $passes; NotTested = $notTested }
}

$catalog = [System.Collections.Generic.List[object]]::new()
$inventoryByPan = [ordered]@{}
$selected = @($selectedXml.SelectNodes('//InScope/Test'))
foreach ($selection in $selected) {
    $testName = [string]$selection.name
    $test = $ruleTests[$testName]
    if (-not $test) { throw "Test sélectionné absent de ruleset.xml: $testName" }
    $cardIds = @([regex]::Matches([string]$test.cards, '\[Card=(?<id>[^\]]+)\]') | ForEach-Object { $_.Groups['id'].Value })
    if ($cardIds.Count -eq 0) { throw "Aucune carte associée au test: $testName" }
    foreach ($cardId in $cardIds) {
        $card = $cards[$cardId]
        if (-not $card) { throw "Définition de carte absente: $cardId" }
        foreach ($app in @(Parse-CardApplications $card)) {
            if (-not $app.PIN) { throw "PIN absent dans Card Details: $cardId / $($app.Application)" }
            $summary = $resultSummary[$testName]
            $catalog.Add([pscustomobject]@{
                test_name = $testName
                status = [string]$selection.status
                result_checks = if ($summary) { $summary.Checks } else { 0 }
                passed_checks = if ($summary) { $summary.Passes } else { 0 }
                not_tested_checks = if ($summary) { $summary.NotTested } else { 0 }
                card_id = $cardId
                application = $app.Application
                brand = $app.Brand
                pan = $app.PAN
                pin = $app.PIN
                cvm_list = $app.CvmList
                objective = Normalize-TseText ([string]$test.objective)
                actions = Normalize-TseText ([string]$test.actions)
                card_description = Normalize-TseText ([string]$card.description)
            })

            if (-not $inventoryByPan.Contains($app.PAN)) {
                $inventoryByPan[$app.PAN] = [ordered]@{
                    CardIds = [System.Collections.Generic.HashSet[string]]::new()
                    Applications = [System.Collections.Generic.HashSet[string]]::new()
                    Brands = [System.Collections.Generic.HashSet[string]]::new()
                    CvmLists = [System.Collections.Generic.HashSet[string]]::new()
                    Tests = [System.Collections.Generic.HashSet[string]]::new()
                    PAN = $app.PAN
                    PIN = $app.PIN
                }
            }
            $item = $inventoryByPan[$app.PAN]
            [void]$item.CardIds.Add($cardId)
            [void]$item.Applications.Add($app.Application)
            [void]$item.Brands.Add($app.Brand)
            [void]$item.CvmLists.Add($app.CvmList)
            [void]$item.Tests.Add($testName)
            if ($item.PIN -ne $app.PIN) { throw "PIN contradictoire pour le PAN $($app.PAN)" }
        }
    }
}

$inventory = foreach ($item in $inventoryByPan.Values) {
    $observed = $observedByPan[$item.PAN]
    $brandText = (@($item.Brands) -join '|')
    $isMastercard = $brandText -match 'Mastercard|MCD'
    $defaultAid = if ($isMastercard) { 'A0000000041010' } else { 'A0000000043060' }
    [pscustomobject]@{
        card_ids = (@($item.CardIds) | Sort-Object) -join '|'
        applications = (@($item.Applications) | Sort-Object) -join '|'
        brands = (@($item.Brands) | Sort-Object) -join '|'
        pan = $item.PAN
        pin = $item.PIN
        pin_origin = 'CARD_DETAILS'
        cvm_list = (@($item.CvmLists) | Sort-Object) -join '|'
        expiry = if ($observed.expiry) { $observed.expiry } else { '4912' }
        currency = if ($observed.currency) { $observed.currency } else { '504' }
        aid = if ($observed.aid) { $observed.aid } else { $defaultAid }
        aip = if ($observed.aip) { $observed.aip } else { '1B80' }
        psn = if ($observed.psn) { $observed.psn } else { '00' }
        observed_atc = if ($observed.observed_atc) { $observed.observed_atc } else { '' }
        app_version = if ($observed.app_version) { $observed.app_version } else { '0002' }
        iad = if ($observed.iad) { $observed.iad } else { '0110A0000000000000000000000000' }
        cvm_result = if ($observed.cvm) { $observed.cvm } elseif ($observed.cvm_result) { $observed.cvm_result } else { '010002' }
        emv_origin = if ($observed) { 'ISO_LOG' } else { 'DMAS_V4_DEFAULT' }
        selected_tests = (@($item.Tests) | Sort-Object) -join '|'
        source_files = if ($observed.source_files) { $observed.source_files } else { '' }
        notes = if ($observed) { $observed.notes } else { 'EXPIRY_AND_EMV_DEFAULTS' }
    }
}
$inventory = @($inventory | Sort-Object pan)
$catalog = @($catalog | Sort-Object test_name, card_id, application, pan)

$inventory | Export-Csv -LiteralPath $oldInventoryPath -NoTypeInformation -Encoding utf8
$catalog | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'mtip_test_cases.csv') -NoTypeInformation -Encoding utf8

$selectedNames = [System.Collections.Generic.HashSet[string]]::new()
foreach ($selection in $selected) { [void]$selectedNames.Add([string]$selection.name) }
$actualByCheck = @{}
foreach ($result in $run.SelectNodes('//Results/Test')) {
    $key = '{0}|{1}|{2}' -f [string]$result.TestName, [string]$result.CheckNo, [string]$result.StepNo
    $actualByCheck[$key] = $result
}
$expectedResults = foreach ($check in $rules.SelectNodes('//Check')) {
    $testName = [string]$check.Test
    if (-not $selectedNames.Contains($testName)) { continue }
    $key = '{0}|{1}|{2}' -f $testName, [string]$check.CheckNumber, [string]$check.StepOfCheck
    $actual = $actualByCheck[$key]
    [pscustomobject]@{
        test_name = $testName
        check_number = [string]$check.CheckNumber
        step = [string]$check.StepOfCheck
        mandatory = [string]$check.IsMandatory
        expected_result = Normalize-TseText ([string]$check.Commentary)
        data_item = [string]$check.DataItem
        data_item_name = [string]$check.DataItemName
        operator = [string]$check.Operator
        expected_value = [string]$check.Value
        action_if_true = [string]$check.ActionIfTrue
        action_if_false = [string]$check.ActionIfFalse
        actual_result = if ($actual) { [string]$actual.Result } else { 'not reported' }
        updated = if ($actual) { [string]$actual.Updated } else { '' }
    }
}
$expectedResults = @($expectedResults | Sort-Object test_name, @{Expression={ [int]$_.check_number }}, @{Expression={ [int]$_.step }})
$expectedResults | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'mtip_expected_results.csv') -NoTypeInformation -Encoding utf8

$valueLines = foreach ($row in $inventory) {
    '    (' + ((@($row.pan, $row.pin, $row.expiry, $row.currency, $row.aid, $row.aip, $row.psn, $row.app_version, $row.iad, $row.cvm_result) | ForEach-Object { Sql-Literal $_ }) -join ',') + ')'
}
$valuesSql = $valueLines -join ",`r`n"
$sql = @"
-- Mastercard MTIP / EMVCo L3 certification cards for mc_dmas_cards.
-- Source: EMVCoL3_04_20251113T160852664Z_260428-152429.tsez
-- Generated from Card Details (PAN/PIN/CVM) and ISO 8583 logs (EMV).
-- Certification/test data only. This script does not create or import an MDK.

BEGIN;

DO `$`$
BEGIN
    IF to_regclass('public.mc_dmas_cards') IS NULL THEN
        RAISE EXCEPTION 'Required table public.mc_dmas_cards does not exist';
    END IF;
END
`$`$;

WITH defaults(bank_code, balance) AS (
    VALUES ('022905'::varchar(6), 100000000::bigint)
), cards(
    pan, pin, expiry, currency, emv_aid, emv_aip, emv_psn,
    emv_app_version, emv_iad, emv_cvm_results
) AS (
    VALUES
$valuesSql
)
INSERT INTO public.mc_dmas_cards (
    pan, pin, balance, currency, expiry, status, created_at, updated_at,
    bank_code, emv_aid, emv_aip, emv_psn, emv_atc,
    emv_app_version, emv_iad, emv_cvm_results
)
SELECT
    c.pan, c.pin, d.balance, c.currency, c.expiry, 'ACTIVE', now(), now(),
    d.bank_code, c.emv_aid, c.emv_aip, c.emv_psn, 0,
    c.emv_app_version, c.emv_iad, c.emv_cvm_results
FROM cards c CROSS JOIN defaults d
ON CONFLICT (pan) DO UPDATE SET
    pin = EXCLUDED.pin,
    currency = EXCLUDED.currency,
    expiry = EXCLUDED.expiry,
    status = EXCLUDED.status,
    updated_at = now(),
    bank_code = EXCLUDED.bank_code,
    emv_aid = EXCLUDED.emv_aid,
    emv_aip = EXCLUDED.emv_aip,
    emv_psn = EXCLUDED.emv_psn,
    emv_app_version = EXCLUDED.emv_app_version,
    emv_iad = EXCLUDED.emv_iad,
    emv_cvm_results = EXCLUDED.emv_cvm_results;

COMMIT;
"@
Set-Content -LiteralPath (Join-Path $OutputDirectory '01_upsert_mtip_mastercard_cards.sql') -Value $sql -Encoding utf8

$panValues = ($inventory | ForEach-Object { '        (' + (Sql-Literal $_.pan) + ')' }) -join ",`r`n"
$verifySql = @"
-- Read-only verification. PANs are masked and PINs are never displayed.
WITH expected(pan) AS (
    VALUES
$panValues
), loaded AS (
    SELECT c.* FROM public.mc_dmas_cards c JOIN expected e USING (pan)
)
SELECT
    (SELECT count(*) FROM expected) AS expected_cards,
    count(*) AS loaded_cards,
    count(*) FILTER (WHERE status = 'ACTIVE') AS active_cards,
    count(*) FILTER (WHERE pin IS NULL OR pin = '') AS missing_pin,
    count(*) FILTER (WHERE expiry IS NULL OR length(expiry) <> 4) AS invalid_expiry,
    count(*) FILTER (WHERE currency IS NULL OR length(currency) <> 3) AS invalid_currency,
    count(*) FILTER (WHERE emv_aid IS NULL OR emv_aip IS NULL OR emv_psn IS NULL) AS missing_emv
FROM loaded;

WITH expected(pan) AS (
    VALUES
$panValues
)
SELECT left(e.pan, 6) || repeat('*', length(e.pan) - 10) || right(e.pan, 4) AS missing_pan
FROM expected e
LEFT JOIN public.mc_dmas_cards c USING (pan)
WHERE c.pan IS NULL
ORDER BY e.pan;
"@
Set-Content -LiteralPath (Join-Path $OutputDirectory '02_verify_mtip_mastercard_cards.sql') -Value $verifySql -Encoding utf8

$observedCount = @($inventory | Where-Object emv_origin -eq 'ISO_LOG').Count
$defaultCount = $inventory.Count - $observedCount
$readme = @'
# Cartes Mastercard MTIP pour DMAS

Ce répertoire prépare les données de certification du paquet
`EMVCoL3_04_20251113T160852664Z_260428-152429.tsez` pour `mc_dmas_cards`.

Le paquet contient **47 cas sélectionnés et réussis**, **36 définitions de carte**
utilisées par ces cas et **48 applications/PAN distincts**. Les PAN, PIN, marques
et listes CVM viennent de l'onglet **Card Details** de TSE. Les objectifs,
actions et résultats viennent des détails des cas de test.

## Fichiers

- `mtip_test_cases.csv` : catalogue des cas, actions, résultats attendus,
  cartes/applications et données Card Details ;
- `mtip_expected_results.csv` : détail des contrôles attendus, opérateurs,
  valeurs de référence et résultat réellement obtenu par le run ;
- `mtip_card_inventory.csv` : inventaire consolidé des 48 applications carte,
  avec l'origine des données EMV ;
- `01_upsert_mtip_mastercard_cards.sql` : upsert idempotent dans
  `public.mc_dmas_cards` ;
- `02_verify_mtip_mastercard_cards.sql` : contrôles en lecture seule, sans PIN
  et avec PAN masqués ;
- `load-mtip-mastercard-cards.sh` : exécution manuelle protégée par
  `MTIP_APPLY=YES` ;
- `generate-mtip-mastercard-assets.ps1` : régénération des artefacts depuis les
  XML extraits du paquet TSE.

## Origine et valeurs par défaut

- PIN : toujours lu dans **Card Details** ; aucun PIN inventé.
- EMV observé : {OBSERVED_COUNT} PAN enrichis à partir des messages ISO 8583 du paquet.
- EMV absent : {DEFAULT_COUNT} PAN utilisent explicitement les valeurs de repli de
  la migration DMAS V4 (`AIP=1B80`, `PSN=00`, version `0002`, IAD et CVM V4).
- Expiration absente : `4912`, valeur commune au jeu de certification.
- Devise absente : `504`.
- Solde : `100000000` unités mineures ; bank code : `022905`.
- `emv_atc` démarre à `0` en base ; l'ATC observé reste dans l'inventaire.

Le catalogue des résultats contient 484 contrôles définis par les règles :
465 sont `pass`, 4 sont `not tested` et 15 n'ont pas de ligne de résultat dans
ce run (`not reported`). Le statut global des 47 cas sélectionnés reste
`Passed`, conformément à `selected.xml`.

Le PAN finissant par `0026` conserve volontairement l'expiration observée
`1912`, car il sert à un scénario de carte expirée.

## Limite cryptographique

Le paquet contient des ARQC attendus mais pas la MDK claire permettant de les
recalculer. Le script ne crée donc aucune clé fictive. Pour valider réellement
les ARQC FETIAN, la MDK de certification correspondante doit être importée dans
`mc_dmas_mastercard_keys`, chiffrée sous la LMK locale via le HSM. Sans cette
MDK, les cartes sont chargeables mais une autorisation EMV peut être refusée
avec `RC05`.

## Exécution manuelle

```bash
cd /d/MoneyCore/ScenarioGenerator
export PGHOST=127.0.0.1
export PGPORT=5432
export PGDATABASE=scenariogenerator
export PGUSER=postgres
export PGPASSWORD='mot-de-passe-local'
export MTIP_APPLY=YES
./tests/dmas/MTIP_Mastercard/load-mtip-mastercard-cards.sh
```

Ne pas versionner `PGPASSWORD` et ne jamais utiliser ces données sur une base
de production.
'@
$readme = $readme.Replace('{OBSERVED_COUNT}', [string]$observedCount).Replace('{DEFAULT_COUNT}', [string]$defaultCount)
Set-Content -LiteralPath (Join-Path $OutputDirectory 'README.md') -Value $readme -Encoding utf8

Write-Host "Généré: $($inventory.Count) PAN, $($catalog.Count) associations test/carte/application, $($selected.Count) tests."
