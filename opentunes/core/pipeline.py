from pathlib import Path
from typing import Callable, List, Optional
import yt_dlp

from opentunes.core.downloader import AudioDownloader
from opentunes.core.lyrics import LyricsFetcher
from opentunes.core.matcher import AudioMatcher
from opentunes.core.models import (
    DownloadOptions,
    DownloadProgress,
    PlaylistInfo,
    TrackMetadata,
    TrackStatus,
)
from opentunes.core.sequencer import Sequencer
from opentunes.core.spotify import SpotifyExtractor
from opentunes.core.tagger import AudioTagger
from opentunes.core.youtube import YouTubeExtractor

class DownloadPipeline:

    @classmethod
    def resolve_input(
        cls,
        query_or_url: str,
        spotify_client_id: str = "",
        spotify_client_secret: str = "",
    ) -> PlaylistInfo:
        query = query_or_url.strip()

        if SpotifyExtractor.is_spotify_url(query):
            return SpotifyExtractor.get_metadata(
                query,
                client_id=spotify_client_id,
                client_secret=spotify_client_secret,
            )

        if YouTubeExtractor.is_youtube_url(query):
            return YouTubeExtractor.get_metadata(query)

        return cls._resolve_search_query(query)

    @classmethod
    def _resolve_search_query(cls, query: str) -> PlaylistInfo:
        ydl_opts = {
            "extract_flat": True,
            "skip_download": True,
            "quiet": True,
            "no_warnings": True,
            "geo_bypass": True,
            "extractor_args": {
                "youtube": {
                    "player_client": ["android_music", "android", "mweb", "web"],
                }
            },
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            res = ydl.extract_info(f"ytsearch1:{query}", download=False)
            if not res or "entries" not in res or not res["entries"]:
                raise ValueError(f"No search results found for query: '{query}'")

            entry = res["entries"][0]
            title = entry.get("title") or query
            uploader = entry.get("uploader") or entry.get("channel") or "Unknown Artist"
            duration = entry.get("duration", 0) or 0
            thumb = entry.get("thumbnail")
            vid = entry.get("id")
            v_url = entry.get("url") or (f"https://www.youtube.com/watch?v={vid}" if vid else None)

            artist = uploader
            song_title = title
            if " - " in title:
                parts = title.split(" - ", 1)
                artist = parts[0].strip()
                song_title = parts[1].strip()

            track = TrackMetadata(
                title=song_title,
                artists=[artist],
                album="Single",
                album_artist=artist,
                duration_seconds=float(duration),
                track_number=1,
                total_tracks=1,
                cover_url=thumb,
                source_url=v_url or query,
                source_id=vid,
                source_provider="search",
                youtube_id=vid,
                youtube_url=v_url,
            )

            return PlaylistInfo(
                title=song_title,
                author=artist,
                cover_url=thumb,
                tracks=[track],
                source_url=v_url or query,
                source_type="search",
            )

    @classmethod
    def process_track(
        cls,
        track: TrackMetadata,
        options: DownloadOptions,
        folder_name: str = "",
        total_tracks: int = 1,
        index: int = 1,
        progress_callback: Optional[Callable[[DownloadProgress], None]] = None,
    ) -> Path:
        dest_path = Sequencer.get_destination_path(
            track,
            options,
            folder_name=folder_name,
            total_tracks=total_tracks,
            index=index,
        )

        if dest_path.exists() and dest_path.stat().st_size < 100_000:
            try:
                dest_path.unlink()
            except Exception:
                pass

        if dest_path.exists() and not options.overwrite and dest_path.stat().st_size >= 100_000:
            if progress_callback:
                progress_callback(
                    DownloadProgress(
                        current_track_idx=index,
                        total_tracks=total_tracks,
                        track_title=track.title,
                        status=TrackStatus.SKIPPED,
                        download_percent=100.0,
                        output_path=dest_path,
                    )
                )
            return dest_path

        if progress_callback:
            progress_callback(
                DownloadProgress(
                    current_track_idx=index,
                    total_tracks=total_tracks,
                    track_title=track.title,
                    status=TrackStatus.MATCHING,
                )
            )

        def wrapped_callback(p: DownloadProgress):
            if p.current_track_idx == 0:
                p.current_track_idx = index
            if p.total_tracks == 0:
                p.total_tracks = total_tracks
            if progress_callback:
                progress_callback(p)

        candidates = AudioMatcher.get_ranked_candidates(track)
        if not candidates:
            raise RuntimeError(f"No audio candidates found on YouTube for '{track.artist_str} - {track.title}'")

        download_succeeded = False
        last_error = None
        for candidate_url, score in candidates[:5]:
            try:
                AudioDownloader.download_audio(
                    track,
                    dest_path,
                    options,
                    stream_url=candidate_url,
                    progress_callback=wrapped_callback,
                    track_index=index,
                    total_tracks=total_tracks,
                )
                if dest_path.exists() and dest_path.stat().st_size >= 100_000:
                    track.youtube_url = candidate_url
                    if "v=" in candidate_url:
                        track.youtube_id = candidate_url.split("v=")[1].split("&")[0]
                    download_succeeded = True
                    break
            except Exception as e:
                last_error = e
                if dest_path.exists():
                    dest_path.unlink(missing_ok=True)
                continue

        if not download_succeeded or not dest_path.exists() or dest_path.stat().st_size < 100_000:
            if dest_path.exists():
                dest_path.unlink(missing_ok=True)
            raise RuntimeError(f"Failed to download audio for '{track.title}': {last_error}")

        plain_lyrics = None
        if options.fetch_lyrics:
            plain_lyrics, synced_lyrics = LyricsFetcher.fetch_lyrics(track)
            if synced_lyrics and options.save_lrc:
                LyricsFetcher.save_lrc_file(synced_lyrics, dest_path)

        if progress_callback:
            progress_callback(
                DownloadProgress(
                    current_track_idx=index,
                    total_tracks=total_tracks,
                    track_title=track.title,
                    status=TrackStatus.TAGGING,
                    download_percent=100.0,
                )
            )

        AudioTagger.tag_file(dest_path, track, lyrics=plain_lyrics)

        if progress_callback:
            progress_callback(
                DownloadProgress(
                    current_track_idx=index,
                    total_tracks=total_tracks,
                    track_title=track.title,
                    status=TrackStatus.COMPLETED,
                    download_percent=100.0,
                    output_path=dest_path,
                )
            )

        return dest_path
