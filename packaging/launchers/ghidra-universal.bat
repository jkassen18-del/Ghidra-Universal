@echo off
REM Portable launcher for Ghidra-Universal (Windows).
REM Assumes the bundle layout described in packaging\README.md:
REM   <bundle>\runtime, <bundle>\app, <bundle>\ghidra
setlocal

set "BUNDLE=%~dp0"
if "%BUNDLE:~-1%"=="\" set "BUNDLE=%BUNDLE:~0,-1%"

set "JAVA=%BUNDLE%\runtime\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java.exe"

if "%GHIDRA_INSTALL_DIR%"=="" set "GHIDRA_INSTALL_DIR=%BUNDLE%\ghidra"

"%JAVA%" -Dapp.home="%BUNDLE%" -cp "%BUNDLE%\app\*" io.ghidrauniversal.app.Launcher %*

endlocal
