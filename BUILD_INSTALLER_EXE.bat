@echo off
setlocal
cd /d "%~dp0"

set "NEOFORGE_VERSION=21.1.265"
set "NEOFORGE_INSTALLER=neoforge-%NEOFORGE_VERSION%-installer.jar"
set "SCRIPT=RPG_Core_一键安装_自动更新版.py"

where py >nul 2>nul
if not errorlevel 1 (
    set "PYTHON=py -3"
) else (
    where python >nul 2>nul
    if errorlevel 1 (
        echo Python 3 was not found. Install Python 3.10 or newer with Tcl/Tk support.
        pause
        exit /b 1
    )
    set "PYTHON=python"
)

%PYTHON% --version
if errorlevel 1 (
    echo Python could not be started.
    pause
    exit /b 1
)

if not exist "%SCRIPT%" (
    echo Installer source was not found:
    echo %SCRIPT%
    pause
    exit /b 1
)

if not exist "%NEOFORGE_INSTALLER%" (
    echo Downloading the official NeoForge installer...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri 'https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.265/neoforge-21.1.265-installer.jar' -OutFile '%NEOFORGE_INSTALLER%'"
    if errorlevel 1 (
        echo NeoForge installer download failed.
        pause
        exit /b 1
    )
)

echo Installing or updating PyInstaller...
%PYTHON% -m pip install --upgrade pyinstaller
if errorlevel 1 (
    echo PyInstaller installation failed.
    pause
    exit /b 1
)

echo Checking Python syntax...
%PYTHON% -m py_compile "%SCRIPT%"
if errorlevel 1 (
    echo Python syntax check failed.
    pause
    exit /b 1
)

echo Building the one-file Windows installer...
%PYTHON% -m PyInstaller --noconfirm --clean --onefile --windowed --name "RPG-Core-Installer" --add-data "%CD%\%NEOFORGE_INSTALLER%;." --distpath "%~dp0dist" --workpath "%~dp0build\pyinstaller" --specpath "%~dp0build\pyinstaller" "%SCRIPT%"
if errorlevel 1 (
    echo EXE packaging failed.
    pause
    exit /b 1
)

echo.
echo EXE created:
echo %~dp0dist\RPG-Core-Installer.exe
pause
