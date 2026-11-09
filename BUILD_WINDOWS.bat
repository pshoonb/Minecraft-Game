@echo off
setlocal
set "GRADLE_VERSION=9.2.1"
set "CACHE_DIR=%USERPROFILE%\.gradle\manual-bootstrap"
set "GRADLE_DIR=%CACHE_DIR%\gradle-%GRADLE_VERSION%"

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install a 64-bit Java 21 JDK first.
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$v=(java -version 2^>^&1 | Out-String); if ($v -notmatch '21\.') { Write-Host 'Java 21 is required. Detected:'; Write-Host $v; exit 1 }"
if errorlevel 1 exit /b 1

if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  echo Downloading the official Gradle %GRADLE_VERSION% distribution...
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path $env:TEMP 'gradle-%GRADLE_VERSION%-bin.zip'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile $zip; Expand-Archive -LiteralPath $zip -DestinationPath '%CACHE_DIR%' -Force; Remove-Item -LiteralPath $zip -Force"
  if errorlevel 1 (
    echo Gradle download failed. Check your internet connection and try again.
    exit /b 1
  )
)

call "%GRADLE_DIR%\bin\gradle.bat" --no-daemon clean build
if errorlevel 1 (
  echo Build failed. Please keep the console output for diagnosis.
  exit /b 1
)

echo.
echo Build complete. The mod jar should be in build\libs\
pause
