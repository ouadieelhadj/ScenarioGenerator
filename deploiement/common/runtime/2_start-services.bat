@echo off
REM =====================================================================
REM 2_start-services.bat  (Windows)
REM Demarre les 3 services sur generatorscenario en VALIDATE (usage normal).
REM Les JAR sont lances en arriere-plan, logs dans %TEMP%\sg_logs.
REM A lancer APRES ..\database\1_create-data_base.bat.
REM =====================================================================
setlocal enabledelayedexpansion

if "%JAVA21%"=="" set "JAVA21=D:\MoneyCore\jdk-21.0.11\bin\java.exe"
set "PROJ=D:\MoneyCore\ScenarioGenerator"
set "DBURL=jdbc:postgresql://localhost:5432/generatorscenario"
set "SGTMP=%TEMP%\sg_start_check.txt"
set "LOGDIR=%TEMP%\sg_logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%"

set "JAR_ACQ=%PROJ%\sg-dmas-acquirer\target\sg-dmas-acquirer-1.0.0-SNAPSHOT.jar"
set "JAR_ISS=%PROJ%\sg-dmas-issuer\target\sg-dmas-issuer-1.0.0-SNAPSHOT.jar"
set "JAR_ORC=%PROJ%\sg-generator-orchestrator\target\sg-generator-orchestrator-1.0.0-SNAPSHOT.jar"

echo === Verification des JAR ===
if not exist "%JAR_ACQ%" (echo   MANQUANT : %JAR_ACQ% & goto :ERR)
if not exist "%JAR_ISS%" (echo   MANQUANT : %JAR_ISS% & goto :ERR)
if not exist "%JAR_ORC%" (echo   MANQUANT : %JAR_ORC% & goto :ERR)
echo   Les 3 JAR sont presents.

echo.
echo === Demarrage en VALIDATE (arriere-plan, logs dans %LOGDIR%) ===
echo   -^> acquereur (8084/8600)...
start "DMAS-ACQUEREUR" /B cmd /c ""%JAVA21%" -jar "%JAR_ACQ%" --spring.datasource.url=%DBURL% > "%LOGDIR%\acq.log" 2>&1"
timeout /t 3 /nobreak >nul
echo   -^> issuer (8501/8500)...
start "DMAS-ISSUER" /B cmd /c ""%JAVA21%" -jar "%JAR_ISS%" --spring.datasource.url=%DBURL% > "%LOGDIR%\iss.log" 2>&1"
timeout /t 3 /nobreak >nul
echo   -^> orchestrateur (8080)...
start "ORCHESTRATEUR" /B cmd /c ""%JAVA21%" -jar "%JAR_ORC%" --spring.datasource.url=%DBURL% > "%LOGDIR%\orc.log" 2>&1"

echo.
echo === Attente que les 3 services repondent (max 150s) ===
set "UP=0"
for /l %%i in (1,1,75) do (
    if "!UP!"=="0" (
        call :PORT 8084
        set "A=!RES!"
        call :PORT 8501
        set "B=!RES!"
        call :PORT 8080
        set "C=!RES!"
        if not "!A!"=="0" if not "!B!"=="0" if not "!C!"=="0" set "UP=1"
        if "!UP!"=="0" timeout /t 2 /nobreak >nul
    )
)

echo.
if "!UP!"=="1" (
    echo   [OK] Services UP : acquereur=!A! issuer=!B! orchestrateur=!C!
    echo   ^(403 = securise/actif, 200 = ouvert^)
    echo.
    echo #####################################################################
    echo #   PLATEFORME PRETE - lancer 3_test_e2e.bat
    echo #####################################################################
) else (
    echo   *** Les services n'ont pas tous demarre. Voir les logs :
    echo       %LOGDIR%\acq.log  /  iss.log  /  orc.log
    echo   ^(erreur de VALIDATION de schema = une table manque^)
    goto :ERR
)
del "%SGTMP%" >nul 2>&1
goto :END

:PORT
powershell -NoProfile -Command "try{(Invoke-WebRequest http://localhost:%1/api/status -UseBasicParsing -TimeoutSec 2).StatusCode}catch{if($_.Exception.Response){[int]$_.Exception.Response.StatusCode}else{0}}" > "%SGTMP%" 2>nul
set /p RES=<"%SGTMP%"
if "!RES!"=="" set "RES=0"
goto :eof

:ERR
echo *** Demarrage incomplet. ***
exit /b 1
:END
endlocal
