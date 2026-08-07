param(
    [string] $DatabasePassword = $env:MERCHANT_E2E_DB_PASSWORD,
    [string] $DatabaseUser = 'postgres',
    [string] $JwtSecret = $env:MERCHANT_E2E_JWT_SECRET,
    [string] $AcquirerId = 'ACQTEST',
    [string] $ProductId = '5480f18c-14a4-4e87-8fe2-13782efc55c9',
    [string] $JavaExe = 'D:\MoneyCore\jdk-21.0.11\bin\java.exe'
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($DatabasePassword)) {
    throw 'MERCHANT_E2E_DB_PASSWORD ou -DatabasePassword est obligatoire'
}
if ([string]::IsNullOrWhiteSpace($JwtSecret)) {
    throw 'MERCHANT_E2E_JWT_SECRET ou -JwtSecret est obligatoire'
}
# Le shell de l'agent peut exposer simultanement Path et PATH. Windows les
# traite sans tenir compte de la casse et Start-Process refuse ce doublon.
$normalizedPath = $env:Path
[Environment]::SetEnvironmentVariable('PATH', $null, [EnvironmentVariableTarget]::Process)
[Environment]::SetEnvironmentVariable('Path', $normalizedPath, [EnvironmentVariableTarget]::Process)
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$acquiringJar = Join-Path $root 'sg-acquiring\target\sg-acquiring-1.0.0-SNAPSHOT.jar'
$onboardingJar = Join-Path $root 'sg-merchant-onboarding\target\sg-merchant-onboarding-1.0.0-SNAPSHOT.jar'
$evidence = Join-Path $PSScriptRoot 'evidence\three-channels'
$runtime = Join-Path $root 'runtime\merchant-onboarding-three-channels'
New-Item -ItemType Directory -Force -Path $evidence, $runtime | Out-Null

foreach ($required in @($JavaExe, $acquiringJar, $onboardingJar)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Fichier requis absent: $required" }
}

function ConvertTo-Base64Url([byte[]] $Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-TestJwt([string] $Subject, [string] $Role, [string[]] $Permissions = @()) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
    $payloadObject = [ordered]@{ sub = $Subject; role = $Role; permissions = $Permissions; iat = $now; exp = $now + 7200 }
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(($payloadObject | ConvertTo-Json -Compress)))
    $unsigned = "$header.$payload"
    $hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($JwtSecret))
    try { $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned))) }
    finally { $hmac.Dispose() }
    return "$unsigned.$signature"
}

function Invoke-Api {
    param([string] $Method, [string] $Uri, [string] $Token, $Body = $null, [hashtable] $ExtraHeaders = @{})
    $headers = @{ Authorization = "Bearer $Token" }
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $parameters = @{ Method = $Method; Uri = $Uri; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    try { return Invoke-RestMethod @parameters }
    catch {
        $status = if ($_.Exception.Response) { [int] $_.Exception.Response.StatusCode } else { 'sans statut' }
        throw "HTTP $status sur $Method $Uri"
    }
}

function Wait-Port([int] $Port, [System.Diagnostics.Process] $Process, [string] $Name) {
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Process.HasExited) { throw "$Name s'est arrete avant de devenir disponible (code $($Process.ExitCode))" }
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $pending = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
            if ($pending.AsyncWaitHandle.WaitOne(1000) -and $client.Connected) { $client.EndConnect($pending); return }
        } catch { }
        finally { $client.Dispose() }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name ne repond pas sur le port $Port"
}

function Invoke-Channel {
    param([string] $Code, [string] $Label, [string] $MakerMode, [int] $Index)

    $suffix = ([Guid]::NewGuid().ToString('N')).Substring(0, 8).ToUpperInvariant()
    $merchantLogin = "merchant.$Code.$($suffix.ToLowerInvariant())"
    $commercialLogin = "commercial.$Code.$($suffix.ToLowerInvariant())"
    $makerLogin = if ($MakerMode -eq 'COMMERCIAL') { $commercialLogin } else { $merchantLogin }
    $makerRole = if ($MakerMode -eq 'COMMERCIAL') { 'COMMERCIAL' } else { 'MERCHANT' }
    $commercialToken = New-TestJwt $commercialLogin 'COMMERCIAL' @('ONBOARDING_PROSPECT_CREATE')
    $makerToken = New-TestJwt $makerLogin $makerRole
    $reviewerToken = New-TestJwt 'backoffice.reviewer' 'BACK_OFFICE' @('ONBOARDING_KYC_REVIEW', 'ONBOARDING_PROVISION')
    $checkerToken = New-TestJwt 'checker.validator' 'CHECKER' @('ONBOARDING_APPROVE', 'ONBOARDING_PROVISION')

    $prospect = Invoke-Api POST 'http://127.0.0.1:8570/api/merchant-onboarding/v1/prospects' $commercialToken @{
        login = $merchantLogin; email = "$merchantLogin@example.test"; acquirerId = $AcquirerId
    }
    $caseId = [string] $prospect.dossier.id
    $registration = "RC-$Code-$suffix"
    $dossier = Invoke-Api PUT "http://127.0.0.1:8570/api/merchant-onboarding/v1/dossiers/$caseId" $makerToken @{
        legalName = "Commerce $Label $suffix"; tradingName = "Boutique $Label";
        registrationNumber = $registration; country = 'MA'; mcc = '5411';
        settlementAccountReference = "ACC-$suffix"; settlementCurrency = '504';
        productId = $ProductId; acceptanceChannel = 'BOTH'; outletCode = "OUT-$suffix";
        outletName = "Point de vente $Label"; outletAddress = 'Casablanca'; terminalCount = $Index
    }

    $documents = @()
    $types = @('LEGAL_EXISTENCE', 'REPRESENTATIVE_IDENTITY', 'BANK_ACCOUNT_PROOF')
    for ($position = 0; $position -lt $types.Count; $position++) {
        $hashCharacter = @('a', 'b', 'c')[$position]
        $documents += Invoke-Api POST "http://127.0.0.1:8570/api/merchant-onboarding/v1/dossiers/$caseId/documents" $makerToken @{
            type = $types[$position]; storageReference = "proof://$Code/$caseId/$($types[$position])";
            contentType = 'application/pdf'; contentLength = 1024 + $position;
            sha256 = $hashCharacter * 64
        }
    }

    $null = Invoke-Api POST "http://127.0.0.1:8570/api/merchant-onboarding/v1/dossiers/$caseId/kyc/submit" $makerToken
    foreach ($document in $documents) {
        $null = Invoke-Api POST "http://127.0.0.1:8570/api/merchant-onboarding/v1/documents/$($document.id)/review" $reviewerToken @{ accepted = $true; reason = $null }
    }
    $validated = Invoke-Api POST "http://127.0.0.1:8570/api/merchant-onboarding/v1/dossiers/$caseId/kyc/validate" $reviewerToken
    $workflow = Invoke-Api POST "http://127.0.0.1:8570/api/merchant-onboarding/v1/dossiers/$caseId/submit" $makerToken
    $approved = Invoke-Api POST "http://127.0.0.1:8570/api/workflow/approvals/$($workflow.id)/approve" $checkerToken

    $correlation = "proof-$Code-$suffix"
    $queued = Invoke-Api POST "http://127.0.0.1:8570/api/merchant-onboarding/v1/dossiers/$caseId/provision?mode=BATCH" $checkerToken $null @{ 'X-Correlation-ID' = $correlation }
    $pending = @(Invoke-Api GET 'http://127.0.0.1:8570/api/merchant-onboarding/v1/batches/pending' $reviewerToken)
    $canonical = $pending | Where-Object { [string] $_.onboardingCaseId -eq $caseId } | Select-Object -First 1
    if ($null -eq $canonical) { throw "JSON canonique absent pour $Code / $caseId" }
    $canonicalPath = Join-Path $evidence "$Code-canonical-acquiring.json"
    $canonical | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $canonicalPath -Encoding UTF8

    $batch = @(Invoke-Api POST 'http://127.0.0.1:8570/api/merchant-onboarding/v1/batches/run?limit=100&retryFailed=false' $reviewerToken $null @{ 'X-Correlation-ID' = "$correlation-run" })
    $outcome = $batch | Where-Object { [string] $_.dossier.id -eq $caseId } | Select-Object -First 1
    if ($null -eq $outcome) { throw "Resultat Acquiring absent pour $Code / $caseId" }
    $final = $outcome.dossier

    if ($validated.kycStatus -ne 'VALIDATED') { throw "KYC non valide pour $Code" }
    if ($approved.status -ne 'APPROVED') { throw "Checker non valide pour $Code" }
    if ($final.status -ne 'PROVISIONED' -or $outcome.jobStatus -ne 'SUCCEEDED') { throw "Provisionnement Acquiring non termine pour $Code" }
    if ([string] $canonical.maker -ne $makerLogin -or [string] $canonical.checker -ne 'checker.validator') { throw "Maker/Checker incorrects dans le JSON $Code" }
    if ([string] $outcome.result.merchantAcceptorId -notmatch '^\d{15}$') { throw "MID invalide pour $Code" }
    $tids = @($outcome.result.terminals | ForEach-Object { [string] $_.terminalId })
    if ($tids.Count -ne $Index -or @($tids | Where-Object { $_ -notmatch '^\d{8}$' }).Count -gt 0) { throw "TID invalides pour $Code" }

    $result = [ordered]@{
        channel = $Code; label = $Label; makerMode = $MakerMode; maker = $makerLogin;
        kycReviewer = 'backoffice.reviewer'; checker = 'checker.validator'; dossierReference = $final.reference;
        caseId = $caseId; workflowId = $workflow.id; kycStatus = $final.kycStatus;
        onboardingStatus = $final.status; jobStatus = $outcome.jobStatus;
        canonicalJson = (Resolve-Path $canonicalPath).Path; merchantId = $outcome.result.merchantId;
        mid = $outcome.result.merchantAcceptorId; tids = $tids; correlationId = $correlation
    }
    $result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $evidence "$Code-result.json") -Encoding UTF8
    return [pscustomobject] $result
}

$acquiring = $null
$onboarding = $null
try {
    $env:ACQUIRING_REST_PORT = '8550'
    $env:ACQUIRING_DB_USER = $DatabaseUser
    $env:ACQUIRING_DB_PASSWORD = $DatabasePassword
    $env:ACQUIRING_LOG_FILE = (Join-Path $runtime 'acquiring.log')
    $acquiring = Start-Process -FilePath $JavaExe -ArgumentList @(
        '-Xms64m', '-Xmx384m', '-jar', $acquiringJar,
        '--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration'
    ) -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'acquiring.out.log') -RedirectStandardError (Join-Path $runtime 'acquiring.err.log') -PassThru
    Wait-Port 8550 $acquiring 'Acquiring'

    $env:MERCHANT_ONBOARDING_REST_PORT = '8570'
    $env:MERCHANT_ONBOARDING_DB_USER = $DatabaseUser
    $env:MERCHANT_ONBOARDING_DB_PASSWORD = $DatabasePassword
    $env:MERCHANT_ONBOARDING_ACQUIRING_ENABLED = 'true'
    $env:MERCHANT_ONBOARDING_ACQUIRING_BASE_URL = 'http://127.0.0.1:8550'
    $env:MERCHANT_ONBOARDING_IDENTITY_ENABLED = 'false'
    $env:MERCHANT_ONBOARDING_LOG_FILE = (Join-Path $runtime 'onboarding.log')
    $env:JWT_SECRET = $JwtSecret
    $onboarding = Start-Process -FilePath $JavaExe -ArgumentList @('-Xms64m', '-Xmx384m', '-jar', $onboardingJar) -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'onboarding.out.log') -RedirectStandardError (Join-Path $runtime 'onboarding.err.log') -PassThru
    Wait-Port 8570 $onboarding 'Merchant Onboarding'

    $results = @(
        Invoke-Channel 'merchant-web' 'Commercant Web' 'MERCHANT' 1
        Invoke-Channel 'commercial-web' 'Commercial Web' 'COMMERCIAL' 2
        Invoke-Channel 'mobile' 'Application Mobile' 'MERCHANT' 3
    )
    $results | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $evidence 'summary.json') -Encoding UTF8
    $results | Format-Table channel, maker, checker, dossierReference, onboardingStatus, mid, tids
    Write-Output "THREE_CHANNEL_E2E_OK evidence=$evidence"
}
finally {
    foreach ($process in @($onboarding, $acquiring)) {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            $process.WaitForExit(10000) | Out-Null
        }
    }
    Remove-Item Env:ACQUIRING_DB_PASSWORD, Env:MERCHANT_ONBOARDING_DB_PASSWORD, Env:JWT_SECRET -ErrorAction SilentlyContinue
}
