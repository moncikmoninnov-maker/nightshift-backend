@echo off
REM NightShift Launcher self-update stub (Phase B).
REM
REM Invoked by the running launcher process right before it exits, with:
REM   %1  PID of the launcher process (we wait for it to terminate)
REM   %2  Absolute path of the OLD .exe (will be replaced)
REM   %3  Absolute path of the NEW .exe (was just downloaded + sha-verified)
REM
REM Behaviour:
REM   - Wait until the launcher process exits so its file handle on the .exe
REM     is released (Windows refuses to overwrite a running .exe).
REM   - Back up the old .exe next to itself (.bak) — single-step rollback in
REM     case the move below corrupts the running install.
REM   - Move the new .exe over the old one. Atomicity comes from MOVE on the
REM     same volume; if MOVE fails (e.g. AV holding a handle) we restore the
REM     .bak and start that instead so the user always gets a functional
REM     launcher (Requirement 12.8).
REM   - Start whichever copy ended up at the target path.
REM
REM Robust against being deleted while running because cmd.exe loads the bat
REM in full at startup; once we're past the shebang, our own resource is no
REM longer needed.

setlocal
set "PARENT_PID=%~1"
set "OLD_EXE=%~2"
set "NEW_EXE=%~3"

if "%PARENT_PID%"=="" goto :usage
if "%OLD_EXE%"==""     goto :usage
if "%NEW_EXE%"==""     goto :usage

REM --- 1. Wait for the parent launcher process to terminate -----------------
:wait
tasklist /FI "PID eq %PARENT_PID%" 2>NUL | find "%PARENT_PID%" >NUL
if %ERRORLEVEL%==0 (
    timeout /t 1 /nobreak >NUL
    goto :wait
)

REM --- 2. Back up the old .exe ---------------------------------------------
copy /Y "%OLD_EXE%" "%OLD_EXE%.bak" >NUL 2>&1

REM --- 3. Replace the old .exe with the new one ----------------------------
move /Y "%NEW_EXE%" "%OLD_EXE%" >NUL 2>&1
if errorlevel 1 (
    REM Move failed — fall back to the backup so the user is not left
    REM without a launcher (Requirement 12.8).
    if exist "%OLD_EXE%.bak" copy /Y "%OLD_EXE%.bak" "%OLD_EXE%" >NUL 2>&1
)

REM --- 4. Start the resulting launcher --------------------------------------
start "" "%OLD_EXE%"
endlocal
exit /b 0

:usage
echo update.bat: missing argument(s)
echo Usage: update.bat ^<parentPid^> ^<oldExePath^> ^<newExePath^>
endlocal
exit /b 64
