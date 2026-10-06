@echo off
setlocal
cd /d "%~dp0"

set "JAR=%~dp0toolbox-desktop.jar"
if not exist "%JAR%" set "JAR=%~dp0target\toolbox-desktop.jar"

if not exist "%JAR%" (
  echo Desktop jar not found.
  echo Run: mvn clean package
  echo Expected: target\toolbox-desktop.jar
  exit /b 1
)

where javaw >nul 2>&1
if errorlevel 1 (
  echo Java 8 or newer was not found. Set JAVA_HOME and add its bin folder to PATH.
  pause
  exit /b 1
)
start "" javaw -Dfile.encoding=UTF-8 -jar "%JAR%" --screenshot
endlocal
