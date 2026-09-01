@echo off
set SCRIPT_DIR=%~dp0
echo Setting up OpenTunes...
python -m venv "%SCRIPT_DIR%.venv"
"%SCRIPT_DIR%.venv\Scripts\pip.exe" install --upgrade pip
"%SCRIPT_DIR%.venv\Scripts\pip.exe" install -r "%SCRIPT_DIR%requirements.txt"
echo OpenTunes setup complete!
echo Run 'opentunes.bat' to start.
