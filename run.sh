#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/.venv/bin/python" ]; then
    PYTHON_EXEC="$SCRIPT_DIR/.venv/bin/python"
else
    PYTHON_EXEC="python3"
fi
export PYTHONPATH="$SCRIPT_DIR:$PYTHONPATH"
exec "$PYTHON_EXEC" -m opentunes.main "$@"
