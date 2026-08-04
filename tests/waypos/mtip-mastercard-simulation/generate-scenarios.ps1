param(
    [Parameter(Mandatory = $true)]
    [string]$ArchivePath,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = $PSScriptRoot
}

function Normalize-Name([string]$Name) {
    return ($Name.ToLowerInvariant() -replace '\.', '-')
}

function Get-TopFields($Message) {
    $result = @{}
    foreach ($field in $Message.SelectNodes('./FieldList/Field')) {
        if ([string]$field.ID -match 'DE\.(\d{3})$') {
            $number = [int]$Matches[1]
            $result[$number] = [pscustomobject]@{
                Value = [string]$field.FieldViewable
                Hex = ([string]$field.FieldBinary -replace '[^0-9A-Fa-f]', '').ToUpperInvariant()
            }
        }
    }
    return $result
}

function Get-Mti($Message) {
    foreach ($field in $Message.SelectNodes('./FieldList/Field')) {
        if ([string]$field.ID -match 'DE\.-1$') {
            return [string]$field.FieldViewable
        }
    }
    return ''
}

function Add-StringField($Map, [int]$Number, [string]$Value) {
    if (-not [string]::IsNullOrWhiteSpace($Value)) {
        $Map[[string]$Number] = $Value
    }
}

function New-WayPosStep($RequestMessage, $ResponseMessage, [int]$Index) {
    $mti = Get-Mti $RequestMessage
    if ($mti -notin @('0100', '0400')) { return $null }
    $source = Get-TopFields $RequestMessage
    $response = if ($ResponseMessage) { Get-TopFields $ResponseMessage } else { @{} }
    $fields = [ordered]@{}
    $binary = [ordered]@{}

    Add-StringField $fields 7 '${MMDDHHMMSS}'
    Add-StringField $fields 11 '${STAN}'
    Add-StringField $fields 41 '${WAY_POS_TERMINAL_ID}'
    Add-StringField $fields 63 '007SV1.0.0'

    if ($mti -eq '0100') {
        foreach ($number in @(2, 3, 4, 14, 18, 22, 23, 32, 33, 35, 49)) {
            if ($source.ContainsKey($number)) {
                $value = $source[$number].Value
                if ($number -eq 35) { $value = $value -replace '\?', 'D' }
                Add-StringField $fields $number $value
            }
        }
        Add-StringField $fields 12 '${HHMMSS}'
        Add-StringField $fields 13 '${MMDD}'
        Add-StringField $fields 25 '00'
        Add-StringField $fields 37 '${RRN}'
        Add-StringField $fields 42 '${WAY_POS_MERCHANT_ID}'
        Add-StringField $fields 43 'WAY POS MTIP TEST CASABLANCA MA       '
        if ($source.ContainsKey(55) -and $source[55].Hex) {
            $binary['55'] = $source[55].Hex
        }
    } else {
        foreach ($number in @(2, 3, 4, 37, 49)) {
            if ($source.ContainsKey($number)) {
                Add-StringField $fields $number $source[$number].Value
            }
        }
        Add-StringField $fields 24 '400'
        Add-StringField $fields 60 '400'
    }

    $expectedMti = if ($ResponseMessage) { Get-Mti $ResponseMessage } else {
        if ($mti -eq '0100') { '0110' } else { '0410' }
    }
    $expectedRc = if ($response.ContainsKey(39)) { $response[39].Value } else { '00' }
    return [ordered]@{
        id = ('step-{0:d2}-{1}' -f $Index, $mti)
        sourceMti = $mti
        sourceContainsPinBlock = $source.ContainsKey(52)
        request = [ordered]@{
            mti = $mti
            fields = $fields
            binaryFields = $binary
            unsetFields = @()
            macEnabled = $true
            validate = $true
        }
        expected = [ordered]@{
            responseMti = $expectedMti
            responseCode = $expectedRc
            approved = $expectedRc -in @('00', '10')
            emvResponseRequired = $response.ContainsKey(55)
        }
    }
}

if (-not (Test-Path -LiteralPath $ArchivePath)) {
    throw "Archive TSEZ absente: $ArchivePath"
}
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('mtip-sim-' + [guid]::NewGuid().ToString('N'))
$outer = Join-Path $tempRoot 'outer'
$tse = Join-Path $tempRoot 'tse'
New-Item -ItemType Directory -Force -Path $outer, $tse | Out-Null
try {
    [IO.Compression.ZipFile]::ExtractToDirectory((Resolve-Path $ArchivePath), $outer)
    $nested = Get-ChildItem -LiteralPath $outer -File -Filter '*.TSE' | Select-Object -First 1
    if (-not $nested) { throw 'Archive .TSE interne absente' }
    [IO.Compression.ZipFile]::ExtractToDirectory($nested.FullName, $tse)

    [xml]$selectedXml = Get-Content -LiteralPath (Join-Path $tse 'selected.xml') -Raw
    $cardCatalog = Import-Csv -LiteralPath (Join-Path $OutputDirectory '..\..\dmas\mtip_mastercard\mtip_test_cases.csv')
    $checkCatalog = Import-Csv -LiteralPath (Join-Path $OutputDirectory '..\..\dmas\mtip_mastercard\mtip_expected_results.csv')
    $inventory = Import-Csv -LiteralPath (Join-Path $OutputDirectory '..\..\dmas\mtip_mastercard\mtip_card_inventory.csv')

    $requestDir = Join-Path $OutputDirectory 'requests'
    $expectedDir = Join-Path $OutputDirectory 'expected'
    New-Item -ItemType Directory -Force -Path $requestDir, $expectedDir | Out-Null
    Get-ChildItem -LiteralPath $requestDir -File -Filter '*.json' | Remove-Item -Force
    Get-ChildItem -LiteralPath $expectedDir -File -Filter '*.json' | Remove-Item -Force

    $manifest = [System.Collections.Generic.List[object]]::new()
    foreach ($selection in $selectedXml.SelectNodes('//InScope/Test')) {
        $testName = [string]$selection.name
        $slug = Normalize-Name $testName
        $caseRows = @($cardCatalog | Where-Object { $_.test_name -eq $testName })
        $checks = @($checkCatalog | Where-Object { $_.test_name -eq $testName })
        $files = @(Get-ChildItem -LiteralPath $outer -File -Filter '*.xml' |
            Where-Object { $_.BaseName.ToLowerInvariant() -like ('test_' + $slug + '_v*_log_*') } |
            Sort-Object Name)
        $steps = [System.Collections.Generic.List[object]]::new()
        $networkMessages = 0
        $stepIndex = 0
        foreach ($file in $files) {
            [xml]$log = Get-Content -LiteralPath $file.FullName -Raw
            $messages = @($log.SelectNodes('//OnlineMessage'))
            for ($i = 0; $i -lt $messages.Count; $i++) {
                if ([string]$messages[$i].Class -ne 'Request') { continue }
                $mti = Get-Mti $messages[$i]
                $reply = $null
                for ($j = $i + 1; $j -lt $messages.Count; $j++) {
                    if ([string]$messages[$j].Class -eq 'Response') { $reply = $messages[$j]; break }
                    if ([string]$messages[$j].Class -eq 'Request') { break }
                }
                if ($mti -eq '0800') { $networkMessages++; continue }
                $stepIndex++
                $step = New-WayPosStep $messages[$i] $reply $stepIndex
                if ($step) {
                    if ($step.sourceContainsPinBlock) {
                        $stepPan = [string]$step.request.fields['2']
                        $stepCard = $caseRows | Where-Object { $_.pan -eq $stepPan } |
                            Select-Object -First 1
                        if (-not $stepCard -or -not $stepCard.pin) {
                            throw "PIN Card Details absent pour $testName / PAN source"
                        }
                        $step.request['pin'] = [string]$stepCard.pin
                    }
                    $steps.Add($step)
                }
            }
        }

        $hasCrypto = @($steps | Where-Object {
            $_.sourceContainsPinBlock -or $_.request.binaryFields.Contains('55')
        }).Count -gt 0
        $hasReversal = @($steps | Where-Object { $_.sourceMti -eq '0400' }).Count -gt 0
        $classification = if ($steps.Count -eq 0) { 'TPE_ONLY' }
            elseif ($hasCrypto) { 'SIMULATABLE_WITH_CERTIFICATION_KEYS' }
            elseif ($hasReversal) { 'PARTIAL_HOST_SIMULATION' }
            else { 'HOST_SIMULATABLE' }

        $cards = foreach ($row in $caseRows) {
            $card = $inventory | Where-Object { $_.pan -eq $row.pan } | Select-Object -First 1
            [ordered]@{
                cardId = $row.card_id
                application = $row.application
                brand = $row.brand
                pan = $row.pan
                expiry = if ($card) { $card.expiry } else { '' }
                pinSource = 'TSE_CARD_DETAILS'
            }
        }
        $requestDocument = [ordered]@{
            schemaVersion = '1.0'
            testCase = $testName
            statusInReferenceRun = [string]$selection.status
            classification = $classification
            objective = if ($caseRows) { $caseRows[0].objective } else { '' }
            actions = if ($caseRows) { $caseRows[0].actions } else { '' }
            cards = @($cards)
            sourceLogFiles = @($files.Name)
            ignoredMastercardNetworkMessages = $networkMessages
            steps = @($steps)
        }

        $classifiedChecks = foreach ($check in $checks) {
            $text = [string]$check.expected_result
            $checkClass = if ($text -match '(0100|0110|0200|0210|0400|0410|1200|1210|DE 39|DE 55)') {
                if ($text -match '(ARQC|ARPC|PIN)') { 'HOST_CRYPTO' } else { 'HOST_ISO' }
            } else { 'TPE_ONLY' }
            [ordered]@{
                number = [int]$check.check_number
                step = [int]$check.step
                mandatory = $check.mandatory
                classification = $checkClass
                expectedResult = $text
                referenceResult = $check.actual_result
            }
        }
        $expectedDocument = [ordered]@{
            schemaVersion = '1.0'
            testCase = $testName
            scenarioClassification = $classification
            automatedResponses = @($steps | ForEach-Object { $_.expected })
            checks = @($classifiedChecks)
        }

        $requestDocument | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $requestDir ($slug + '.json')) -Encoding utf8
        $expectedDocument | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $expectedDir ($slug + '.json')) -Encoding utf8
        $manifest.Add([pscustomobject]@{
            testCase = $testName
            slug = $slug
            classification = $classification
            simulatorSteps = $steps.Count
            referenceChecks = $checks.Count
            sourceLogs = $files.Count
        })
    }
    $manifest | Sort-Object testCase | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $OutputDirectory 'manifest.json') -Encoding utf8
    $manifest | Sort-Object testCase | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'coverage.csv') -NoTypeInformation -Encoding utf8
    Write-Host "Généré: $($manifest.Count) scénarios MTIP."
} finally {
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}
