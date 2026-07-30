@echo off
REM Silent launcher - no credentials shown, terminal hidden
cd /d "%~dp0"

REM Check Java silently (no output)
java -version >nul 2>&1
if %errorlevel% neq 0 (
    msg * "ERROR: Java not found! Please install Java JDK 17+"
    exit /b 1
)

REM Check Maven silently (no output)
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    msg * "ERROR: Maven not found! Please install Maven"
    exit /b 1
)

REM Launch using VBScript for completely hidden execution
if exist "%~dp0launch-app.vbs" (
    cscript //nologo "%~dp0launch-app.vbs"
    exit
)

REM Fallback: Launch in minimized window
start /min cmd /c "cd /d %~dp0 && mvn javafx:run >nul 2>&1"

REM Exit immediately
exit
