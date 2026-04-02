@echo off
setlocal
for %%I in ("%~dp0.") do set "REPO_ROOT=%%~fI"
set "JAR_PATH=%REPO_ROOT%\target\code-agent-java-0.1.0-SNAPSHOT.jar"

if not exist "%JAR_PATH%" (
  echo Jar not found: %JAR_PATH%
  echo Run: mvn -s local-settings.xml -DskipTests package
  exit /b 1
)

if "%JAVA_TOOL_OPTIONS%"=="" set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8"

chcp 65001>nul
cd /d "%REPO_ROOT%"
if "%~1"=="" (
  powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%REPO_ROOT%\start-chat.ps1"
) else (
  powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%REPO_ROOT%\start-chat.ps1" %*
)
