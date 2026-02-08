@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-and-test.ps1" %*
exit /b %ERRORLEVEL%
