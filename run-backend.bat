@echo off
REM Quick-start script for the NightShift launcher backend.
REM Uses embedded H2 (file-backed in %USERPROFILE%\.nightshift-backend\db.mv.db)
REM so no docker / postgres install is required for local development.

setlocal
if "%JAVA_HOME%"=="" set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
echo Using JAVA_HOME=%JAVA_HOME%
echo Starting NightShift backend on http://127.0.0.1:8080 ...
call gradlew.bat :launcher-backend:run --no-daemon
endlocal
