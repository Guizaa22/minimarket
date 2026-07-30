@echo off
REM Completely silent launcher - no terminal window visible
REM Uses VBScript to hide the window

cd /d "%~dp0"

REM Create a temporary VBScript to run the command hidden
set "tempVBS=%temp%\run_app_hidden.vbs"
echo Set WshShell = CreateObject("WScript.Shell") > "%tempVBS%"
echo WshShell.Run "cmd /c cd /d ""%~dp0"" && mvn javafx:run", 0, False >> "%tempVBS%"
echo Set WshShell = Nothing >> "%tempVBS%"

REM Execute the VBScript (runs hidden)
cscript //nologo "%tempVBS%"

REM Clean up
del "%tempVBS%" >nul 2>&1

exit

