from pathlib import Path
from typing import List

from opentunes.core.models import DownloadOptions, TrackMetadata
from opentunes.utils.formatting import pad_number, sanitize_filename

class Sequencer:

    @classmethod
    def generate_filename(
        cls,
        track: TrackMetadata,
        options: DownloadOptions,
        total_tracks: int = 1,
        index: int = 1,
    ) -> str:
        ext = options.format.lower().lstrip(".")
        artist_clean = sanitize_filename(track.primary_artist)
        title_clean = sanitize_filename(track.title)

        padded_idx = pad_number(index, total_tracks, min_digits=3)
        name = f"{padded_idx} - {title_clean} - {artist_clean}.{ext}"
        return sanitize_filename(name)

    @classmethod
    def get_destination_path(
        cls,
        track: TrackMetadata,
        options: DownloadOptions,
        folder_name: str = "",
        total_tracks: int = 1,
        index: int = 1,
    ) -> Path:
        base_dir = options.output_dir

        if folder_name:
            clean_folder = sanitize_filename(folder_name)
            target_dir = base_dir / clean_folder
        else:
            target_dir = base_dir

        target_dir.mkdir(parents=True, exist_ok=True)

        filename = cls.generate_filename(track, options, total_tracks=total_tracks, index=index)
        return target_dir / filename
