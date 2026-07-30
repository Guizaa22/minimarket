@echo off
REM Script batch pour lancer l'application et capturer les erreurs

cd /d "%~dp0\dist\2M-Market"

echo ========================================
echo   Launching 2M-Market Application
echo ========================================
echo.
echo Working directory: %CD%
echo.

REM Lancer l'application et capturer la sortie
2M-Market.exe > ..\..\app-output.log 2> ..\..\app-error.log

echo.
echo ========================================
echo   Application Exited
echo ========================================
echo.
echo Exit code: %ERRORLEVEL%
echo.

if exist ..\..\app-output.log (
    echo STDOUT:
    type ..\..\app-output.log
    echo.
)

if exist ..\..\app-error.log (
    echo STDERR:
    type ..\..\app-error.log
    echo.
)

pause

