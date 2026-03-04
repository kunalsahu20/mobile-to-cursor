@echo off
title Vexra Desktop Receiver
color 0A

echo.
echo  ╔══════════════════════════════════════╗
echo  ║       VEXRA DESKTOP RECEIVER         ║
echo  ╚══════════════════════════════════════╝
echo.

:: Check if Python is installed
python --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo  [!] Python is not installed.
    echo  [!] Please download Python from: https://www.python.org/downloads/
    echo  [!] IMPORTANT: Check "Add Python to PATH" during installation!
    echo.
    echo  Opening Python download page...
    start https://www.python.org/downloads/
    echo.
    pause
    exit /b 1
)

echo  [OK] Python found.
echo.

:: Install dependencies
echo  [..] Installing dependencies...
pip install pynput >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    python -m pip install pynput >nul 2>&1
)
echo  [OK] Dependencies installed.
echo.

:: Run the receiver
echo  [>>] Starting Vexra Receiver...
echo  ─────────────────────────────────────────
echo.
python "%~dp0receiver.py"

pause
