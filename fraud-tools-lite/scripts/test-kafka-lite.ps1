$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path 'T:\')) { subst.exe T: $root | Out-Null }
$kafka = 'T:\runtime\kafka_2.13-4.3.1'
$bin = Join-Path $kafka 'bin\windows'
$topic = 'fraud.risk-assessment-completed.v1'
& (Join-Path $bin 'kafka-topics.bat') --bootstrap-server 127.0.0.1:9092 --create --if-not-exists --topic $topic --partitions 1 --replication-factor 1
if ($LASTEXITCODE -ne 0) { throw 'Kafka topic creation failed' }
& (Join-Path $bin 'kafka-topics.bat') --bootstrap-server 127.0.0.1:9092 --describe --topic $topic
if ($LASTEXITCODE -ne 0) { throw 'Kafka topic description failed' }
