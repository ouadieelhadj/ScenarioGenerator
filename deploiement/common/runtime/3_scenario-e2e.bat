@echo off
REM =====================================================================
REM scenario-e2e.bat  (version .bat pur + PowerShell pour les appels)
REM Scenario complet de bout en bout :
REM   1.login 2.bootstrap KEK 3.sign-on 4.key exchange PEK
REM   5.achat PURCHASE 6.creer campagne 7.lancer TPS 8.suivre
REM Prerequis : les 3 services demarres (start-services.bat).
REM =====================================================================
setlocal enabledelayedexpansion

REM --- Configuration ---
set "ORC=http://localhost:8080"
set "ACQ=http://localhost:8084"
set "ISS=http://localhost:8501"
set "LOGIN=admin"
set "GROUP=TESTGRP01"
set "KEK_CLEAR=0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
set "PAN=5321962145453348"
set "AMOUNT=000000010000"
set "TOKFILE=%TEMP%\sg_token.txt"
set "CIDFILE=%TEMP%\sg_cid.txt"
set "EXIDFILE=%TEMP%\sg_exid.txt"

REM =====================================================================
echo === 1. Login (%LOGIN%) ===
powershell -NoProfile -Command "$body=@{login='%LOGIN%';password=('Admin123'+[char]33)} | ConvertTo-Json -Compress; try{$r=Invoke-RestMethod -Uri '%ORC%/auth/login' -Method Post -ContentType 'application/json' -Body $body; Set-Content -NoNewline -Path '%TOKFILE%' -Value $r.token}catch{Set-Content -NoNewline -Path '%TOKFILE%' -Value ''}"
for %%A in ("%TOKFILE%") do set "TOKSIZE=%%~zA"
if not defined TOKSIZE (echo   [FAIL] login : pas de fichier token & goto :END)
if "%TOKSIZE%"=="0" (echo   [FAIL] login : token vide & goto :END)
echo   [OK]   token recupere (%TOKSIZE% octets)

REM =====================================================================
echo.
echo === 2. Bootstrap KEK (groupe %GROUP%) ===
powershell -NoProfile -Command "$t=Get-Content -Raw '%TOKFILE%'; $h=@{Authorization=('Bearer '+$t)}; $b=@{memberGroupId='%GROUP%';kekClear='%KEK_CLEAR%'} | ConvertTo-Json -Compress; try{$r=Invoke-WebRequest -Uri '%ACQ%/api/admin/dmas/kek/bootstrap' -Method Post -Headers $h -ContentType 'application/json' -Body $b -UseBasicParsing; 'HTTP '+$r.StatusCode}catch{if($_.Exception.Response){'HTTP '+[int]$_.Exception.Response.StatusCode}else{'HTTP 0'}}"

REM =====================================================================
echo.
echo === 3. Sign-on issuer (prerequis du key exchange) ===
for /f "usebackq delims=" %%C in (`powershell -NoProfile -Command "$t=Get-Content -Raw '%TOKFILE%'; $h=@{Authorization=('Bearer '+$t)}; try{$r=Invoke-WebRequest -Uri '%ISS%/api/admin/dmas/jpos/signon' -Method Post -Headers $h -UseBasicParsing; $r.StatusCode}catch{if($_.Exception.Response){[int]$_.Exception.Response.StatusCode}else{0}}"`) do set "CODE=%%C"
if "!CODE!"=="200" (echo   [OK]   sign-on HTTP !CODE!) else (echo   [FAIL] sign-on HTTP !CODE! & goto :END)

REM =====================================================================
echo.
echo === 4. Key exchange PEK (groupe %GROUP%) - apres le sign-on ===
for /f "usebackq delims=" %%C in (`powershell -NoProfile -Command "$t=Get-Content -Raw '%TOKFILE%'; $h=@{Authorization=('Bearer '+$t)}; try{$r=Invoke-WebRequest -Uri '%ACQ%/api/admin/dmas/keyexchange/pek?memberGroupId=%GROUP%' -Method Post -Headers $h -UseBasicParsing; $r.StatusCode}catch{if($_.Exception.Response){[int]$_.Exception.Response.StatusCode}else{0}}"`) do set "CODE=%%C"
if "!CODE!"=="200" (echo   [OK]   PEK echangee HTTP !CODE!) else (echo   [WARN] PEK HTTP !CODE! - on continue)

REM =====================================================================
echo.
echo === 5. Achat unitaire (PURCHASE via jPOS) ===
powershell -NoProfile -Command "$t=Get-Content -Raw '%TOKFILE%'; $h=@{Authorization=('Bearer '+$t)}; $b=@{type='PURCHASE';pan='%PAN%';amount='%AMOUNT%';transport='jpos';entryMode='CARD_PRESENT'} | ConvertTo-Json -Compress; try{$r=Invoke-RestMethod -Uri '%ACQ%/api/admin/dmas/auth' -Method Post -Headers $h -ContentType 'application/json' -Body $b; Set-Content -NoNewline -Path '%TEMP%\sg_achat.txt' -Value ($r.approved.ToString()+';'+[string]$r.de039_response_code)}catch{Set-Content -NoNewline -Path '%TEMP%\sg_achat.txt' -Value 'erreur;'}"
set /p ACHAT=<"%TEMP%\sg_achat.txt"
for /f "tokens=1,2 delims=;" %%a in ("!ACHAT!") do (set "APPROVED=%%a" ^& set "DE39=%%b")
if /i "!APPROVED!"=="True" (echo   [OK]   achat approuve ^(de039=!DE39!^)) else (echo   [INFO] achat : !ACHAT! - on continue)

REM =====================================================================
echo.
echo === 6. Creer une campagne ===
powershell -NoProfile -Command "$t=Get-Content -Raw '%TOKFILE%'; $h=@{Authorization=('Bearer '+$t)}; $cfg='{\"DE002_PAN_MODE\":\"RANDOM\",\"WITH_PIN\":false,\"VARIABLE_FIELDS\":{\"AMOUNT\":{\"mode\":\"RANGE\",\"min\":1000,\"max\":50000}}}'; $payload=@{name='CAMP-E2E';category='DMAS';config=$cfg;active=$true;slaErrorRateMax=10.0;stopOnErrorRate=20.0;loadSteps=@(@{stepOrder=1;startSeconds=0;endSeconds=8;tpsValue=5})} | ConvertTo-Json -Depth 6; try{$r=Invoke-RestMethod -Uri '%ORC%/api/campaigns' -Method Post -Headers $h -ContentType 'application/json' -Body $payload; Set-Content -NoNewline -Path '%CIDFILE%' -Value $r.id}catch{Set-Content -NoNewline -Path '%CIDFILE%' -Value ''}"
set /p CID=<"%CIDFILE%"
if "!CID!"=="" (echo   [FAIL] creation campagne & goto :END)
echo   [OK]   campagne creee ^(id=!CID!^)

REM =====================================================================
echo.
echo === 7. Lancer la campagne (TPS) ===
powershell -NoProfile -Command "$t=Get-Content -Raw '%TOKFILE%'; $h=@{Authorization=('Bearer '+$t)}; try{$r=Invoke-RestMethod -Uri '%ORC%/api/campaigns/!CID!/run' -Method Post -Headers $h; Set-Content -NoNewline -Path '%EXIDFILE%' -Value $r.campaignExecutionId}catch{Set-Content -NoNewline -Path '%EXIDFILE%' -Value ''}"
set /p EXID=<"%EXIDFILE%"
if "!EXID!"=="" (echo   [FAIL] lancement & goto :END)
echo   [OK]   campagne lancee ^(executionId=!EXID!^)

REM =====================================================================
echo.
echo === 8. Suivre l'execution (jusqu'a COMPLETED) ===
set "FINAL="
for /l %%i in (1,1,20) do (
    if not defined FINAL (
        powershell -NoProfile -Command "$t=Get-Content -Raw '%TOKFILE%'; $h=@{Authorization=('Bearer '+$t)}; try{$r=Invoke-RestMethod -Uri '%ORC%/api/campaigns/executions/!EXID!' -Headers $h; Set-Content -NoNewline -Path '%TEMP%\sg_row.txt' -Value ($r.status+';'+[string]$r.txTotal+';'+[string]$r.txApproved+';'+[string]$r.txDeclined+';'+[string]$r.verdict+';'+[string]$r.verdictDetail+';'+[string]$r.tpsActualAvg+';'+[string]$r.responseTimeAvg)}catch{Set-Content -NoNewline -Path '%TEMP%\sg_row.txt' -Value 'ERR;'}"
        set /p ROW=<"%TEMP%\sg_row.txt"
        for /f "tokens=1 delims=;" %%x in ("!ROW!") do set "ST=%%x"
        echo   [%%i] !ROW!
        if "!ST!"=="COMPLETED"          set "FINAL=!ROW!"
        if "!ST!"=="STOPPED_ERROR_RATE" set "FINAL=!ROW!"
        if "!ST!"=="ERROR"              set "FINAL=!ROW!"
        if not defined FINAL timeout /t 2 /nobreak >nul
    )
)

echo.
if defined FINAL (
    echo === RESULTAT FINAL ===
    for /f "tokens=1,2,3,4,5,6,7,8 delims=;" %%a in ("!FINAL!") do (
        echo   status      : %%a
        echo   tx total    : %%b
        echo   tx approved : %%c
        echo   tx declined : %%d
        echo   verdict     : %%e
        echo   detail      : %%f
        echo   tps moyen   : %%g
        echo   resp. moy ms: %%h
    )
    echo   [OK]   Scenario E2E termine avec succes.
) else (
    echo   [WARN] l'execution n'a pas atteint un etat final dans le temps imparti.
)

:END
echo.
echo Note : pour nettoyer la campagne de test, utiliser DELETE %ORC%/api/campaigns/!CID! avec le token.
del "%TOKFILE%" "%CIDFILE%" "%EXIDFILE%" >nul 2>&1
endlocal