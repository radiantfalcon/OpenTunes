@echo off
set SCRIPT_DIR=%~dp0
if exist "%SCRIPT_DIR%.venv\Scripts\python.exe" (
    set PYTHON_EXEC="%SCRIPT_DIR%.venv\Scripts\python.exe"
) else (
    set PYTHON_EXEC=python
)

set PYTHONPATH=%SCRIPT_DIR%;%PYTHONPATH%
%PYTHON_EXEC% -m opentunes.main %*
