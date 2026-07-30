' VBScript launcher - completely silent, no window visible
Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

' Get script directory
scriptPath = fso.GetParentFolderName(WScript.ScriptFullName)

' Change to script directory
WshShell.CurrentDirectory = scriptPath

' Launch Maven in hidden window
WshShell.Run "cmd /c cd /d """ & scriptPath & """ && mvn javafx:run", 0, False

Set WshShell = Nothing
Set fso = Nothing

