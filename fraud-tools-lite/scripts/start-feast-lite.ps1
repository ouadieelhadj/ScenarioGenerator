$ErrorActionPreference = 'Stop'
$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$Feast = Join-Path $Root 'runtime\venv-feast\Scripts\feast.exe'
$Repo = Join-Path $Root 'feature-repo'
$Out = Join-Path $Root 'logs\feast-server.out.log'
$Err = Join-Path $Root 'logs\feast-server.err.log'

$existing = Get-NetTCPConnection -LocalPort 6566 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    Write-Output 'FEAST_LITE_ALREADY_RUNNING Port=6566'
    exit 0
}

$process = Start-Process -FilePath $Feast -ArgumentList @('serve','--host','127.0.0.1','--port','6566','--workers','1') `
    -WorkingDirectory $Repo -WindowStyle Hidden -RedirectStandardOutput $Out -RedirectStandardError $Err -PassThru
$process.Id | Set-Content -LiteralPath (Join-Path $Root 'data\feast-server.pid')
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 1
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:6566/health' -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            Write-Output "FEAST_LITE_OK Port=6566 PID=$($process.Id)"
            exit 0
        }
    } catch { }
}
throw "Feast n a pas demarre. Consulter $Err"
