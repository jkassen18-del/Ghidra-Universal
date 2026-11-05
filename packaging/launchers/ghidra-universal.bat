@echo off
REM Portable launcher for Ghidra-Universal (Windows).
REM Bundle layout (see packaging\README.md):
REM   <bundle>\runtime    bundled JDK (runs the app AND Ghidra)
REM   <bundle>\app\lib     application + dependency jars
REM   <bundle>\app\javafx  JavaFX module jars (Windows)
REM   <bundle>\ghidra      bundled Ghidra install
setlocal

set "BUNDLE=%~dp0"
if "%BUNDLE:~-1%"=="\" set "BUNDLE=%BUNDLE:~0,-1%"

set "JAVA=%BUNDLE%\runtime\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java.exe"

if "%GHIDRA_INSTALL_DIR%"=="" set "GHIDRA_INSTALL_DIR=%BUNDLE%\ghidra"

"%JAVA%" -Dapp.home="%BUNDLE%" ^
  --module-path "%BUNDLE%\app\javafx" ^
  --add-modules javafx.controls,javafx.graphics,javafx.base ^
  -cp "%BUNDLE%\app\lib\*" ^
  io.ghidrauniversal.app.Launcher %*

endlocal
