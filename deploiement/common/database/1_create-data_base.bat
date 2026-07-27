@echo off
REM =====================================================================
REM 1_create-data_base.bat  (Windows)
REM Cree la base generatorscenario complete via scripts SQL :
REM   - DROP + CREATE DATABASE + users + droits schema
REM   - structure_tables.sql  (35 tables + FK + owners + grants)
REM   - donnees_reference.sql (donnees de reference)
REM Ensuite : demarrer les services en VALIDATE (2_start-services.bat).
REM
REM Usage : 1_create-data_base.bat [chemin_structure.sql] [chemin_donnees.sql]
REM Par defaut, cherche les 2 fichiers dans le dossier courant.
REM =====================================================================
setlocal

set "PGHOST=localhost"
set "PGPORT=5432"
set "SUPERUSER=postgres"
set "PGPASSWORD=postgres123"
set "PSQL=D:\MoneyCore\PostgreSQL\18\bin\psql.exe"
set "DBNAME=generatorscenario"
set "DBPASS=postgres123"

set "STRUCT=%~1"
if "%STRUCT%"=="" set "STRUCT=%~dp0structure_tables.sql"
set "DATA=%~2"
if "%DATA%"=="" set "DATA=%~dp0donnees_reference.sql"

echo #####################################################################
echo #   CREATION DE LA BASE %DBNAME% (via scripts SQL)
echo #####################################################################
if not exist "%STRUCT%" (echo *** structure introuvable : %STRUCT% *** & goto :ERR)
if not exist "%DATA%"   (echo *** donnees introuvable : %DATA% *** & goto :ERR)

echo.
echo === 1. Arret d'eventuels services + DROP/CREATE DATABASE ===
taskkill /IM java.exe /F >nul 2>&1
timeout /t 2 /nobreak >nul

REM Creation des users si absents
"%PSQL%" -h %PGHOST% -p %PGPORT% -U %SUPERUSER% -d postgres -c "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='scenario_user') THEN CREATE ROLE scenario_user LOGIN PASSWORD '%DBPASS%'; END IF; IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_acquirer_user') THEN CREATE ROLE dmas_acquirer_user LOGIN PASSWORD '%DBPASS%'; END IF; IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_issuer_user') THEN CREATE ROLE dmas_issuer_user LOGIN PASSWORD '%DBPASS%'; END IF; END $$;"

REM Drop + recreate base
"%PSQL%" -h %PGHOST% -p %PGPORT% -U %SUPERUSER% -d postgres -c "DROP DATABASE IF EXISTS %DBNAME%;"
if errorlevel 1 (echo   *** DROP impossible *** & goto :ERR)
"%PSQL%" -h %PGHOST% -p %PGPORT% -U %SUPERUSER% -d postgres -c "CREATE DATABASE %DBNAME% OWNER scenario_user;"
if errorlevel 1 (echo   *** CREATE DATABASE impossible *** & goto :ERR)
echo   Base %DBNAME% creee.

echo.
echo === 2. Droits sur le schema public ===
"%PSQL%" -v ON_ERROR_STOP=1 -h %PGHOST% -p %PGPORT% -U %SUPERUSER% -d %DBNAME% -c "GRANT ALL ON SCHEMA public TO scenario_user, dmas_acquirer_user, dmas_issuer_user;"
if errorlevel 1 goto :ERR

echo.
echo === 3. Structure : tables + FK + owners + grants (%STRUCT%) ===
"%PSQL%" -v ON_ERROR_STOP=1 -h %PGHOST% -p %PGPORT% -U %SUPERUSER% -d %DBNAME% -f "%STRUCT%"
if errorlevel 1 (echo   *** ERREUR structure *** & goto :ERR)

echo.
echo --- Verification : nombre de tables ---
"%PSQL%" -h %PGHOST% -p %PGPORT% -U %SUPERUSER% -d %DBNAME% -tAc "SELECT count(*) FROM pg_tables WHERE schemaname='public';"
echo (doit afficher 35)

echo.
echo === 4. Donnees de reference (%DATA%) ===
"%PSQL%" -v ON_ERROR_STOP=1 -h %PGHOST% -p %PGPORT% -U %SUPERUSER% -d %DBNAME% -f "%DATA%"
if errorlevel 1 (echo   *** ERREUR donnees *** & goto :ERR)

echo.
echo === 5. Controle final ===
"%PSQL%" -h %PGHOST% -p %PGPORT% -U %SUPERUSER% -d %DBNAME% -c "SELECT 'tables' AS objet, count(*)::text AS n FROM pg_tables WHERE schemaname='public' UNION ALL SELECT 'users', count(*)::text FROM users UNION ALL SELECT 'roles', count(*)::text FROM roles UNION ALL SELECT 'dmas_cards', count(*)::text FROM dmas_cards;"

echo.
echo --- Repartition des proprietaires ---
"%PSQL%" -h %PGHOST% -p %PGPORT% -U %SUPERUSER% -d %DBNAME% -c "SELECT tableowner, count(*) FROM pg_tables WHERE schemaname='public' GROUP BY tableowner ORDER BY tableowner;"

echo.
echo #####################################################################
echo #   BASE PRETE - lancer 2_start-services.bat (validate)
echo #####################################################################
goto :END

:ERR
echo.
echo *** ECHEC de la creation de la base. ***
exit /b 1
:END
endlocal
