@echo off
cd /d "%~dp0"
where javaw >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERRO: Java nao encontrado. Verifique se o Java esta instalado.
    pause
    exit /b 1
)
start /b javaw.exe -jar "usage-monitor-desktop.jar"