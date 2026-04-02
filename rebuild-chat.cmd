@echo off
setlocal
for %%I in ("%~dp0.") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%"

for /f "tokens=5" %%P in ('netstat -ano ^| findstr :18081 ^| findstr LISTENING') do (
  taskkill /PID %%P /F >nul 2>nul
)

call mvn -s local-settings.xml -DskipTests package
if errorlevel 1 exit /b 1

call "%REPO_ROOT%\start-chat-clean.cmd"
