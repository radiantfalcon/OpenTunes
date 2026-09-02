#!/usr/bin/env bash
set -e

REPO_URL="https://github.com/radiantfalcon/OpenTunes.git"

echo "🎧 Installing OpenTunes..."

if [ -n "$TERMUX_VERSION" ] || [ -d "/data/data/com.termux" ]; then
    IS_TERMUX=1
    BIN_DIR="$PREFIX/bin"
    INSTALL_DIR="$PREFIX/opt/opentunes"
else
    IS_TERMUX=0
    BIN_DIR="$HOME/.local/bin"
    INSTALL_DIR="$HOME/.local/share/opentunes"
fi

if [ "$IS_TERMUX" -eq 1 ]; then
    echo "• Checking Termux packages..."
    pkg update -y || true
    pkg install -y python ffmpeg git pipx || pkg install -y python ffmpeg git || true
fi

if ! command -v python3 >/dev/null 2>&1; then
    echo "Error: Python 3 is required but not installed."
    exit 1
fi

if ! command -v git >/dev/null 2>&1; then
    echo "Error: Git is required but not installed."
    exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "Notice: FFmpeg is not found. Please install FFmpeg for audio conversion."
fi

if command -v pipx >/dev/null 2>&1; then
    echo "• Installing OpenTunes via pipx..."
    pipx install "git+$REPO_URL" --force
    pipx ensurepath >/dev/null 2>&1 || true
else
    echo "• Setting up isolated Python environment..."
    mkdir -p "$INSTALL_DIR"
    python3 -m venv "$INSTALL_DIR/venv"
    "$INSTALL_DIR/venv/bin/pip" install --upgrade pip --quiet
    echo "• Installing OpenTunes dependencies..."
    "$INSTALL_DIR/venv/bin/pip" install "git+$REPO_URL" --quiet

    mkdir -p "$BIN_DIR"
    ln -sf "$INSTALL_DIR/venv/bin/opentunes" "$BIN_DIR/opentunes"

    case ":$PATH:" in
        *":$BIN_DIR:"*) ;;
        *)
            echo "• Adding $BIN_DIR to PATH..."
            SHELL_RC=""
            if [ -f "$HOME/.bashrc" ]; then
                SHELL_RC="$HOME/.bashrc"
            elif [ -f "$HOME/.zshrc" ]; then
                SHELL_RC="$HOME/.zshrc"
            fi
            if [ -n "$SHELL_RC" ]; then
                echo "export PATH=\"$BIN_DIR:\$PATH\"" >> "$SHELL_RC"
            fi
            export PATH="$BIN_DIR:$PATH"
            ;;
    esac
fi

echo ""
echo "✅ OpenTunes successfully installed!"
echo "Run 'opentunes' to start."
