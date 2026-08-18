$ErrorActionPreference = 'Stop'
$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$Scripts = Join-Path $Root 'scripts'

try {
    & (Join-Path $Scripts 'start-kafka-lite.ps1')
    & (Join-Path $Scripts 'test-kafka-lite.ps1')
    & (Join-Path $Scripts 'apply-and-test-feast-lite.ps1')
    & (Join-Path $Scripts 'start-feast-lite.ps1')
    & (Join-Path $Scripts 'test-janusgraph-lite.ps1')
    & (Join-Path $Scripts 'train-and-test-ai-lite.ps1')
    & (Join-Path $Scripts 'start-mlflow-lite.ps1')
    & (Join-Path $Scripts 'test-mlflow-model-serving.ps1')
    & (Join-Path $Scripts 'test-two-machine-topology.ps1')
    & (Join-Path $Scripts 'test-trust-validation-campaign.ps1')
    Write-Output 'FRAUD_TOOLS_LITE_ALL_TESTS_OK'
} finally {
    & (Join-Path $Scripts 'stop-fraud-tools-lite.ps1')
}
