$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$ports = 5000,5001,6566,9092,9093
$stopped = @{}
foreach ($port in $ports) {
    $listeners = netstat.exe -ano -p tcp | Select-String ("^\s*TCP\s+[^:]+:" + $port + "\s+.*LISTENING\s+(\d+)")
    foreach ($listener in $listeners) {
        $processId = [int]$listener.Matches[0].Groups[1].Value
        if ($stopped.ContainsKey($processId)) { continue }
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $process) { continue }
        $ownedPython = $process.Path -like "$Root*"
        $kafkaPidFile = Join-Path $Root 'data\kafka-server.pid'
        $ownedKafka = $port -in 9092,9093 -and (Test-Path $kafkaPidFile) -and `
            ([int](Get-Content -LiteralPath $kafkaPidFile -Raw) -eq $processId)
        if ($port -in 9092,9093 -and $process.Path -eq 'D:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\java.exe') {
            $ownedKafka = $true
        }
        if ($ownedPython -or $ownedKafka) {
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
            $stopped[$processId] = $true
            Write-Output "STOPPED PID=$processId Port=$port"
        }
    }
}
Get-ChildItem -LiteralPath (Join-Path $Root 'data') -Filter '*.pid' -ErrorAction SilentlyContinue | `
    Remove-Item -Force -ErrorAction SilentlyContinue
