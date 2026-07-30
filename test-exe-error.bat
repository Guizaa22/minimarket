@echo off
REM Test de l'application avec capture d'erreurs
cd /d "%~dp0\dist\2M-Market"

echo ========================================
echo   Testing 2M-Market.exe
echo ========================================
echo.
echo Working directory: %CD%
echo.

REM Lancer et capturer TOUTE la sortie
2M-Market.exe > ..\..\test-output.txt 2> ..\..\test-error.txt

echo Exit code: %ERRORLEVEL%
echo.

if exist ..\..\test-output.txt (
    echo === STDOUT ===
    type ..\..\test-output.txt
    echo.
)

if exist ..\..\test-error.txt (
    echo === STDERR ===
    type ..\..\test-error.txt
    echo.
)

pause

