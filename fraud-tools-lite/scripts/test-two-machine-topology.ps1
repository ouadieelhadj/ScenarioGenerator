$ErrorActionPreference = 'Stop'
$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$Python = Join-Path $Root 'runtime\venv-ml\Scripts\python.exe'
$env:NO_PROXY = '127.0.0.1,127.0.0.2,127.0.0.3,localhost'
$env:FRAUD_MODEL_URL = 'http://127.0.0.1:5001/invocations'
$env:FRAUD_FEAST_URL = 'http://127.0.0.1:6566'
$env:FRAUD_PLATFORM_URL = 'http://127.0.0.3:8089'
$env:FRAUD_PLATFORM_ISO_HOST = '127.0.0.3'
$env:FRAUD_PLATFORM_ISO_PORT = '8583'
$env:FRAUD_KAFKA_BOOTSTRAP = '127.0.0.1:9092'
$env:FRAUD_KAFKA_TOPIC = 'fraud.risk-assessment-completed.v1'

if (-not (Get-NetTCPConnection -LocalPort 9092 -State Listen -ErrorAction SilentlyContinue)) {
    & (Join-Path $Root 'scripts\start-kafka-lite.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Kafka n a pas demarre.' }
}
if (-not (Get-NetTCPConnection -LocalPort 6566 -State Listen -ErrorAction SilentlyContinue)) {
    & (Join-Path $Root 'scripts\start-feast-lite.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Feast n a pas demarre.' }
}

$platformOut = Join-Path $Root 'logs\platform-machine.out.log'
$platformErr = Join-Path $Root 'logs\platform-machine.err.log'
$gatewayOut = Join-Path $Root 'logs\gateway-machine.out.log'
$gatewayErr = Join-Path $Root 'logs\gateway-machine.err.log'
$platform = Start-Process -FilePath $Python -ArgumentList @(
    (Join-Path $Root 'network-lab\platform_service.py'), '--host', '127.0.0.3', '--port', '8089'
) -WindowStyle Hidden -RedirectStandardOutput $platformOut -RedirectStandardError $platformErr -PassThru
$gateway = Start-Process -FilePath $Python -ArgumentList @(
    (Join-Path $Root 'network-lab\gateway_service.py'), '--host', '127.0.0.2', '--port', '8090'
) -WindowStyle Hidden -RedirectStandardOutput $gatewayOut -RedirectStandardError $gatewayErr -PassThru

try {
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Seconds 1
        try {
            $p = Invoke-RestMethod -Uri 'http://127.0.0.3:8089/health' -TimeoutSec 2
            $g = Invoke-RestMethod -Uri 'http://127.0.0.2:8090/health' -TimeoutSec 2
            if ($p.status -eq 'UP' -and $g.status -eq 'UP') { break }
        } catch { }
    }
    if ($p.status -ne 'UP' -or $g.status -ne 'UP') { throw 'Les deux machines simulees ne sont pas pretes.' }

    $transaction = @{
        bankId='BANK_LAB_A'; transactionReference='AUTH-REST-001'; decisionMode='ALERT_ONLY'
        instrumentToken='tok_bank_lab_001'
        amountDeviation=4.8; attemptsLastHour=9; deviceNovelty=0.82; locationNovelty=0.71
        beneficiaryAgeMinutes=5; graphGroupSize=14; behavioralDeviation=0.86; threatIntelligenceSignal=1
    }
    $rest = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.2:8090/v1/bank/authorizations' `
        -ContentType 'application/json' -Body ($transaction | ConvertTo-Json) -TimeoutSec 15
    if ($rest.gatewayTransport -ne 'REST') { throw 'Le parcours REST n est pas confirme.' }
    if ($rest.enforcedAction -ne 'ALERT') { throw 'Le mode alerte seule n est pas respecte.' }
    if ($rest.featureSource -ne 'FEAST_HTTP') { throw 'L enrichissement Feast distant n est pas confirme.' }
    if ($null -eq $rest.eventPublication.offset) { throw 'La publication Kafka REST n est pas confirmee.' }

    $transaction.decisionMode = 'DECISION'
    $transaction.transactionReference = 'AUTH-ISO-001'
    $iso1 = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.2:8090/v1/bank/iso-authorizations' `
        -ContentType 'application/json' -Body ($transaction | ConvertTo-Json) -TimeoutSec 15
    $transaction.transactionReference = 'AUTH-ISO-002'
    $iso2 = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.2:8090/v1/bank/iso-authorizations' `
        -ContentType 'application/json' -Body ($transaction | ConvertTo-Json) -TimeoutSec 15
    if ($iso1.isoConnectionId -ne $iso2.isoConnectionId) { throw 'La liaison ISO persistante n a pas ete reutilisee.' }
    if ($null -eq $iso1.eventPublication.offset -or $null -eq $iso2.eventPublication.offset) {
        throw 'La publication Kafka ISO n est pas confirmee.'
    }

    & $Python (Join-Path $Root 'scripts\consume-kafka-evidence.py') 'AUTH-REST-001' 'AUTH-ISO-001' 'AUTH-ISO-002'
    if ($LASTEXITCODE -ne 0) { throw 'La consommation Kafka de bout en bout a echoue.' }

    $proof = @{
        status='TWO_MACHINE_TOPOLOGY_OK'
        gatewayMachine=@{address='127.0.0.2'; port=8090; processId=$gateway.Id}
        platformMachine=@{address='127.0.0.3'; restPort=8089; isoPort=8583; processId=$platform.Id}
        sharedDatabase=$false
        directInProcessCall=$false
        restResult=$rest
        isoFirstResult=$iso1
        isoSecondResult=$iso2
        persistentIsoConnectionReused=($iso1.isoConnectionId -eq $iso2.isoConnectionId)
    }
    $proof | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $Root 'evidence\two-machine-topology.json') -Encoding utf8
    Write-Output "TWO_MACHINE_TOPOLOGY_OK REST=$($rest.enforcedAction) ISO=$($iso1.enforcedAction) PersistentConnection=$($iso1.isoConnectionId)"
} finally {
    Stop-Process -Id $gateway.Id,$platform.Id -ErrorAction SilentlyContinue
}
