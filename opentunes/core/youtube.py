import re
import urllib.parse
from typing import List, Optional, Tuple
import yt_dlp

from opentunes.core.models import PlaylistInfo, TrackMetadata

class YouTubeExtractor:

    YDL_EXTRACTOR_ARGS = {
        "youtube": {
            "player_client": ["android", "web", "mweb"],
        }
    }

    @classmethod
    def is_youtube_url(cls, url: str) -> bool:
        if not url:
            return False
        clean = url.lower()
        return any(d in clean for d in ("youtube.com", "youtu.be", "music.youtube.com"))

    @classmethod
    def parse_video_id(cls, url: str) -> Optional[str]:
        if not url:
            return None

        clean = url.strip()

        match_short = re.search(r"youtu\.be/([a-zA-Z0-9_-]{11})", clean)
        if match_short:
            return match_short.group(1)

        match_embed = re.search(r"youtube\.com/(?:shorts|embed)/([a-zA-Z0-9_-]{11})", clean)
        if match_embed:
            return match_embed.group(1)

        parsed = urllib.parse.urlparse(clean)
        if "youtube.com" in parsed.netloc:
            qs = urllib.parse.parse_qs(parsed.query)
            if "v" in qs and qs["v"]:
                v_id = qs["v"][0]
                if len(v_id) == 11:
                    return v_id

        match_v = re.search(r"[?&]v=([a-zA-Z0-9_-]{11})", clean)
        if match_v:
            return match_v.group(1)

        return None

    @classmethod
    def is_playlist_url(cls, url: str) -> bool:
        video_id = cls.parse_video_id(url)
        if video_id:

            return False
        return "/playlist" in url or "list=" in url or "/browse/MPREb_" in url

    @classmethod
    def get_best_thumbnail(cls, info: dict) -> Optional[str]:
        thumbs = info.get("thumbnails", [])
        if not thumbs:
            return info.get("thumbnail")

        valid_thumbs = [t for t in thumbs if "storyboard" not in t.get("url", "").lower()]
        if not valid_thumbs:
            valid_thumbs = thumbs

        sorted_thumbs = sorted(
            valid_thumbs,
            key=lambda t: (t.get("height", 0) or 0) * (t.get("width", 0) or 0) or (t.get("preference", 0) or 0),
            reverse=True,
        )
        return sorted_thumbs[0].get("url")

    @classmethod
    def get_metadata(cls, url: str) -> PlaylistInfo:
        video_id = cls.parse_video_id(url)

        if video_id:

            canonical_url = f"https://www.youtube.com/watch?v={video_id}"
            return cls._fetch_single_track(canonical_url, video_id)
        else:
            return cls._fetch_playlist(url)

    @classmethod
    def _fetch_single_track(cls, canonical_url: str, video_id: str) -> PlaylistInfo:
        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "skip_download": True,
            "extractor_args": cls.YDL_EXTRACTOR_ARGS,
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            try:
                info = ydl.extract_info(canonical_url, download=False)
            except Exception as e:
                raise RuntimeError(f"Failed to fetch YouTube song: {e}")

        if not info:
            raise ValueError(f"Could not retrieve video info for ID: {video_id}")

        title = info.get("track") or info.get("title") or "YouTube Track"
        uploader = info.get("artist") or info.get("creator") or info.get("uploader") or info.get("channel") or "Unknown Artist"
        duration = info.get("duration", 0) or 0
        thumbnail = cls.get_best_thumbnail(info)
        album = info.get("album") or "Single"
        release_year = str(info.get("release_year") or "") if info.get("release_year") else None

        artist_name = uploader
        track_title = title
        if " - " in title and not info.get("track"):
            parts = title.split(" - ", 1)
            artist_name = parts[0].strip()
            track_title = parts[1].strip()

        track_title = re.sub(
            r"\s*[\(\[](Official\s*(Audio|Video|Music\s*Video|Lyric\s*Video|HD|4K)?|Lyrics|Audio)[\)\]]",
            "",
            track_title,
            flags=re.IGNORECASE,
        ).strip()

        track = TrackMetadata(
            title=track_title,
            artists=[artist_name],
            album=album,
            album_artist=artist_name,
            duration_seconds=float(duration),
            track_number=1,
            total_tracks=1,
            release_year=release_year,
            cover_url=thumbnail,
            source_url=canonical_url,
            source_id=video_id,
            source_provider="youtube",
            youtube_id=video_id,
            youtube_url=canonical_url,
        )

        return PlaylistInfo(
            title=track_title,
            author=artist_name,
            description=info.get("description", ""),
            cover_url=thumbnail,
            tracks=[track],
            source_url=canonical_url,
            source_type="youtube_track",
        )

    @classmethod
    def _fetch_playlist(cls, url: str) -> PlaylistInfo:
        ydl_opts = {
            "extract_flat": "in_playlist",
            "skip_download": True,
            "quiet": True,
            "no_warnings": True,
            "playlistend": 500,
            "extractor_args": cls.YDL_EXTRACTOR_ARGS,
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            try:
                info = ydl.extract_info(url, download=False)
            except Exception as e:
                raise RuntimeError(f"Failed to fetch YouTube playlist: {e}")

        if not info:
            raise ValueError(f"Could not retrieve playlist info from URL: {url}")

        if "entries" not in info or not info.get("entries"):

            vid = info.get("id")
            canon = f"https://www.youtube.com/watch?v={vid}" if vid else url
            return cls._fetch_single_track(canon, vid or "")

        playlist_title = info.get("title") or "YouTube Playlist"
        author = info.get("uploader") or info.get("channel") or "YouTube"
        cover_url = cls.get_best_thumbnail(info)

        raw_entries = list(info.get("entries", []))
        tracks: List[TrackMetadata] = []
        total = len(raw_entries)

        for idx, entry in enumerate(raw_entries, 1):
            if not entry:
                continue

            v_id = entry.get("id")
            if not v_id:
                continue

            v_url = f"https://www.youtube.com/watch?v={v_id}"
            v_title = entry.get("title") or f"Track {idx}"
            v_uploader = entry.get("uploader") or entry.get("channel") or author
            v_dur = entry.get("duration", 0) or 0
            v_thumb = cls.get_best_thumbnail(entry) or cover_url

            artist_name = v_uploader
            track_title = v_title
            if " - " in v_title:
                parts = v_title.split(" - ", 1)
                artist_name = parts[0].strip()
                track_title = parts[1].strip()

            track_title = re.sub(
                r"\s*[\(\[](Official\s*(Audio|Video|Music\s*Video|Lyric\s*Video|HD|4K)?|Lyrics|Audio)[\)\]]",
                "",
                track_title,
                flags=re.IGNORECASE,
            ).strip()

            tracks.append(
                TrackMetadata(
                    title=track_title,
                    artists=[artist_name],
                    album=playlist_title,
                    album_artist=author,
                    duration_seconds=float(v_dur),
                    track_number=idx,
                    total_tracks=total,
                    cover_url=v_thumb,
                    source_url=v_url,
                    source_id=v_id,
                    source_provider="youtube",
                    youtube_id=v_id,
                    youtube_url=v_url,
                )
            )

        return PlaylistInfo(
            title=playlist_title,
            author=author,
            description=info.get("description", ""),
            cover_url=cover_url,
            tracks=tracks,
            source_url=url,
            source_type="youtube_playlist",
        )
