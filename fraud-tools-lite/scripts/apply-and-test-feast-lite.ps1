$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$venv = Join-Path $root 'runtime\venv-feast'
$repo = Join-Path $root 'feature-repo'
Push-Location $repo
try {
    & (Join-Path $venv 'Scripts\python.exe') (Join-Path $root 'scripts\prepare-feast-data.py')
    if ($LASTEXITCODE -ne 0) { throw 'Feast data preparation failed' }
    & (Join-Path $venv 'Scripts\feast.exe') apply
    if ($LASTEXITCODE -ne 0) { throw 'Feast apply failed' }
    & (Join-Path $venv 'Scripts\python.exe') (Join-Path $root 'scripts\test-feast-lite.py')
    if ($LASTEXITCODE -ne 0) { throw 'Feast online feature test failed' }
} finally { Pop-Location }
