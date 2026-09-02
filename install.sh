#!/usr/bin/env bash
set -e

INSTALL_DIR="${OPENTUNES_DIR:-$HOME/.opentunes}"
REPO_URL="https://github.com/radiantfalcon/OpenTunes.git"

echo "========================================"
echo "         Installing OpenTunes           "
echo "========================================"

if [ -n "$TERMUX_VERSION" ] || [ -d "/data/data/com.termux" ]; then
    IS_TERMUX=1
else
    IS_TERMUX=0
fi

install_dependencies() {
    if [ "$IS_TERMUX" -eq 1 ]; then
        echo "• Termux environment detected. Installing packages..."
        pkg update -y || true
        pkg install -y git python ffmpeg || true
        if [ ! -d "$HOME/storage/shared" ]; then
            termux-setup-storage || true
        fi
    elif command -v apt-get >/dev/null 2>&1; then
        echo "• Debian/Ubuntu detected. Checking dependencies..."
        MISSING_PKGS=""
        for pkg in git ffmpeg python3 python3-venv python3-pip; do
            if ! dpkg -s "$pkg" >/dev/null 2>&1; then
                MISSING_PKGS="$MISSING_PKGS $pkg"
            fi
        done
        if [ -n "$MISSING_PKGS" ]; then
            echo "• Installing missing packages:$MISSING_PKGS..."
            if [ "$EUID" -eq 0 ]; then
                apt-get update -y && apt-get install -y $MISSING_PKGS
            elif command -v sudo >/dev/null 2>&1; then
                sudo apt-get update -y && sudo apt-get install -y $MISSING_PKGS
            else
                echo "Warning: sudo not found. Please ensure git, ffmpeg, and python3-venv are installed."
            fi
        fi
    elif command -v pacman >/dev/null 2>&1; then
        echo "• Arch Linux detected. Checking dependencies..."
        MISSING_PKGS=""
        for pkg in git ffmpeg python python-pip; do
            if ! pacman -Qi "$pkg" >/dev/null 2>&1; then
                MISSING_PKGS="$MISSING_PKGS $pkg"
            fi
        done
        if [ -n "$MISSING_PKGS" ]; then
            echo "• Installing missing packages:$MISSING_PKGS..."
            if [ "$EUID" -eq 0 ]; then
                pacman -S --needed --noconfirm $MISSING_PKGS
            elif command -v sudo >/dev/null 2>&1; then
                sudo pacman -S --needed --noconfirm $MISSING_PKGS
            fi
        fi
    elif command -v dnf >/dev/null 2>&1; then
        echo "• Fedora detected. Checking dependencies..."
        if [ "$EUID" -eq 0 ]; then
            dnf install -y git ffmpeg python3 python3-pip || true
        elif command -v sudo >/dev/null 2>&1; then
            sudo dnf install -y git ffmpeg python3 python3-pip || true
        fi
    elif command -v brew >/dev/null 2>&1; then
        echo "• macOS detected. Checking Homebrew packages..."
        brew install git ffmpeg python || true
    fi
}

install_dependencies

if ! command -v git >/dev/null 2>&1; then
    echo "Error: git is required but not installed."
    exit 1
fi

if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1; then
    echo "Error: Python 3 is required but not installed."
    exit 1
fi

PYTHON_CMD="python3"
if ! command -v python3 >/dev/null 2>&1; then
    PYTHON_CMD="python"
fi

echo "• Fetching OpenTunes repository..."
if [ -d "$INSTALL_DIR/.git" ]; then
    echo "  Updating existing installation in $INSTALL_DIR..."
    git -C "$INSTALL_DIR" pull --ff-only || {
        echo "  Pull failed, re-cloning fresh..."
        rm -rf "$INSTALL_DIR"
        git clone --depth=1 "$REPO_URL" "$INSTALL_DIR"
    }
else
    rm -rf "$INSTALL_DIR"
    git clone --depth=1 "$REPO_URL" "$INSTALL_DIR"
fi

echo "• Setting up virtual environment..."
"$PYTHON_CMD" -m venv "$INSTALL_DIR/.venv"

echo "• Installing Python dependencies..."
"$INSTALL_DIR/.venv/bin/pip" install --upgrade pip --quiet
"$INSTALL_DIR/.venv/bin/pip" install -r "$INSTALL_DIR/requirements.txt" --quiet

BIN_DIR=""
if [ "$IS_TERMUX" -eq 1 ]; then
    BIN_DIR="${PREFIX:-/data/data/com.termux/files/usr}/bin"
elif [ -w "/usr/local/bin" ]; then
    BIN_DIR="/usr/local/bin"
else
    BIN_DIR="$HOME/.local/bin"
    mkdir -p "$BIN_DIR"
fi

WRAPPER_PATH="$BIN_DIR/opentunes"
echo "• Creating launcher command at $WRAPPER_PATH..."

cat << WRAPPER > "$WRAPPER_PATH"
#!/usr/bin/env bash
OPENTUNES_DIR="$INSTALL_DIR"
export PYTHONPATH="\$OPENTUNES_DIR:\$PYTHONPATH"
exec "\$OPENTUNES_DIR/.venv/bin/python" -m opentunes.main "\$@"
WRAPPER

chmod +x "$WRAPPER_PATH"

PATH_NOTE=""
if [ "$IS_TERMUX" -eq 0 ] && [ "$BIN_DIR" = "$HOME/.local/bin" ]; then
    if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
        for rc in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.profile"; do
            if [ -f "$rc" ] && ! grep -q 'export PATH="$HOME/.local/bin:$PATH"' "$rc"; then
                echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$rc"
            fi
        done
        PATH_NOTE="Note: Added $HOME/.local/bin to your PATH. Restart your terminal or run: export PATH=\"\$HOME/.local/bin:\$PATH\""
    fi
fi

echo ""
echo "========================================"
echo "   OpenTunes successfully installed!    "
echo "========================================"
echo "Run 'opentunes' from any terminal to start."
if [ -n "$PATH_NOTE" ]; then
    echo "$PATH_NOTE"
fi
echo ""
