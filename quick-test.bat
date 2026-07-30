@echo off
REM Test rapide de l'application
cd /d "%~dp0\dist\2M-Market"

echo ========================================
echo   Testing 2M-Market Application
echo ========================================
echo.
echo Working directory: %CD%
echo.

REM Lancer l'application et capturer TOUTE la sortie
2M-Market.exe > ..\..\app-stdout.txt 2> ..\..\app-stderr.txt

echo.
echo ========================================
echo   Application Exited
echo ========================================
echo.
echo Exit code: %ERRORLEVEL%
echo.

if exist ..\..\app-stdout.txt (
    echo STDOUT:
    echo ----------------------------------------
    type ..\..\app-stdout.txt
    echo.
)

if exist ..\..\app-stderr.txt (
    echo STDERR:
    echo ----------------------------------------
    type ..\..\app-stderr.txt
    echo.
)

pause

