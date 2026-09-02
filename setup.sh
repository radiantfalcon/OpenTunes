#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "🎧 Setting up OpenTunes in $SCRIPT_DIR..."

if [ -n "$TERMUX_VERSION" ] || [ -d "/data/data/com.termux" ]; then
    echo "📱 Detected Termux environment."
    echo "• Checking system packages..."
    pkg update -y || true
    pkg install -y python ffmpeg || true
fi

echo "• Creating local Python virtual environment (.venv)..."
python3 -m venv "$SCRIPT_DIR/.venv"

echo "• Installing OpenTunes dependencies..."
"$SCRIPT_DIR/.venv/bin/pip" install --upgrade pip
"$SCRIPT_DIR/.venv/bin/pip" install -r "$SCRIPT_DIR/requirements.txt"

chmod +x "$SCRIPT_DIR/run.sh" "$SCRIPT_DIR/opentunes_cli"
echo "✅ OpenTunes setup complete!"
echo "Run './run.sh' or './run.sh --help' to start."
