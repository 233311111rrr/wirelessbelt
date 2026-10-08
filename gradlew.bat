@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "GRADLE_VERSION=8.10.2"
set "GRADLE_HOME=%~dp0.gradle-dist\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip"

where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: Java was not found in PATH.
  echo Install JDK 17 or newer, then reopen the terminal.
  exit /b 1
)

if exist "%GRADLE_HOME%\bin\gradle.bat" goto run_gradle

where powershell >nul 2>nul
if errorlevel 1 (
  echo ERROR: PowerShell was not found.
  exit /b 1
)

echo Gradle %GRADLE_VERSION% not found. Downloading it once...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
if errorlevel 1 (
  echo ERROR: Could not download Gradle. Check your Internet connection.
  exit /b 1
)

if not exist "%~dp0.gradle-dist" mkdir "%~dp0.gradle-dist"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '%GRADLE_ZIP%' -DestinationPath '%~dp0.gradle-dist'"
if errorlevel 1 (
  echo ERROR: Could not extract Gradle.
  exit /b 1
)
del /q "%GRADLE_ZIP%" >nul 2>nul

:run_gradle
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %errorlevel%
