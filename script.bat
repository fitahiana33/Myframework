@echo off
setlocal
REM Wrapper to run the PowerShell build without cmd parsing issues
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
exit /b %ERRORLEVEL%
