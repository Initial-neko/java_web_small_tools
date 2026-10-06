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

start "" javaw -jar "%JAR%" --screenshot
endlocal
