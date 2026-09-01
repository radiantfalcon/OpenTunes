import os
import platform
import shutil
import socket
import sys
from pathlib import Path

def is_termux() -> bool:
    return "TERMUX_VERSION" in os.environ or os.path.exists("/data/data/com.termux")

def get_platform_name() -> str:
    if is_termux():
        return "Android (Termux)"
    sys_name = platform.system().lower()
    if sys_name == "darwin":
        return "macOS"
    if sys_name == "windows":
        return "Windows"
    if sys_name == "linux":
        return "Linux"
    return platform.system()

def is_ffmpeg_available() -> bool:
    if shutil.which("ffmpeg") is not None:
        return True
    try:
        import static_ffmpeg
        static_ffmpeg.add_paths()
        return shutil.which("ffmpeg") is not None
    except Exception:
        pass
    return False

def get_ffmpeg_path() -> str | None:
    p = shutil.which("ffmpeg")
    if p:
        return p
    try:
        import static_ffmpeg
        static_ffmpeg.add_paths()
        return shutil.which("ffmpeg")
    except Exception:
        pass
    return None

def get_ffmpeg_install_instructions() -> str:
    if is_termux():
        return "pkg update && pkg install ffmpeg"
    sys_name = platform.system().lower()
    if sys_name == "darwin":
        return "brew install ffmpeg"
    if sys_name == "windows":
        return "winget install Gyan.FFmpeg   (or download from https://ffmpeg.org/download.html)"
    if sys_name == "linux":
        return "sudo apt install ffmpeg   (Ubuntu/Debian) or sudo dnf install ffmpeg (Fedora) or sudo pacman -S ffmpeg (Arch)"
    return "Please install ffmpeg from https://ffmpeg.org"

def get_default_music_dir() -> Path:
    if is_termux():

        shared_music = Path("/sdcard/Music")
        if shared_music.exists() and os.access(shared_music, os.W_OK):
            return shared_music
        termux_storage = Path(os.path.expanduser("~/storage/shared/Music"))
        if termux_storage.exists() and os.access(termux_storage, os.W_OK):
            return termux_storage
        return Path(os.path.expanduser("~/Music"))

    sys_name = platform.system().lower()
    if sys_name == "windows":
        userprofile = os.environ.get("USERPROFILE")
        if userprofile:
            p = Path(userprofile) / "Music"
            return p
    home_music = Path.home() / "Music"
    if home_music.exists():
        return home_music
    return Path.cwd() / "Music"

def check_internet_connection(host: str = "1.1.1.1", port: int = 53, timeout: float = 3.0) -> bool:
    try:
        socket.setdefaulttimeout(timeout)
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))
        s.close()
        return True
    except Exception:

        try:
            import urllib.request
            urllib.request.urlopen("https://www.google.com", timeout=timeout)
            return True
        except Exception:
            return False

def is_narrow_screen(threshold: int = 70) -> bool:
    try:
        width = shutil.get_terminal_size().columns
        return width < threshold or is_termux()
    except Exception:
        return False
