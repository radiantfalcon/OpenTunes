from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import List, Optional

class TrackStatus(str, Enum):
    QUEUED = "queued"
    MATCHING = "matching"
    DOWNLOADING = "downloading"
    CONVERTING = "converting"
    TAGGING = "tagging"
    COMPLETED = "completed"
    SKIPPED = "skipped"
    FAILED = "failed"

@dataclass
class TrackMetadata:
    title: str
    artists: List[str] = field(default_factory=list)
    album: str = "Unknown Album"
    album_artist: str = ""
    duration_seconds: float = 0.0
    track_number: int = 1
    total_tracks: int = 1
    disc_number: int = 1
    release_year: Optional[str] = None
    isrc: Optional[str] = None
    genre: Optional[str] = None
    cover_url: Optional[str] = None
    lyrics: Optional[str] = None
    source_url: Optional[str] = None
    source_id: Optional[str] = None
    source_provider: str = "unknown"
    youtube_id: Optional[str] = None
    youtube_url: Optional[str] = None

    @property
    def artist_str(self) -> str:
        if not self.artists:
            return "Unknown Artist"
        return ", ".join(self.artists)

    @property
    def primary_artist(self) -> str:
        if self.artists:
            return self.artists[0]
        return "Unknown Artist"

    def __post_init__(self):
        if not self.album_artist and self.artists:
            self.album_artist = self.artists[0]

@dataclass
class PlaylistInfo:
    title: str
    author: str = "Unknown"
    description: str = ""
    cover_url: Optional[str] = None
    tracks: List[TrackMetadata] = field(default_factory=list)
    source_url: str = ""
    source_type: str = "playlist"

    @property
    def total_tracks(self) -> int:
        return len(self.tracks)

@dataclass
class DownloadOptions:
    format: str = "mp3"
    bitrate: str = "320k"
    output_dir: Path = field(default_factory=lambda: Path.cwd() / "Music")
    sequential_naming: bool = True
    naming_template: str = "{index:03d} - {title} - {artist}"
    fetch_lyrics: bool = True
    save_lrc: bool = False
    embed_cover: bool = True
    overwrite: bool = False
    max_retries: int = 3
    rate_limit_delay: float = 1.2
    mobile_mode: bool = False
    quiet: bool = False

    def get_audio_quality_args(self) -> List[str]:
        fmt = self.format.lower()
        if fmt == "mp3":
            br = self.bitrate if self.bitrate.endswith("k") else f"{self.bitrate}k"
            return ["-c:a", "libmp3lame", "-b:a", br]
        elif fmt == "flac":
            return ["-c:a", "flac", "-compression_level", "8"]
        elif fmt == "wav":
            return ["-c:a", "pcm_s16le"]
        elif fmt == "opus":
            br = self.bitrate if self.bitrate.endswith("k") else "160k"
            return ["-c:a", "libopus", "-b:a", br, "-vbr", "on"]
        elif fmt == "m4a":
            br = self.bitrate if self.bitrate.endswith("k") else "256k"
            return ["-c:a", "aac", "-b:a", br]
        return ["-c:a", "libmp3lame", "-b:a", "320k"]

@dataclass
class DownloadProgress:
    current_track_idx: int = 0
    total_tracks: int = 0
    track_title: str = ""
    status: TrackStatus = TrackStatus.QUEUED
    download_percent: float = 0.0
    speed_str: str = ""
    eta_str: str = ""
    error_message: Optional[str] = None
    output_path: Optional[Path] = None
