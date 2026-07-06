@echo off
REM =====================================================================
REM stop-services.bat
REM Arrete les 3 services en fermant les fenetres ouvertes par
REM start-services.bat (titres DMAS-ACQUEREUR, DMAS-ISSUER, ORCHESTRATEUR),
REM puis, par securite, tout process java qui ecoute sur 8080/8084/8501/8600/8500.
REM =====================================================================
setlocal enabledelayedexpansion

echo === Arret par titre de fenetre ===
for %%W in (DMAS-ACQUEREUR DMAS-ISSUER ORCHESTRATEUR) do (
    taskkill /FI "WINDOWTITLE eq %%W" /T /F >nul 2>&1
    if errorlevel 1 (echo   %%W : deja arrete ou introuvable) else (echo   %%W : arrete)
)

echo.
echo === Verification des ports (au cas ou) ===
for %%P in (8080 8084 8501 8600 8500) do (
    set "PID="
    for /f "tokens=5" %%I in ('netstat -ano ^| findstr ":%%P " ^| findstr LISTENING') do set "PID=%%I"
    if defined PID (
        echo   port %%P occupe par PID !PID! -^> arret
        taskkill /PID !PID! /F >nul 2>&1
    ) else (
        echo   port %%P libre
    )
)
echo.
echo Termine.
endlocal
