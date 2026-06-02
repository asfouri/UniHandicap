@echo off
setlocal
chcp 65001 >nul
set "MAVEN_OPTS=%MAVEN_OPTS% -Dfile.encoding=UTF-8"

where mvn >nul 2>nul
if not errorlevel 1 (
    set "MVN=mvn"
    goto run_maven
)

set "MVN=C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd"
if exist "%MVN%" goto run_maven

echo Maven was not found on PATH, and NetBeans Maven was not found at:
echo   %MVN%
echo Install Apache Maven or add Maven's bin directory to PATH.
exit /b 1

:run_maven
"%MVN%" %*
