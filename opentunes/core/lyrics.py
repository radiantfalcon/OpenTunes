from pathlib import Path
from typing import Optional, Tuple
import requests

from opentunes.core.models import TrackMetadata

class LyricsFetcher:

    API_URL = "https://lrclib.net/api/get"
    USER_AGENT = "OpenTunes/1.0 (https://github.com/opentunes/opentunes)"

    @classmethod
    def fetch_lyrics(cls, track: TrackMetadata) -> Tuple[Optional[str], Optional[str]]:
        params = {
            "track_name": track.title,
            "artist_name": track.primary_artist,
        }
        if track.album and track.album != "Single" and track.album != "Unknown Album":
            params["album_name"] = track.album
        if track.duration_seconds > 0:
            params["duration"] = str(int(round(track.duration_seconds)))

        headers = {"User-Agent": cls.USER_AGENT}

        try:
            resp = requests.get(cls.API_URL, params=params, headers=headers, timeout=6)
            if resp.status_code == 200:
                data = resp.json()
                plain = data.get("plainLyrics")
                synced = data.get("syncedLyrics")
                return plain, synced
        except Exception:
            pass

        return None, None

    @classmethod
    def save_lrc_file(cls, lrc_content: str, audio_file_path: Path) -> Optional[Path]:
        if not lrc_content or not audio_file_path:
            return None
        lrc_path = audio_file_path.with_suffix(".lrc")
        try:
            with open(lrc_path, "w", encoding="utf-8") as f:
                f.write(lrc_content)
            return lrc_path
        except Exception:
            return None
