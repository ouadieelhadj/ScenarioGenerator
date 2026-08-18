$ErrorActionPreference = 'Stop'
$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$Mlflow = Join-Path $Root 'runtime\venv-ml\Scripts\mlflow.exe'
$VenvScripts = Join-Path $Root 'runtime\venv-ml\Scripts'
$env:Path = "$VenvScripts;$env:Path"
$Summary = Get-Content -LiteralPath (Join-Path $Root 'evidence\ai-training-summary.json') -Raw | ConvertFrom-Json
$env:MLFLOW_TRACKING_URI = $Summary.trackingUri
$Out = Join-Path $Root 'logs\mlflow-model.out.log'
$Err = Join-Path $Root 'logs\mlflow-model.err.log'

$existing = Get-NetTCPConnection -LocalPort 5001 -State Listen -ErrorAction SilentlyContinue
if (-not $existing) {
    $process = Start-Process -FilePath $Mlflow -ArgumentList @(
        'models', 'serve', '--model-uri', $Summary.modelUri, '--env-manager', 'local',
        '--host', '127.0.0.1', '--port', '5001'
    ) -WindowStyle Hidden -RedirectStandardOutput $Out -RedirectStandardError $Err -PassThru
    $process.Id | Set-Content -LiteralPath (Join-Path $Root 'data\mlflow-model-server.pid')
}

for ($attempt = 0; $attempt -lt 45; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process -and $process.HasExited) {
        throw "Le service de modele s est arrete. Consulter $Err"
    }
    try {
        $health = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5001/health' -TimeoutSec 2
        if ($health.StatusCode -eq 200) { break }
    } catch { }
}

$body = @'
{"dataframe_split":{"columns":["amount_deviation","attempts_last_hour","device_novelty","location_novelty","beneficiary_age_minutes","graph_group_size","behavioral_deviation","threat_intelligence_signal"],"data":[[4.8,9,0.82,0.71,5,14,0.86,1]]}}
'@
$response = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:5001/invocations' -ContentType 'application/json' -Body $body -TimeoutSec 15
if ($null -eq $response.predictions -or $response.predictions.Count -ne 1) {
    throw 'La reponse du service de modele ne contient pas une prediction unique.'
}
if ($null -eq $response.predictions[0].riskScore -or $null -eq $response.predictions[0].recommendedAction) {
    throw 'La prediction ne contient pas le score et l action recommendee.'
}
$proof = @{ status='MLFLOW_MODEL_SERVING_OK'; modelUri=$Summary.modelUri; response=$response } | ConvertTo-Json -Depth 8
$proof | Set-Content -LiteralPath (Join-Path $Root 'evidence\mlflow-model-serving.json') -Encoding utf8
Write-Output "MLFLOW_MODEL_SERVING_OK Score=$($response.predictions[0].riskScore) Action=$($response.predictions[0].recommendedAction)"
