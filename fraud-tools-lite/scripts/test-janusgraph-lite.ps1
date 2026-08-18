$ErrorActionPreference = 'Stop'
$Root = 'D:\MoneyCore\ScenarioGenerator\fraud-tools-lite'
$env:JAVA_HOME = Join-Path $Root 'runtime\jdk11\jdk-11.0.32+9'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$JanusHome = Join-Path $Root 'runtime\janusgraph-1.1.0\janusgraph-1.1.0'
$Java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$Test = Join-Path $Root 'scripts\test-janusgraph-lite.groovy'

Push-Location $JanusHome
try {
    $output = & $Java '-Xms32m' '-Xmx512m' '-Dlog4j2.configurationFile=conf/log4j2-console.xml' `
        '-cp' (Join-Path $JanusHome 'lib\*') 'groovy.ui.GroovyMain' $Test
    $output | Set-Content -LiteralPath (Join-Path $Root 'evidence\janusgraph-lite.txt') -Encoding utf8
    $output | Write-Output
} finally {
    Pop-Location
}
if ($LASTEXITCODE -ne 0) {
    throw "Le test JanusGraph a echoue avec le code $LASTEXITCODE."
}
