@echo off
setlocal
chcp 65001 >nul
set "MAVEN_OPTS=%MAVEN_OPTS% -Dfile.encoding=UTF-8"

where mvn >nul 2>nul
if not errorlevel 1 (
    set "MVN=mvn"
    goto run_app
)

set "MVN=C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd"
if exist "%MVN%" goto run_app

echo Maven was not found on PATH, and NetBeans Maven was not found at:
echo   %MVN%
echo Install Apache Maven or add Maven's bin directory to PATH.
exit /b 1

:run_app
if "%DB_HOST%"=="" set "DB_HOST=localhost"
if "%DB_PORT%"=="" set "DB_PORT=3306"
if "%DB_NAME%"=="" set "DB_NAME=universite_accessibilite"
if "%DB_USER%"=="" set "DB_USER=root"

"%MVN%" javafx:run ^
  "-Ddb.host=%DB_HOST%" ^
  "-Ddb.port=%DB_PORT%" ^
  "-Ddb.name=%DB_NAME%" ^
  "-Ddb.user=%DB_USER%" ^
  "-Ddb.password=%DB_PASSWORD%"
