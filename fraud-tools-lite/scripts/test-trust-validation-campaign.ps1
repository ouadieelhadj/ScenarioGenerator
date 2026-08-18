$ErrorActionPreference = 'Stop'
$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$Python = Join-Path $Root 'runtime\venv-ml\Scripts\python.exe'
$Campaign = Join-Path $Root 'ai-lab\trust_validation_campaign.py'
$RowsPerBank = if ($env:FRAUD_TRUST_ROWS_PER_BANK) { [int]$env:FRAUD_TRUST_ROWS_PER_BANK } else { 100000 }

& $Python $Campaign --rows-per-bank $RowsPerBank
if ($LASTEXITCODE -ne 0) { throw "Trust Validation campaign failed with code $LASTEXITCODE." }
$Report = Get-Content -LiteralPath (Join-Path $Root 'evidence\trust-validation-campaign.json') -Raw | ConvertFrom-Json
if ($Report.status -ne 'TRUST_VALIDATION_LAB_OK') { throw 'Expected trust validation status is absent.' }
if ($Report.banks -ne 3 -or $Report.sectorsPerBank -ne 2) { throw 'Three banks and two sectors per bank are required.' }
if ($Report.totalRows -ne (3 * $RowsPerBank)) { throw "Expected $((3 * $RowsPerBank)) total rows, got $($Report.totalRows)." }
if ($Report.phases.Count -ne 3) { throw 'The three validation phases are required.' }
if (($Report.results | Where-Object { -not $_.blindLabelsKeptOutOfTraining -or -not $_.modelFrozenBeforeBlindTest }).Count) { throw 'Blind validation integrity failed.' }
foreach ($MemberId in @('MEMBER-OUADIE', 'MEMBER-TRESOR', 'MEMBER-SEDIK')) {
    $MemberResults = @($Report.results | Where-Object memberId -eq $MemberId)
    if ($MemberResults.Count -ne 2) { throw "$MemberId must contain exactly two sectors." }
    $Suspicious = ($MemberResults | ForEach-Object { $_.knownLabels.suspicious } | Measure-Object -Sum).Sum
    $ConfirmedFraud = ($MemberResults | ForEach-Object { $_.knownLabels.confirmedFraud } | Measure-Object -Sum).Sum
    if ($Suspicious -ne 100 -or $ConfirmedFraud -ne 5) {
        throw "$MemberId must contain exactly 100 suspicious operations and 5 confirmed frauds."
    }
}
$BlindFraudTotal = ($Report.results | ForEach-Object { $_.phase2TrustValidation.confirmedFraudTotal } | Measure-Object -Sum).Sum
$BlindFraudDetected = ($Report.results | ForEach-Object { $_.phase2TrustValidation.confirmedFraudDetected } | Measure-Object -Sum).Sum
if ($BlindFraudTotal -ne 6 -or $BlindFraudDetected -ne $BlindFraudTotal) { throw 'All six hidden confirmed frauds must be detected.' }
if (($Report.results | Where-Object { $_.phase1Baseline.recall -ne $_.phase2TrustValidation.recall }).Count -eq 0) { throw 'Trust validation must be measured separately from the initial baseline.' }
if (($Report.results | Where-Object { $_.phase3AdaptiveDiscovery.proposals.Count -ne 2 }).Count) { throw 'Two governed proposals are required for each bank and sector.' }
if (($Report.results | Where-Object { $_.phase3AdaptiveDiscovery.automaticActivation }).Count) { throw 'Adaptive proposals must never be automatically activated.' }
Write-Output "TRUST_VALIDATION_CAMPAIGN_OK Banks=$($Report.banks) Sectors=$($Report.sectorsPerBank) Rows=$($Report.totalRows) HiddenFraud=$BlindFraudDetected/$BlindFraudTotal"
