import os
import random
import shutil
import tempfile
import time
from pathlib import Path
from typing import Callable, Optional
import yt_dlp

from opentunes.core.models import DownloadOptions, DownloadProgress, TrackMetadata, TrackStatus

class AudioDownloader:

    CLIENT_PROFILES = [
        ["android_music", "android", "mweb"],
        ["android"],
        ["web"],
    ]

    @classmethod
    def download_audio(
        cls,
        track: TrackMetadata,
        dest_path: Path,
        options: DownloadOptions,
        stream_url: Optional[str] = None,
        progress_callback: Optional[Callable[[DownloadProgress], None]] = None,
        track_index: int = 1,
        total_tracks: int = 1,
    ) -> Path:
        url_to_download = stream_url or track.youtube_url
        if not url_to_download:
            raise ValueError(f"No stream URL attached to track: {track.title}")

        if dest_path.exists() and not options.overwrite and dest_path.stat().st_size > 100_000:
            if progress_callback:
                prog = DownloadProgress(
                    current_track_idx=track_index,
                    total_tracks=total_tracks,
                    track_title=track.title,
                    status=TrackStatus.SKIPPED,
                    download_percent=100.0,
                    output_path=dest_path,
                )
                progress_callback(prog)
            return dest_path

        temp_dir = Path(tempfile.mkdtemp(prefix="opentunes_"))
        temp_out_template = str(temp_dir / "%(id)s.%(ext)s")

        fmt = options.format.lower()
        if fmt == "mp3":
            preferred_quality = options.bitrate.replace("k", "") if options.bitrate else "320"
        elif fmt == "opus":
            preferred_quality = "160"
        elif fmt in ("flac", "wav"):
            preferred_quality = "0"
        else:
            preferred_quality = "256"

        def yt_hook(d: dict):
            if not progress_callback:
                return

            status = d.get("status")
            if status == "downloading":
                total_bytes = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                downloaded = d.get("downloaded_bytes") or 0
                speed = d.get("speed") or 0
                eta = d.get("eta") or 0

                percent = (downloaded / total_bytes * 100.0) if total_bytes > 0 else 0.0
                speed_str = f"{speed / (1024 * 1024):.1f} MB/s" if speed else ""
                eta_str = f"{int(eta)}s" if eta else ""

                prog = DownloadProgress(
                    current_track_idx=track_index,
                    total_tracks=total_tracks,
                    track_title=track.title,
                    status=TrackStatus.DOWNLOADING,
                    download_percent=percent,
                    speed_str=speed_str,
                    eta_str=eta_str,
                )
                progress_callback(prog)
            elif status == "finished":
                prog = DownloadProgress(
                    current_track_idx=track_index,
                    total_tracks=total_tracks,
                    track_title=track.title,
                    status=TrackStatus.CONVERTING,
                    download_percent=100.0,
                )
                progress_callback(prog)

        last_err = None

        for profile_idx, client_profile in enumerate(cls.CLIENT_PROFILES, 1):
            ydl_opts = {
                "format": "bestaudio/best",
                "outtmpl": temp_out_template,
                "noplaylist": True,
                "geo_bypass": True,
                "extractor_args": {
                    "youtube": {
                        "player_client": client_profile,
                    }
                },
                "postprocessors": [
                    {
                        "key": "FFmpegExtractAudio",
                        "preferredcodec": fmt,
                        "preferredquality": preferred_quality,
                    }
                ],
                "postprocessor_args": {
                    "ExtractAudio": ["-id3v2_version", "3", "-write_xing", "1", "-map_metadata", "-1"]
                },
                "quiet": True,
                "no_warnings": True,
                "progress_hooks": [yt_hook],
                "retries": 3,
                "fragment_retries": 3,
                "socket_timeout": 25,
            }

            try:
                with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                    ydl.download([url_to_download])

                expected_files = list(temp_dir.glob(f"*.{fmt}"))
                if not expected_files:
                    expected_files = list(temp_dir.glob("*.*"))

                if not expected_files:
                    raise RuntimeError(f"FFmpeg produced no output audio file for '{track.title}'")

                generated_file = expected_files[0]

                if generated_file.stat().st_size < 100_000:
                    raise RuntimeError(f"Downloaded audio file is too small ({generated_file.stat().st_size} bytes)")

                dest_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(generated_file), str(dest_path))

                if options.rate_limit_delay > 0:
                    delay = options.rate_limit_delay + random.uniform(0.1, 0.4)
                    time.sleep(delay)

                try:
                    shutil.rmtree(str(temp_dir), ignore_errors=True)
                except Exception:
                    pass

                return dest_path

            except Exception as e:
                last_err = e
                time.sleep(0.5 * profile_idx)

        try:
            shutil.rmtree(str(temp_dir), ignore_errors=True)
        except Exception:
            pass

        raise RuntimeError(f"Download failed for {url_to_download}: {last_err}")
