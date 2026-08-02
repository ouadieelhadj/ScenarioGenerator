param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $DeploymentArguments
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$cliJar = if ($env:DEPLOYMENT_CLI_JAR) { $env:DEPLOYMENT_CLI_JAR } else {
    Join-Path $repoRoot 'sg-deployment-cli\target\deployment-cli.jar'
}
$javaExecutable = if ($env:DEPLOYMENT_JAVA) { $env:DEPLOYMENT_JAVA } else { 'java' }

if (-not (Test-Path -LiteralPath $cliJar -PathType Leaf)) {
    throw "CLI absent: $cliJar"
}

& $javaExecutable -jar $cliJar @DeploymentArguments
exit $LASTEXITCODE
