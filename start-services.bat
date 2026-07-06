@echo off
REM =====================================================================
REM start-services.bat
REM Lance les 3 fat JAR (acquereur, issuer, orchestrateur) avec Java 21.
REM A lancer depuis la racine du projet (D:\MoneyCore\ScenarioGenerator).
REM Chaque service demarre dans sa propre fenetre.
REM =====================================================================
setlocal enabledelayedexpansion

REM --- Configuration ---
if "%JAVA21%"=="" set "JAVA21=D:\MoneyCore\jdk-21.0.11\bin\java.exe"
set "ROOT=%~dp0"
set "JAR_ACQ=%ROOT%sg-dmas-acquirer\target\sg-dmas-acquirer-1.0.0-SNAPSHOT.jar"
set "JAR_ISS=%ROOT%sg-dmas-issuer\target\sg-dmas-issuer-1.0.0-SNAPSHOT.jar"
set "JAR_ORC=%ROOT%sg-generator-orchestrator\target\sg-generator-orchestrator-1.0.0-SNAPSHOT.jar"

echo === Verification Java 21 ===
if not exist "%JAVA21%" (
    echo   ERREUR : Java 21 introuvable a "%JAVA21%"
    echo   Definir la variable JAVA21 vers le bon chemin et relancer.
    exit /b 1
)
"%JAVA21%" -version

echo.
echo === Verification des JAR ===
for %%J in ("%JAR_ACQ%" "%JAR_ISS%" "%JAR_ORC%") do (
    if not exist "%%~J" (
        echo   ERREUR : JAR manquant : %%~J
        echo   Lancer d'abord : mvn clean package -DskipTests -pl sg-common,sg-dmas-issuer,sg-dmas-acquirer,sg-generator-orchestrator -am
        exit /b 1
    )
    echo   OK : %%~nxJ
)

REM --- Demarrage : acquereur (serveur jPOS) puis issuer (client) puis orchestrateur ---
echo.
echo === Demarrage des services (chacun dans sa fenetre) ===

echo   -^> acquereur (8084 / jPOS 8600)...
start "DMAS-ACQUEREUR" "%JAVA21%" -jar "%JAR_ACQ%"
timeout /t 3 /nobreak >nul

echo   -^> issuer (8501 / jPOS 8500)...
start "DMAS-ISSUER" "%JAVA21%" -jar "%JAR_ISS%"
timeout /t 3 /nobreak >nul

echo   -^> orchestrateur (8080)...
start "ORCHESTRATEUR" "%JAVA21%" -jar "%JAR_ORC%"

REM --- Attente que chaque service reponde ---
echo.
echo === Attente du demarrage (max ~90s par service) ===
call :WAITPORT acquereur 8084
call :WAITPORT issuer 8501
call :WAITPORT orchestrateur 8080

echo.
echo === Termine ===
echo Chaque service tourne dans sa fenetre (DMAS-ACQUEREUR, DMAS-ISSUER, ORCHESTRATEUR).
echo Pour le scenario : scenario-e2e.bat
echo Pour arreter : fermer les 3 fenetres, ou stop-services.bat
exit /b 0

REM ------------------------------------------------------------------
:WAITPORT
REM %1 = nom, %2 = port
set "_name=%~1"
set "_port=%~2"
<nul set /p="  %_name% (port %_port%) "
for /l %%i in (1,1,45) do (
    for /f %%c in ('powershell -NoProfile -Command "try{(Invoke-WebRequest -Uri http://localhost:%_port%/api/status -UseBasicParsing -TimeoutSec 2).StatusCode}catch{if($_.Exception.Response){[int]$_.Exception.Response.StatusCode}else{0}}"') do set "_code=%%c"
    if "!_code!"=="200" goto :WP_OK
    if "!_code!"=="403" goto :WP_OK
    <nul set /p="."
    timeout /t 2 /nobreak >nul
)
echo  TIMEOUT
goto :eof
:WP_OK
echo  DEMARRE (HTTP !_code!)
goto :eof
