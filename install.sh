#!/usr/bin/env bash
set -e

INSTALL_DIR="$HOME/.local/share/opentunes"
BIN_DIR="$HOME/.local/bin"

echo "Installing OpenTunes..."

if ! command -v python3 >/dev/null 2>&1; then
    echo "Error: Python 3 is required."
    exit 1
fi

mkdir -p "$INSTALL_DIR"
python3 -m venv "$INSTALL_DIR/venv"

"$INSTALL_DIR/venv/bin/pip" install --upgrade pip
"$INSTALL_DIR/venv/bin/pip" install git+https://github.com/radiantfalcon/OpenTunes.git

mkdir -p "$BIN_DIR"
ln -sf "$INSTALL_DIR/venv/bin/opentunes" "$BIN_DIR/opentunes"

if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    export PATH="$BIN_DIR:$PATH"
    echo "Note: Add $BIN_DIR to your PATH in ~/.bashrc or ~/.zshrc:"
    echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
fi

echo "OpenTunes installed successfully!"
echo "Run 'opentunes' to start."
