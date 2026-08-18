$ErrorActionPreference = 'Stop'
$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$Python = Join-Path $Root 'runtime\venv-ml\Scripts\python.exe'
$Training = Join-Path $Root 'ai-lab\train_and_propose.py'

& $Python $Training
if ($LASTEXITCODE -ne 0) {
    throw "L'entrainement IA a echoue avec le code $LASTEXITCODE."
}

$Summary = Get-Content -LiteralPath (Join-Path $Root 'evidence\ai-training-summary.json') -Raw | ConvertFrom-Json
if ($Summary.status -ne 'AI_LAB_OK') { throw 'Le statut attendu AI_LAB_OK est absent.' }
if ($Summary.proposalCount -lt 1) { throw 'Aucune proposition de controle n a ete generee.' }
if ($Summary.productionClaimAllowed -ne $false) { throw 'Le laboratoire ne doit pas autoriser une revendication production.' }
Write-Output "AI_LITE_OK RunId=$($Summary.runId) Proposals=$($Summary.proposalCount)"
