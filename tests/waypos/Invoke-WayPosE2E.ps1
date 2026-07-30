[CmdletBinding()]
param(
    [string]$ServerBaseUrl = "http://localhost:8530",
    [string]$SimulatorBaseUrl = "http://localhost:8532",
    [switch]$AllowExistingProvisioning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RequiredEnvironmentValue {
    param([Parameter(Mandatory = $true)][string]$Name)
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required environment variable: $Name"
    }
    return $value
}

function Assert-Matches {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Pattern
    )
    if ($Value -notmatch $Pattern) {
        throw "Invalid format for environment variable: $Name"
    }
}

function Test-TcpPort {
    param([string]$HostName, [int]$Port)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(2000)) {
            return $false
        }
        $client.EndConnect($pending)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Invoke-JsonPost {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)]$Body,
        [int[]]$ExpectedStatus = @(200, 201, 202)
    )
    $json = $Body | ConvertTo-Json -Depth 10 -Compress
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post `
            -Uri $Uri -ContentType "application/json" -Body $json `
            -TimeoutSec 65
        $status = [int]$response.StatusCode
        if ($ExpectedStatus -notcontains $status) {
            throw "Unexpected HTTP status $status from $Uri"
        }
        if ([string]::IsNullOrWhiteSpace($response.Content)) {
            return $null
        }
        return $response.Content | ConvertFrom-Json
    } catch {
        $status = 0
        if ($null -ne $_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        if ($ExpectedStatus -contains $status) {
            return $null
        }
        throw "HTTP POST failed for $Uri (status $status). No secret was printed."
    }
}

function Invoke-EmptyPost {
    param([Parameter(Mandatory = $true)][string]$Uri)
    try {
        return Invoke-RestMethod -Method Post -Uri $Uri -TimeoutSec 65
    } catch {
        $status = 0
        if ($null -ne $_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        throw "HTTP POST failed for $Uri (status $status). No secret was printed."
    }
}

function Wait-Health {
    param([string]$Uri, [string]$Service)
    try {
        $health = Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec 5
    } catch {
        throw "$Service is not reachable at $Uri"
    }
    if ($health.status -ne "UP") {
        throw "$Service health is not UP"
    }
}

function Assert-ScenarioCompleted {
    param([string]$Name, $Result)
    if ($null -eq $Result -or -not $Result.completed) {
        $status = if ($null -eq $Result) { "NO_RESPONSE" } else { $Result.status }
        throw "Scenario $Name failed with status $status"
    }
    $codes = @($Result.steps | ForEach-Object { $_.responseCode })
    [PSCustomObject]@{
        Scenario = $Name
        Status = $Result.status
        ResponseCodes = ($codes -join ",")
        BatchId = $Result.batchId
    }
}

Write-Host "[WayPos E2E] Validating real-environment prerequisites..."

$requiredNames = @(
    "WAY_POS_DB_PASSWORD",
    "WAY_POS_LMK_FILE",
    "WAY_POS_OUTBOX_KEY_HEX",
    "WAY_POS_PAN_PEPPER",
    "WAY_POS_TERMINAL_ID",
    "WAY_POS_MERCHANT_ID",
    "WAY_POS_CURRENCY",
    "WAY_POS_MAC_MODE",
    "WAY_POS_TAK_HEX",
    "WAY_POS_MASTER_KEY_ID",
    "WAY_POS_MASTER_KEY_TYPE",
    "WAY_POS_MASTER_KEY_HEX",
    "WAY_POS_E2E_TAK_UNDER_LMK",
    "WAY_POS_E2E_TAK_KCV",
    "WAY_POS_E2E_TAK_LENGTH",
    "WAY_POS_E2E_TPK_UNDER_LMK",
    "WAY_POS_E2E_TPK_KCV",
    "WAY_POS_E2E_TPK_LENGTH",
    "WAY_POS_E2E_PVK_A_UNDER_LMK",
    "WAY_POS_E2E_PVK_A_KCV",
    "WAY_POS_E2E_PVK_B_UNDER_LMK",
    "WAY_POS_E2E_PVK_B_KCV",
    "WAY_POS_E2E_PAN",
    "WAY_POS_E2E_EXPIRY",
    "WAY_POS_E2E_AMOUNT",
    "WAY_POS_E2E_AVAILABLE_BALANCE",
    "WAY_POS_E2E_PIN_BLOCK_HEX",
    "WAY_POS_E2E_PIN_PVV",
    "WAY_POS_E2E_PIN_PVKI",
    "WAY_POS_E2E_MDK_UNDER_LMK",
    "WAY_POS_E2E_MDK_KCV",
    "WAY_POS_E2E_MDK_LENGTH",
    "WAY_POS_E2E_PAN_SEQUENCE",
    "WAY_POS_E2E_ARPC_ARC_HEX",
    "WAY_POS_E2E_EMV_EOD_HEX",
    "WAY_POS_E2E_EMV_REPEAT_HEX",
    "WAY_POS_E2E_EMV_REVERSAL_HEX",
    "WAY_POS_E2E_EMV_ADVICE_HEX",
    "WAY_POS_E2E_NEXT_TAK_ID",
    "WAY_POS_E2E_NEXT_TAK_X917_BLOCK_HEX",
    "WAY_POS_E2E_NEXT_TAK_UNDER_LMK",
    "WAY_POS_E2E_NEXT_TAK_KCV",
    "WAY_POS_E2E_NEXT_TAK_LENGTH"
)

$values = @{}
$missingNames = @()
foreach ($name in $requiredNames) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        $missingNames += $name
    } else {
        $values[$name] = $value
    }
}
if ($missingNames.Count -gt 0) {
    throw "Missing required environment variables: $($missingNames -join ', ')"
}

Assert-Matches "WAY_POS_TERMINAL_ID" $values.WAY_POS_TERMINAL_ID "^[A-Za-z0-9]{8}$"
Assert-Matches "WAY_POS_MERCHANT_ID" $values.WAY_POS_MERCHANT_ID "^[A-Za-z0-9]{15}$"
Assert-Matches "WAY_POS_CURRENCY" $values.WAY_POS_CURRENCY "^\d{3}$"
Assert-Matches "WAY_POS_MAC_MODE" $values.WAY_POS_MAC_MODE "^(BIN|HEX)$"
Assert-Matches "WAY_POS_OUTBOX_KEY_HEX" $values.WAY_POS_OUTBOX_KEY_HEX "^(?i)[0-9a-f]{64}$"
Assert-Matches "WAY_POS_TAK_HEX" $values.WAY_POS_TAK_HEX "^(?i)([0-9a-f]{16}|[0-9a-f]{32})$"
Assert-Matches "WAY_POS_MASTER_KEY_HEX" $values.WAY_POS_MASTER_KEY_HEX "^(?i)([0-9a-f]{16}|[0-9a-f]{32}|[0-9a-f]{48})$"
Assert-Matches "WAY_POS_E2E_PAN" $values.WAY_POS_E2E_PAN "^\d{13,19}$"
Assert-Matches "WAY_POS_E2E_EXPIRY" $values.WAY_POS_E2E_EXPIRY "^\d{4}$"
Assert-Matches "WAY_POS_E2E_AMOUNT" $values.WAY_POS_E2E_AMOUNT "^\d{12}$"
Assert-Matches "WAY_POS_E2E_AVAILABLE_BALANCE" $values.WAY_POS_E2E_AVAILABLE_BALANCE "^\d+$"
Assert-Matches "WAY_POS_E2E_PIN_BLOCK_HEX" $values.WAY_POS_E2E_PIN_BLOCK_HEX "^(?i)[0-9a-f]{16}$"
Assert-Matches "WAY_POS_E2E_PIN_PVV" $values.WAY_POS_E2E_PIN_PVV "^\d{4}$"
Assert-Matches "WAY_POS_E2E_PIN_PVKI" $values.WAY_POS_E2E_PIN_PVKI "^\d$"
Assert-Matches "WAY_POS_E2E_PAN_SEQUENCE" $values.WAY_POS_E2E_PAN_SEQUENCE "^\d{2}$"
Assert-Matches "WAY_POS_E2E_ARPC_ARC_HEX" $values.WAY_POS_E2E_ARPC_ARC_HEX "^(?i)[0-9a-f]{4}$"

foreach ($name in @(
    "WAY_POS_E2E_TAK_UNDER_LMK", "WAY_POS_E2E_TPK_UNDER_LMK",
    "WAY_POS_E2E_PVK_A_UNDER_LMK", "WAY_POS_E2E_PVK_B_UNDER_LMK",
    "WAY_POS_E2E_MDK_UNDER_LMK", "WAY_POS_E2E_NEXT_TAK_UNDER_LMK",
    "WAY_POS_E2E_NEXT_TAK_X917_BLOCK_HEX", "WAY_POS_E2E_EMV_EOD_HEX",
    "WAY_POS_E2E_EMV_REPEAT_HEX", "WAY_POS_E2E_EMV_REVERSAL_HEX",
    "WAY_POS_E2E_EMV_ADVICE_HEX")) {
    Assert-Matches $name $values[$name] "^(?i)([0-9a-f]{2})+$"
}
foreach ($name in @(
    "WAY_POS_E2E_TAK_KCV", "WAY_POS_E2E_TPK_KCV",
    "WAY_POS_E2E_PVK_A_KCV", "WAY_POS_E2E_PVK_B_KCV",
    "WAY_POS_E2E_MDK_KCV", "WAY_POS_E2E_NEXT_TAK_KCV")) {
    Assert-Matches $name $values[$name] "^(?i)[0-9a-f]{6}$"
}
foreach ($name in @(
    "WAY_POS_E2E_TAK_LENGTH", "WAY_POS_E2E_TPK_LENGTH",
    "WAY_POS_E2E_NEXT_TAK_LENGTH")) {
    Assert-Matches $name $values[$name] "^(8|16)$"
}
Assert-Matches "WAY_POS_E2E_MDK_LENGTH" $values.WAY_POS_E2E_MDK_LENGTH "^(16|24)$"

if (-not (Test-Path -LiteralPath $values.WAY_POS_LMK_FILE -PathType Leaf)) {
    throw "WAY_POS_LMK_FILE does not reference an existing file"
}
if (-not (Test-TcpPort "localhost" 5432)) {
    throw "PostgreSQL is not reachable on localhost:5432"
}
Wait-Health "$ServerBaseUrl/api/routing/v1/health" "WayPosServer"
Wait-Health "$SimulatorBaseUrl/api/simulator/v1/health" "wayPosSimulator"

$provisionStatuses = if ($AllowExistingProvisioning) {
    @(200, 201, 202, 409)
} else {
    @(200, 201, 202)
}

Write-Host "[WayPos E2E] Provisioning terminal and HSM-protected keys..."

$terminal = @{
    terminalId = $values.WAY_POS_TERMINAL_ID
    merchantId = $values.WAY_POS_MERCHANT_ID
    extendedSet = $true
    macData = $values.WAY_POS_MAC_MODE
    macRequired = $true
    initialBatchId = "000001"
}
$null = Invoke-JsonPost "$ServerBaseUrl/api/admin/waypos/v1/terminals" `
    $terminal $provisionStatuses

foreach ($key in @(
    @{
        keyType = "TAK"
        keyUnderLmk = $values.WAY_POS_E2E_TAK_UNDER_LMK
        kcv = $values.WAY_POS_E2E_TAK_KCV
        keyLength = [int]$values.WAY_POS_E2E_TAK_LENGTH
    },
    @{
        keyType = "TPK"
        keyUnderLmk = $values.WAY_POS_E2E_TPK_UNDER_LMK
        kcv = $values.WAY_POS_E2E_TPK_KCV
        keyLength = [int]$values.WAY_POS_E2E_TPK_LENGTH
    }
)) {
    $null = Invoke-JsonPost `
        "$ServerBaseUrl/api/admin/waypos/v1/terminals/$($values.WAY_POS_TERMINAL_ID)/working-keys" `
        $key
}

foreach ($key in @(
    @{
        keyCode = "LOCAL_PVK_A"
        keyType = "PVK"
        keyUnderLmk = $values.WAY_POS_E2E_PVK_A_UNDER_LMK
        kcv = $values.WAY_POS_E2E_PVK_A_KCV
        keyLength = 8
    },
    @{
        keyCode = "LOCAL_PVK_B"
        keyType = "PVK"
        keyUnderLmk = $values.WAY_POS_E2E_PVK_B_UNDER_LMK
        kcv = $values.WAY_POS_E2E_PVK_B_KCV
        keyLength = 8
    }
)) {
    $null = Invoke-JsonPost `
        "$ServerBaseUrl/api/admin/waypos/v1/security-keys" $key
}

$card = @{
    pan = $values.WAY_POS_E2E_PAN
    expiryYymm = $values.WAY_POS_E2E_EXPIRY
    currency = $values.WAY_POS_CURRENCY
    availableBalance = [long]$values.WAY_POS_E2E_AVAILABLE_BALANCE
    pinPvv = $values.WAY_POS_E2E_PIN_PVV
    pinPvki = [int]$values.WAY_POS_E2E_PIN_PVKI
    mdkUnderLmk = $values.WAY_POS_E2E_MDK_UNDER_LMK
    mdkKcv = $values.WAY_POS_E2E_MDK_KCV
    mdkLength = [int]$values.WAY_POS_E2E_MDK_LENGTH
    panSequenceNumber = $values.WAY_POS_E2E_PAN_SEQUENCE
    arpcArcHex = $values.WAY_POS_E2E_ARPC_ARC_HEX
}
$null = Invoke-JsonPost "$ServerBaseUrl/api/admin/waypos/v1/cards" `
    $card $provisionStatuses

$bin = $values.WAY_POS_E2E_PAN.Substring(0, 6)
$route = @{
    binFrom = $bin
    binTo = $bin
    interfaceCode = "00000"
    priority = 1
}
$null = Invoke-JsonPost "$ServerBaseUrl/api/admin/waypos/v1/bin-routes" `
    $route

$nextTak = @{
    terminalId = $values.WAY_POS_TERMINAL_ID
    keyType = "TAK"
    keyId = $values.WAY_POS_E2E_NEXT_TAK_ID
    algorithm = "T"
    kcv = $values.WAY_POS_E2E_NEXT_TAK_KCV
    masterKeyId = $values.WAY_POS_MASTER_KEY_ID
    masterKeyType = $values.WAY_POS_MASTER_KEY_TYPE
    ansiX917BlockHex = $values.WAY_POS_E2E_NEXT_TAK_X917_BLOCK_HEX
    keyUnderLmk = $values.WAY_POS_E2E_NEXT_TAK_UNDER_LMK
    keyLength = [int]$values.WAY_POS_E2E_NEXT_TAK_LENGTH
    actionCode = "0"
    replacementKeyId = $null
}
$null = Invoke-JsonPost `
    "$ServerBaseUrl/api/admin/waypos/v1/terminal-keys" $nextTak

Write-Host "[WayPos E2E] Executing dynamic ANSI X9.17 key change..."
$keyChange = Invoke-EmptyPost `
    "$SimulatorBaseUrl/api/simulator/v1/key-change?confirm=true"
if ($keyChange.responseCode -ne "00" `
        -or -not $keyChange.responseMacVerified `
        -or -not $keyChange.confirmationSent `
        -or $keyChange.confirmationResponseCode -ne "00" `
        -or -not $keyChange.confirmationMacVerified `
        -or @($keyChange.importedKeyStatuses |
            Where-Object { $_.status -ne "0" }).Count -ne 0) {
    throw "Dynamic key change was not fully accepted and MAC-verified"
}

function Invoke-E2EScenario {
    param([string]$Name, [string]$EmvDataHex)
    $body = @{
        pan = $values.WAY_POS_E2E_PAN
        expiry = $values.WAY_POS_E2E_EXPIRY
        amount = $values.WAY_POS_E2E_AMOUNT
        targetPan = $null
        pinBlockHex = $values.WAY_POS_E2E_PIN_BLOCK_HEX
        emvDataHex = $EmvDataHex
        terminalId = $values.WAY_POS_TERMINAL_ID
        merchantId = $values.WAY_POS_MERCHANT_ID
        macEnabled = $true
        batchId = $null
        cardControlType = $null
    }
    $result = Invoke-JsonPost `
        "$SimulatorBaseUrl/api/simulator/v1/scenarios/$Name" $body
    return Assert-ScenarioCompleted $Name $result
}

Write-Host "[WayPos E2E] Executing PIN/ARQC/ARPC and lifecycle scenarios..."
$results = @()
# EOD is executed first so its reconciliation totals refer to a clean batch.
$results += Invoke-E2EScenario "PURCHASE_EOD" $values.WAY_POS_E2E_EMV_EOD_HEX
$results += Invoke-E2EScenario "PURCHASE_REPEAT" $values.WAY_POS_E2E_EMV_REPEAT_HEX
$results += Invoke-E2EScenario "PURCHASE_REVERSAL" $values.WAY_POS_E2E_EMV_REVERSAL_HEX
$results += Invoke-E2EScenario "AUTHORIZATION_FINAL_ADVICE" $values.WAY_POS_E2E_EMV_ADVICE_HEX

Write-Host "[WayPos E2E] SUCCESS - no clear key, PIN or PAN was printed."
$results | Format-Table -AutoSize
