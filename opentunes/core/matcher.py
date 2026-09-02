import re
from typing import Dict, List, Optional, Set, Tuple
try:
    from rapidfuzz import fuzz
except ImportError:
    from thefuzz import fuzz
import yt_dlp

from opentunes.core.models import TrackMetadata

class AudioMatcher:

    YDL_EXTRACTOR_ARGS = {
        "youtube": {
            "player_client": ["android_music", "android", "mweb", "web"],
        }
    }

    @classmethod
    def get_ranked_candidates(
        cls,
        track: TrackMetadata,
        exclude_ids: Optional[Set[str]] = None,
    ) -> List[Tuple[str, float]]:
        excluded = exclude_ids or set()
        candidates: Dict[str, float] = {}

        if track.youtube_url and track.source_provider == "youtube":
            vid = track.youtube_id or (track.youtube_url.split("v=")[1].split("&")[0] if "v=" in track.youtube_url else "")
            if vid and vid not in excluded:
                candidates[track.youtube_url] = 100.0

        clean_title = re.sub(r"\s*[\(\[].*?[\)\]]", "", track.title).strip()
        queries = [
            f"ytsearch6:{track.primary_artist} - {clean_title} Official Audio",
            f"ytsearch6:{track.primary_artist} {clean_title} audio",
            f"ytsearch6:{clean_title} {track.primary_artist}",
            f"ytsearch6:{track.title} {track.primary_artist}",
        ]

        ydl_opts = {
            "extract_flat": True,
            "skip_download": True,
            "quiet": True,
            "no_warnings": True,
            "geo_bypass": True,
            "extractor_args": cls.YDL_EXTRACTOR_ARGS,
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            for query in queries:
                try:
                    res = ydl.extract_info(query, download=False)
                except Exception:
                    continue

                if not res or "entries" not in res:
                    continue

                for entry in res.get("entries", []):
                    if not entry:
                        continue
                    video_id = entry.get("id")
                    if not video_id or video_id in excluded:
                        continue

                    url = entry.get("url") or f"https://www.youtube.com/watch?v={video_id}"
                    score = cls._score_candidate(track, entry)
                    if url not in candidates or score > candidates[url]:
                        candidates[url] = score

        ranked = sorted(candidates.items(), key=lambda x: x[1], reverse=True)
        return ranked

    @classmethod
    def match_track(
        cls,
        track: TrackMetadata,
        force_search: bool = False,
        exclude_ids: Optional[Set[str]] = None,
    ) -> Tuple[str, float]:
        excluded = exclude_ids or set()
        if not force_search and track.youtube_url and track.source_provider == "youtube":
            vid = track.youtube_id or (track.youtube_url.split("v=")[1].split("&")[0] if "v=" in track.youtube_url else "")
            if vid and vid not in excluded:
                return track.youtube_url, 100.0

        candidates = cls.get_ranked_candidates(track, exclude_ids=excluded)
        if not candidates:
            raise RuntimeError(f"No active YouTube audio candidates found for '{track.artist_str} - {track.title}'")

        best_url, best_score = candidates[0]
        track.youtube_url = best_url
        if "v=" in best_url:
            track.youtube_id = best_url.split("v=")[1].split("&")[0]

        return best_url, best_score

    @classmethod
    def _score_candidate(cls, target: TrackMetadata, candidate: dict) -> float:
        cand_title = candidate.get("title", "").lower()
        cand_channel = (candidate.get("uploader") or candidate.get("channel") or "").lower()
        cand_duration = float(candidate.get("duration", 0) or 0)

        target_title = target.title.lower()
        target_artist = target.primary_artist.lower()
        target_duration = target.duration_seconds

        score = 0.0

        cleaned_cand_title = re.sub(
            r"[\(\[](official\s*(audio|video|music\s*video|lyric\s*video|hd|4k)?|lyrics|audio)[\)\]]",
            "",
            cand_title,
            flags=re.IGNORECASE,
        ).strip()

        title_ratio = fuzz.token_set_ratio(target_title, cleaned_cand_title)
        score += (title_ratio / 100.0) * 45.0

        artist_in_channel = fuzz.partial_ratio(target_artist, cand_channel) >= 70
        artist_in_title = fuzz.partial_ratio(target_artist, cand_title) >= 70
        if artist_in_channel or artist_in_title:
            score += 25.0
        else:
            for extra_art in target.artists[1:]:
                if fuzz.partial_ratio(extra_art.lower(), cand_title) >= 70 or fuzz.partial_ratio(extra_art.lower(), cand_channel) >= 70:
                    score += 15.0
                    break

        if target_duration > 0 and cand_duration > 0:
            diff = abs(target_duration - cand_duration)
            if diff <= 4.0:
                score += 20.0
            elif diff <= 8.0:
                score += 15.0
            elif diff <= 15.0:
                score += 5.0
            elif diff <= 30.0:
                score -= 15.0
            else:
                score -= 40.0
        else:
            score += 10.0

        if cand_channel.endswith("- topic"):
            score += 10.0
        elif "official audio" in cand_title or "official lyric video" in cand_title or "official music video" in cand_title:
            score += 6.0

        unwanted_keywords = [
            "cover", "karaoke", "live", "concert", "reaction",
            "slowed", "reverb", "8d audio", "1 hour", "10 hours",
            "extended mix", "tribute", "instrumental"
        ]

        for kw in unwanted_keywords:
            if kw in cand_title and kw not in target_title:
                if kw in ("cover", "karaoke", "reaction", "1 hour", "10 hours"):
                    score -= 40.0
                else:
                    score -= 20.0

        return max(0.0, min(100.0, score))
