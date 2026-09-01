import json
import os
from pathlib import Path
from typing import Any, Dict

from opentunes.core.models import DownloadOptions
from opentunes.utils.system import get_default_music_dir

CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "opentunes"
CONFIG_FILE = CONFIG_DIR / "config.json"

DEFAULT_CONFIG: Dict[str, Any] = {
    "format": "mp3",
    "bitrate": "320k",
    "output_dir": str(get_default_music_dir()),
    "sequential_naming": True,
    "naming_template": "{index:03d} - {title} - {artist}",
    "fetch_lyrics": True,
    "save_lrc": False,
    "embed_cover": True,
    "overwrite": False,
    "max_retries": 3,
    "rate_limit_delay": 1.2,
    "mobile_mode": False,
    "spotify_client_id": "",
    "spotify_client_secret": "",
}

def load_config() -> Dict[str, Any]:
    if not CONFIG_FILE.exists():
        save_config(DEFAULT_CONFIG)
        return dict(DEFAULT_CONFIG)

    try:
        with open(CONFIG_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
            config = dict(DEFAULT_CONFIG)
            config.update(data)
            return config
    except Exception:
        return dict(DEFAULT_CONFIG)

def save_config(config: Dict[str, Any]) -> None:
    try:
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(config, f, indent=2)
    except Exception:
        local_cfg = Path.cwd() / "opentunes_config.json"
        with open(local_cfg, "w", encoding="utf-8") as f:
            json.dump(config, f, indent=2)

def get_download_options(overrides: Dict[str, Any] | None = None) -> DownloadOptions:
    cfg = load_config()
    if overrides:
        cfg.update({k: v for k, v in overrides.items() if v is not None})

    out_dir = Path(cfg.get("output_dir", str(get_default_music_dir()))).expanduser()

    return DownloadOptions(
        format=cfg.get("format", "mp3"),
        bitrate=cfg.get("bitrate", "320k"),
        output_dir=out_dir,
        sequential_naming=cfg.get("sequential_naming", True),
        naming_template=cfg.get("naming_template", "{index:03d} - {title} - {artist}"),
        fetch_lyrics=cfg.get("fetch_lyrics", True),
        save_lrc=cfg.get("save_lrc", False),
        embed_cover=cfg.get("embed_cover", True),
        overwrite=cfg.get("overwrite", False),
        max_retries=cfg.get("max_retries", 3),
        rate_limit_delay=float(cfg.get("rate_limit_delay", 1.2)),
        mobile_mode=cfg.get("mobile_mode", False),
    )
