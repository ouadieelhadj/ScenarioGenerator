$ErrorActionPreference = 'Stop'
$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$Mlflow = Join-Path $Root 'runtime\venv-ml\Scripts\mlflow.exe'
$Database = 'sqlite:///' + ((Join-Path $Root 'data\mlflow.db') -replace '\\', '/')
$Artifacts = 'file:///' + ((Join-Path $Root 'data\mlflow-artifacts') -replace '\\', '/')
$Out = Join-Path $Root 'logs\mlflow-server.out.log'
$Err = Join-Path $Root 'logs\mlflow-server.err.log'

$existing = Get-NetTCPConnection -LocalPort 5000 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    Write-Output 'MLFLOW_LITE_ALREADY_RUNNING Port=5000'
    exit 0
}

$process = Start-Process -FilePath $Mlflow -ArgumentList @(
    'server', '--backend-store-uri', $Database, '--default-artifact-root', $Artifacts,
    '--host', '127.0.0.1', '--port', '5000', '--workers', '1'
) -WindowStyle Hidden -RedirectStandardOutput $Out -RedirectStandardError $Err -PassThru
$process.Id | Set-Content -LiteralPath (Join-Path $Root 'data\mlflow-server.pid')

for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 1
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5000/health' -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            Write-Output "MLFLOW_LITE_OK Port=5000 PID=$($process.Id)"
            exit 0
        }
    } catch { }
}
throw "MLflow n a pas demarre. Consulter $Err"
