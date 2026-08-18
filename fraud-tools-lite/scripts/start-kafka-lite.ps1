$ErrorActionPreference = 'Stop'
$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$existing = netstat.exe -ano -p tcp | Select-String '^\s*TCP\s+127\.0\.0\.1:9092\s+.*LISTENING\s+(\d+)'
if ($existing) {
    $existingPid = [int]$existing.Matches[0].Groups[1].Value
    $existingPid | Set-Content -LiteralPath (Join-Path $Root 'data\kafka-server.pid')
    Write-Output "KAFKA_LITE_ALREADY_RUNNING Port=9092 PID=$existingPid"
    exit 0
}

if (-not (Test-Path 'T:\')) { subst.exe T: $Root | Out-Null }
$Kafka = 'T:\runtime\kafka_2.13-4.3.1'
$Config = 'T:\config\kafka-lite.properties'
$env:JAVA_HOME = 'D:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot'
$env:KAFKA_HEAP_OPTS = '-Xms256m -Xmx512m'
$env:KAFKA_JVM_PERFORMANCE_OPTS = '-server -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -Djava.awt.headless=true'
$Out = Join-Path $Root 'logs\kafka-server.out.log'
$Err = Join-Path $Root 'logs\kafka-server.err.log'
$Command = '""' + (Join-Path $Kafka 'bin\windows\kafka-server-start.bat') + '" "' + $Config + '""'
$launcher = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d', '/s', '/c', $Command) -WindowStyle Hidden `
    -RedirectStandardOutput $Out -RedirectStandardError $Err -PassThru

for ($attempt = 0; $attempt -lt 90; $attempt++) {
    Start-Sleep -Seconds 1
    $listener = netstat.exe -ano -p tcp | Select-String '^\s*TCP\s+127\.0\.0\.1:9092\s+.*LISTENING\s+(\d+)'
    if ($listener) {
        $listenerPid = [int]$listener.Matches[0].Groups[1].Value
        $listenerPid | Set-Content -LiteralPath (Join-Path $Root 'data\kafka-server.pid')
        Write-Output "KAFKA_LITE_OK Port=9092 PID=$listenerPid LauncherPID=$($launcher.Id)"
        exit 0
    }
    if ($launcher.HasExited) { throw "Kafka s est arrete. Consulter $Err" }
}
throw "Kafka n a pas demarre. Consulter $Err"
