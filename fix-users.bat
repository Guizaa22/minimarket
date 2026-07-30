@echo off
echo ========================================
echo   2M Market - Fix Users SQLite
echo ========================================
echo.
echo This will reset the default users (admin/employe)
echo with password: admin123
echo.

cd /d "%~dp0"

echo Compiling and running fix utility...
call mvn compile exec:java -Dexec.mainClass="util.FixUsersSQLite" -q

echo.
echo Done! You can now try to login with:
echo   Username: admin
echo   Password: admin123
echo.
pause

