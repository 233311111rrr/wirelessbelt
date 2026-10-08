@echo off
setlocal
cd /d "%~dp0"
where gradle >nul 2>nul
if %errorlevel%==0 (
  call gradle clean jar
  if errorlevel 1 exit /b %errorlevel%
  echo.
  echo Build complete: build\libs\wirelessbelt-1.0.jar
  exit /b 0
)

echo Gradle was not found in PATH.
echo Please install Gradle 8.x or run this project from an IDE with Gradle support.
echo Required: JDK 17 or newer; Java 21 is supported with --release 17.
exit /b 1
