@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%\..\..\..") do set "REPO_ROOT=%%~fI"
if not defined DEPLOYMENT_CLI_JAR set "DEPLOYMENT_CLI_JAR=%REPO_ROOT%\sg-deployment-cli\target\deployment-cli.jar"
if not defined DEPLOYMENT_JAVA set "DEPLOYMENT_JAVA=java"
if not exist "%DEPLOYMENT_CLI_JAR%" (
  echo ERREUR: CLI absent: %DEPLOYMENT_CLI_JAR% 1>&2
  exit /b 2
)
"%DEPLOYMENT_JAVA%" -jar "%DEPLOYMENT_CLI_JAR%" %*
exit /b %ERRORLEVEL%
