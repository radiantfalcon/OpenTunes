#!/usr/bin/env bash
set -e

IS_TERMUX=false
if [ -n "$TERMUX_VERSION" ] || [ -d "/data/data/com.termux" ]; then
    IS_TERMUX=true
fi

echo "Installing OpenTunes..."

if [ "$IS_TERMUX" = true ]; then
    echo "• Termux environment detected."
    pkg update -y || true
    pkg install -y python ffmpeg git pipx || pkg install -y python ffmpeg git
    BIN_DIR="$PREFIX/bin"
else
    BIN_DIR="$HOME/.local/bin"
    mkdir -p "$BIN_DIR"
fi

if command -v pipx >/dev/null 2>&1; then
    echo "• Installing via pipx..."
    pipx install --force git+https://github.com/radiantfalcon/OpenTunes.git
    pipx ensurepath || true
else
    echo "• Installing into isolated environment..."
    INSTALL_DIR="$HOME/.opentunes"
    mkdir -p "$INSTALL_DIR"
    python3 -m venv "$INSTALL_DIR/venv"
    "$INSTALL_DIR/venv/bin/pip" install --upgrade pip
    "$INSTALL_DIR/venv/bin/pip" install git+https://github.com/radiantfalcon/OpenTunes.git

    ln -sf "$INSTALL_DIR/venv/bin/opentunes" "$BIN_DIR/opentunes"
fi

echo ""
echo "OpenTunes installation complete!"
echo "Run 'opentunes' to start."
