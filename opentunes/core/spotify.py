import json
import re
import urllib.parse
from typing import List, Optional, Tuple
import requests

from opentunes.core.models import PlaylistInfo, TrackMetadata

class SpotifyExtractor:

    USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    @classmethod
    def is_spotify_url(cls, url: str) -> bool:
        return "spotify.com" in url or url.startswith("spotify:")

    @classmethod
    def parse_spotify_link(cls, url: str) -> Tuple[Optional[str], Optional[str]]:
        if not url:
            return None, None

        clean_url = url.strip()

        if clean_url.startswith("spotify:"):
            parts = clean_url.split(":")
            if len(parts) >= 3:
                return parts[1], parts[2]

        try:
            parsed = urllib.parse.urlparse(clean_url)
            path_parts = [p for p in parsed.path.split("/") if p]
            if len(path_parts) >= 2:
                if path_parts[0] in ("track", "playlist", "album", "artist"):
                    return path_parts[0], path_parts[1].split("?")[0]
                elif len(path_parts) >= 3 and path_parts[1] in ("track", "playlist", "album", "artist"):
                    return path_parts[1], path_parts[2].split("?")[0]
        except Exception:
            pass

        match = re.search(r"(track|playlist|album|artist)[/:]([a-zA-Z0-9]{22})", clean_url)
        if match:
            return match.group(1), match.group(2)

        return None, None

    @classmethod
    def extract_cover_art(cls, entity: dict) -> Optional[str]:
        if not isinstance(entity, dict):
            return None

        vis_images = entity.get("visualIdentity", {}).get("image", [])
        if isinstance(vis_images, list) and vis_images:
            sorted_imgs = sorted(
                vis_images,
                key=lambda x: x.get("maxHeight", 0) or x.get("maxWidth", 0) or x.get("height", 0) or 0,
                reverse=True,
            )
            return sorted_imgs[0].get("url")

        cover_sources = entity.get("coverArt", {}).get("sources", [])
        if isinstance(cover_sources, list) and cover_sources:
            sorted_sources = sorted(
                cover_sources,
                key=lambda x: x.get("height", 0) or x.get("width", 0) or 0,
                reverse=True,
            )
            return sorted_sources[0].get("url")

        imgs = entity.get("images", []) or entity.get("album", {}).get("images", [])
        if isinstance(imgs, list) and imgs:
            sorted_imgs = sorted(imgs, key=lambda x: x.get("height", 0) or 0, reverse=True)
            return sorted_imgs[0].get("url")

        return None

    @classmethod
    def get_metadata(cls, url: str, client_id: str = "", client_secret: str = "") -> PlaylistInfo:
        entity_type, entity_id = cls.parse_spotify_link(url)
        if not entity_type or not entity_id:
            raise ValueError(f"Invalid Spotify link: {url}")

        if client_id and client_secret:
            try:
                return cls._fetch_via_api(entity_type, entity_id, client_id, client_secret, url)
            except Exception:
                pass

        return cls._fetch_via_embed(entity_type, entity_id, url)

    @classmethod
    def _fetch_via_embed(cls, entity_type: str, entity_id: str, original_url: str) -> PlaylistInfo:
        embed_url = f"https://open.spotify.com/embed/{entity_type}/{entity_id}"
        headers = {"User-Agent": cls.USER_AGENT, "Accept-Language": "en-US,en;q=0.9"}

        try:
            resp = requests.get(embed_url, headers=headers, timeout=12)
            resp.raise_for_status()
            html = resp.text
        except Exception:
            return cls._fetch_via_oembed(entity_type, entity_id, original_url)

        match = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', html)
        if not match:
            return cls._fetch_via_oembed(entity_type, entity_id, original_url)

        token = ""
        try:
            data = json.loads(match.group(1))
            page_props = data.get("props", {}).get("pageProps", {})
            state = page_props.get("state", {}).get("data", {})
            entity = state.get("entity", {})
            session = page_props.get("state", {}).get("settings", {}).get("session", {})
            token = session.get("accessToken", "")
        except Exception:
            return cls._fetch_via_oembed(entity_type, entity_id, original_url)

        if not entity:
            return cls._fetch_via_oembed(entity_type, entity_id, original_url)

        if entity_type == "track":
            return cls._parse_embed_track(entity, original_url)
        elif entity_type == "playlist":
            return cls._parse_embed_playlist(entity, original_url, token=token, entity_id=entity_id)
        elif entity_type == "album":
            return cls._parse_embed_album(entity, original_url)
        elif entity_type == "artist":
            return cls._parse_embed_artist(entity, entity_id, original_url)

        raise ValueError(f"Unsupported Spotify entity type: {entity_type}")

    @classmethod
    def _parse_embed_track(cls, entity: dict, original_url: str) -> PlaylistInfo:
        title = entity.get("name") or entity.get("title") or "Unknown Track"
        artists = [a.get("name") for a in entity.get("artists", []) if a.get("name")]
        if not artists and entity.get("subtitle"):
            artists = [entity.get("subtitle")]
        if not artists:
            artists = ["Unknown Artist"]

        album_name = entity.get("album", {}).get("name") or "Single"
        duration_ms = entity.get("duration", 0) or 0
        duration_sec = duration_ms / 1000.0 if duration_ms else 0.0

        cover_url = cls.extract_cover_art(entity)
        release_date = entity.get("releaseDate", {})
        release_year = str(release_date.get("year", "")) if isinstance(release_date, dict) else None

        track = TrackMetadata(
            title=title,
            artists=artists,
            album=album_name,
            album_artist=artists[0],
            duration_seconds=duration_sec,
            track_number=1,
            total_tracks=1,
            release_year=release_year,
            cover_url=cover_url,
            source_url=original_url,
            source_id=entity.get("id"),
            source_provider="spotify",
        )

        return PlaylistInfo(
            title=title,
            author=artists[0],
            description=f"Track by {', '.join(artists)}",
            cover_url=cover_url,
            tracks=[track],
            source_url=original_url,
            source_type="spotify_track",
        )

    @classmethod
    def _fetch_playlist_pathfinder(cls, entity_id: str, token: str, offset: int = 100) -> List[dict]:
        url = "https://api-partner.spotify.com/pathfinder/v1/query"
        sha256 = "a65e12194ed5fc443a1cdebed5fabe33ca5b07b987185d63c72483867ad13cb4"
        headers = {
            "Authorization": f"Bearer {token}",
            "app-platform": "WebPlayer",
            "User-Agent": cls.USER_AGENT,
        }
        collected = []
        curr_offset = offset
        while True:
            variables = {
                "uri": f"spotify:playlist:{entity_id}",
                "offset": curr_offset,
                "limit": 100,
                "enableWatchFeedEntrypoint": False,
            }
            params = {
                "operationName": "fetchPlaylist",
                "variables": json.dumps(variables, separators=(",", ":")),
                "extensions": json.dumps({"persistedQuery": {"version": 1, "sha256Hash": sha256}}, separators=(",", ":")),
            }
            try:
                resp = requests.get(url, params=params, headers=headers, timeout=12)
                if resp.status_code != 200:
                    break
                content = resp.json().get("data", {}).get("playlistV2", {}).get("content", {})
                items = content.get("items", [])
                total_count = content.get("totalCount", 0)
                if not items:
                    break
                for it in items:
                    t_data = it.get("itemV2", {}).get("data", {})
                    if t_data.get("__typename") == "Track":
                        title = t_data.get("name")
                        if not title:
                            continue
                        artists = [
                            a.get("profile", {}).get("name")
                            for a in t_data.get("artists", {}).get("items", [])
                            if a.get("profile", {}).get("name")
                        ]
                        dur_ms = t_data.get("trackDuration", {}).get("totalMilliseconds", 0)
                        uri = t_data.get("uri", "")
                        tid = uri.split(":")[-1] if uri else ""
                        sources = t_data.get("albumOfTrack", {}).get("coverArt", {}).get("sources", [])
                        cover = sources[0].get("url") if sources else ""
                        album_name = t_data.get("albumOfTrack", {}).get("name", "")
                        collected.append({
                            "title": title,
                            "subtitle": ", ".join(artists) if artists else "",
                            "duration": dur_ms,
                            "id": tid,
                            "uid": tid,
                            "cover": cover,
                            "album": album_name,
                        })
                curr_offset += len(items)
                if total_count and curr_offset >= total_count:
                    break
            except Exception:
                break
        return collected

    @classmethod
    def _parse_embed_playlist(cls, entity: dict, original_url: str, token: str = "", entity_id: str = "") -> PlaylistInfo:
        playlist_title = entity.get("name") or entity.get("title") or "Spotify Playlist"
        author = entity.get("subtitle") or entity.get("owner", {}).get("name") or "Spotify"
        description = entity.get("description") or ""

        cover_url = cls.extract_cover_art(entity)

        raw_tracks = list(entity.get("trackList", []))
        if token and entity_id and len(raw_tracks) >= 100:
            extra_tracks = cls._fetch_playlist_pathfinder(entity_id, token, offset=len(raw_tracks))
            raw_tracks.extend(extra_tracks)

        tracks: List[TrackMetadata] = []
        total = len(raw_tracks)
        for idx, item in enumerate(raw_tracks, 1):
            title = item.get("title") or item.get("name") or f"Track {idx}"
            subtitle = item.get("subtitle") or ""
            artists = [a.strip() for a in subtitle.split(",") if a.strip()] if subtitle else ["Unknown Artist"]
            duration_ms = item.get("duration", 0) or 0
            duration_sec = duration_ms / 1000.0 if duration_ms else 0.0

            track_cover = item.get("cover") or cls.extract_cover_art(item) or cover_url
            track_id = item.get("uid") or item.get("id") or (item.get("uri", "").split(":")[-1] if item.get("uri") else "")
            track_url = f"https://open.spotify.com/track/{track_id}" if track_id else original_url

            album_name = item.get("album") or playlist_title

            tracks.append(
                TrackMetadata(
                    title=title,
                    artists=artists,
                    album=album_name,
                    album_artist=artists[0] if artists else "Various Artists",
                    duration_seconds=duration_sec,
                    track_number=idx,
                    total_tracks=total,
                    cover_url=track_cover,
                    source_url=track_url,
                    source_id=track_id,
                    source_provider="spotify",
                )
            )

        return PlaylistInfo(
            title=playlist_title,
            author=author,
            description=description,
            cover_url=cover_url,
            tracks=tracks,
            source_url=original_url,
            source_type="spotify_playlist",
        )

    @classmethod
    def _parse_embed_album(cls, entity: dict, original_url: str) -> PlaylistInfo:
        album_title = entity.get("name") or entity.get("title") or "Spotify Album"
        author = entity.get("subtitle") or "Unknown Artist"

        cover_url = cls.extract_cover_art(entity)

        raw_tracks = entity.get("trackList", [])
        tracks: List[TrackMetadata] = []

        total = len(raw_tracks)
        for idx, item in enumerate(raw_tracks, 1):
            title = item.get("title") or item.get("name") or f"Track {idx}"
            subtitle = item.get("subtitle") or author
            artists = [a.strip() for a in subtitle.split(",") if a.strip()] if subtitle else [author]
            duration_ms = item.get("duration", 0) or 0
            duration_sec = duration_ms / 1000.0 if duration_ms else 0.0

            track_cover = cls.extract_cover_art(item) or cover_url
            track_id = item.get("uid") or item.get("id") or (item.get("uri", "").split(":")[-1] if item.get("uri") else "")
            track_url = f"https://open.spotify.com/track/{track_id}" if track_id else original_url

            tracks.append(
                TrackMetadata(
                    title=title,
                    artists=artists,
                    album=album_title,
                    album_artist=author,
                    duration_seconds=duration_sec,
                    track_number=idx,
                    total_tracks=total,
                    cover_url=track_cover,
                    source_url=track_url,
                    source_id=track_id,
                    source_provider="spotify",
                )
            )

        return PlaylistInfo(
            title=album_title,
            author=author,
            description=f"Album by {author}",
            cover_url=cover_url,
            tracks=tracks,
            source_url=original_url,
            source_type="spotify_album",
        )

    @classmethod
    def _parse_embed_artist(cls, entity: dict, artist_id: str, original_url: str) -> PlaylistInfo:
        artist_name = entity.get("name") or entity.get("title") or "Spotify Artist"
        raw_tracks = entity.get("trackList", [])

        tracks: List[TrackMetadata] = []
        total = len(raw_tracks)
        for idx, item in enumerate(raw_tracks, 1):
            title = item.get("title") or item.get("name") or f"Track {idx}"
            artists = [artist_name]
            duration_ms = item.get("duration", 0) or 0
            duration_sec = duration_ms / 1000.0 if duration_ms else 0.0

            tracks.append(
                TrackMetadata(
                    title=title,
                    artists=artists,
                    album=f"{artist_name} - Top Tracks",
                    album_artist=artist_name,
                    duration_seconds=duration_sec,
                    track_number=idx,
                    total_tracks=total,
                    source_url=original_url,
                    source_provider="spotify",
                )
            )

        return PlaylistInfo(
            title=f"{artist_name} - Popular Tracks",
            author=artist_name,
            description=f"Top tracks for {artist_name}",
            tracks=tracks,
            source_url=original_url,
            source_type="spotify_artist",
        )

    @classmethod
    def _fetch_via_oembed(cls, entity_type: str, entity_id: str, original_url: str) -> PlaylistInfo:
        oembed_url = f"https://open.spotify.com/oembed?url={urllib.parse.quote(original_url)}"
        headers = {"User-Agent": cls.USER_AGENT}

        resp = requests.get(oembed_url, headers=headers, timeout=10)
        resp.raise_for_status()
        data = resp.json()

        title = data.get("title", "Unknown")
        thumbnail = data.get("thumbnail_url")

        track = TrackMetadata(
            title=title,
            artists=["Spotify Artist"],
            album="Spotify",
            cover_url=thumbnail,
            source_url=original_url,
            source_id=entity_id,
            source_provider="spotify",
        )

        return PlaylistInfo(
            title=title,
            author="Spotify",
            cover_url=thumbnail,
            tracks=[track],
            source_url=original_url,
            source_type="spotify_track",
        )

    @classmethod
    def _fetch_via_api(cls, entity_type: str, entity_id: str, client_id: str, client_secret: str, original_url: str) -> PlaylistInfo:
        import spotipy
        from spotipy.oauth2 import SpotifyClientCredentials

        auth_mgr = SpotifyClientCredentials(client_id=client_id, client_secret=client_secret)
        sp = spotipy.Spotify(client_credentials_manager=auth_mgr)

        if entity_type == "track":
            t = sp.track(entity_id)
            title = t.get("name")
            artists = [a.get("name") for a in t.get("artists", [])]
            album = t.get("album", {}).get("name", "Single")
            duration_sec = (t.get("duration_ms", 0)) / 1000.0
            images = t.get("album", {}).get("images", [])
            cover_url = images[0].get("url") if images else None
            release_date = t.get("album", {}).get("release_date", "")

            track = TrackMetadata(
                title=title,
                artists=artists,
                album=album,
                album_artist=artists[0] if artists else "Unknown",
                duration_seconds=duration_sec,
                release_year=release_date[:4] if release_date else None,
                cover_url=cover_url,
                source_url=original_url,
                source_id=entity_id,
                source_provider="spotify",
            )
            return PlaylistInfo(title=title, author=artists[0] if artists else "Spotify", tracks=[track], source_url=original_url, source_type="spotify_track")

        elif entity_type == "playlist":
            p = sp.playlist(entity_id)
            title = p.get("name")
            author = p.get("owner", {}).get("display_name", "Spotify")
            images = p.get("images", [])
            cover_url = images[0].get("url") if images else None

            tracks: List[TrackMetadata] = []
            results = p.get("tracks", {})
            raw_items = results.get("items", [])

            while results.get("next"):
                results = sp.next(results)
                raw_items.extend(results.get("items", []))

            total = len(raw_items)
            for idx, item in enumerate(raw_items, 1):
                t = item.get("track")
                if not t:
                    continue
                t_name = t.get("name", f"Track {idx}")
                t_artists = [a.get("name") for a in t.get("artists", [])]
                t_album = t.get("album", {}).get("name", title)
                t_dur = (t.get("duration_ms", 0)) / 1000.0
                t_imgs = t.get("album", {}).get("images", [])
                t_cover = t_imgs[0].get("url") if t_imgs else cover_url
                t_rel = t.get("album", {}).get("release_date", "")

                tracks.append(
                    TrackMetadata(
                        title=t_name,
                        artists=t_artists,
                        album=t_album,
                        album_artist=t_artists[0] if t_artists else "Unknown",
                        duration_seconds=t_dur,
                        track_number=idx,
                        total_tracks=total,
                        release_year=t_rel[:4] if t_rel else None,
                        cover_url=t_cover,
                        source_url=t.get("external_urls", {}).get("spotify", original_url),
                        source_id=t.get("id"),
                        source_provider="spotify",
                    )
                )

            return PlaylistInfo(title=title, author=author, cover_url=cover_url, tracks=tracks, source_url=original_url, source_type="spotify_playlist")

        elif entity_type == "album":
            a = sp.album(entity_id)
            title = a.get("name")
            artists = [art.get("name") for art in a.get("artists", [])]
            author = artists[0] if artists else "Unknown"
            images = a.get("images", [])
            cover_url = images[0].get("url") if images else None
            release_date = a.get("release_date", "")

            tracks = []
            results = a.get("tracks", {})
            raw_items = results.get("items", [])
            while results.get("next"):
                results = sp.next(results)
                raw_items.extend(results.get("items", []))

            total = len(raw_items)
            for idx, t in enumerate(raw_items, 1):
                t_name = t.get("name", f"Track {idx}")
                t_artists = [art.get("name") for art in t.get("artists", [])] or artists
                t_dur = (t.get("duration_ms", 0)) / 1000.0

                tracks.append(
                    TrackMetadata(
                        title=t_name,
                        artists=t_artists,
                        album=title,
                        album_artist=author,
                        duration_seconds=t_dur,
                        track_number=idx,
                        total_tracks=total,
                        release_year=release_date[:4] if release_date else None,
                        cover_url=cover_url,
                        source_url=t.get("external_urls", {}).get("spotify", original_url),
                        source_id=t.get("id"),
                        source_provider="spotify",
                    )
                )

            return PlaylistInfo(title=title, author=author, cover_url=cover_url, tracks=tracks, source_url=original_url, source_type="spotify_album")

        elif entity_type == "artist":
            top = sp.artist_top_tracks(entity_id)
            artist = sp.artist(entity_id)
            artist_name = artist.get("name", "Artist")
            images = artist.get("images", [])
            cover_url = images[0].get("url") if images else None

            raw_tracks = top.get("tracks", [])
            tracks = []
            total = len(raw_tracks)
            for idx, t in enumerate(raw_tracks, 1):
                t_name = t.get("name")
                t_artists = [a.get("name") for a in t.get("artists", [])]
                t_album = t.get("album", {}).get("name", "Top Tracks")
                t_dur = (t.get("duration_ms", 0)) / 1000.0
                t_imgs = t.get("album", {}).get("images", [])
                t_cover = t_imgs[0].get("url") if t_imgs else cover_url

                tracks.append(
                    TrackMetadata(
                        title=t_name,
                        artists=t_artists,
                        album=t_album,
                        album_artist=artist_name,
                        duration_seconds=t_dur,
                        track_number=idx,
                        total_tracks=total,
                        cover_url=t_cover,
                        source_url=t.get("external_urls", {}).get("spotify", original_url),
                        source_id=t.get("id"),
                        source_provider="spotify",
                    )
                )

            return PlaylistInfo(title=f"{artist_name} - Top Tracks", author=artist_name, cover_url=cover_url, tracks=tracks, source_url=original_url, source_type="spotify_artist")

        raise ValueError(f"Unknown entity type: {entity_type}")
