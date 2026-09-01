import base64
import subprocess
import tempfile
from pathlib import Path
from typing import Optional
import requests

from mutagen.flac import FLAC, Picture
from mutagen.id3 import (
    APIC,
    ID3,
    TALB,
    TCON,
    TDRC,
    TIT2,
    TPE1,
    TPE2,
    TPOS,
    TRCK,
    USLT,
    ID3NoHeaderError,
)
from mutagen.mp4 import MP4, MP4Cover
from mutagen.oggopus import OggOpus

from opentunes.core.models import TrackMetadata
from opentunes.utils.system import get_ffmpeg_path

class AudioTagger:

    USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"

    @classmethod
    def tag_file(cls, file_path: Path, track: TrackMetadata, lyrics: Optional[str] = None) -> bool:
        if not file_path.exists():
            return False

        ext = file_path.suffix.lower()
        art_bytes, mime_type = cls._fetch_artwork(track.cover_url)

        try:
            if ext == ".mp3":
                cls._tag_mp3(file_path, track, art_bytes, mime_type, lyrics)
            elif ext == ".flac":
                cls._tag_flac(file_path, track, art_bytes, mime_type, lyrics)
            elif ext in (".opus", ".ogg"):
                cls._tag_opus(file_path, track, art_bytes, mime_type, lyrics)
            elif ext in (".m4a", ".aac"):
                cls._tag_m4a(file_path, track, art_bytes, mime_type, lyrics)
            elif ext == ".wav":
                cls._tag_wav(file_path, track, art_bytes, mime_type, lyrics)
            return True
        except Exception:
            return False

    @classmethod
    def _fetch_artwork(cls, url: Optional[str]) -> tuple[Optional[bytes], str]:
        if not url:
            return None, "image/jpeg"
        try:
            headers = {"User-Agent": cls.USER_AGENT}
            resp = requests.get(url, headers=headers, timeout=8)
            if resp.status_code == 200:
                raw_bytes = resp.content
                if not raw_bytes:
                    return None, "image/jpeg"

                opt_bytes, mime = cls._optimize_artwork(raw_bytes)
                return opt_bytes, mime
        except Exception:
            pass
        return None, "image/jpeg"

    @classmethod
    def _optimize_artwork(cls, raw_bytes: bytes) -> tuple[bytes, str]:
        try:
            with tempfile.NamedTemporaryFile(suffix=".img", delete=False) as f_in:
                f_in.write(raw_bytes)
                f_in_path = f_in.name

            f_out_path = f_in_path + ".jpg"
            ff_exe = get_ffmpeg_path() or "ffmpeg"
            subprocess.run(
                [
                    ff_exe, "-y", "-i", f_in_path,
                    "-vf", "scale='min(1000,iw)':-2",
                    "-q:v", "2",
                    "-pix_fmt", "yuvj420p",
                    f_out_path,
                ],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            with open(f_out_path, "rb") as f_out:
                jpg_bytes = f_out.read()

            Path(f_in_path).unlink(missing_ok=True)
            Path(f_out_path).unlink(missing_ok=True)
            return jpg_bytes, "image/jpeg"
        except Exception:
            return raw_bytes, "image/jpeg"

    @classmethod
    def _tag_mp3(cls, path: Path, track: TrackMetadata, art: Optional[bytes], mime: str, lyrics: Optional[str]):
        try:
            old_tags = ID3(path)
            old_tags.delete()
        except Exception:
            pass

        tags = ID3()

        artist_text = track.artist_str
        album_artist_text = track.album_artist or track.primary_artist

        tags.setall("TIT2", [TIT2(encoding=1, text=track.title)])
        tags.setall("TPE1", [TPE1(encoding=1, text=artist_text)])
        tags.setall("TALB", [TALB(encoding=1, text=track.album)])
        tags.setall("TPE2", [TPE2(encoding=1, text=album_artist_text)])

        if track.total_tracks > 1:
            tags.setall("TRCK", [TRCK(encoding=1, text=f"{track.track_number}/{track.total_tracks}")])
        else:
            tags.setall("TRCK", [TRCK(encoding=1, text=str(track.track_number))])

        if track.disc_number:
            tags.setall("TPOS", [TPOS(encoding=1, text=str(track.disc_number))])

        if track.release_year:
            tags.setall("TDRC", [TDRC(encoding=1, text=str(track.release_year))])

        if track.genre:
            tags.setall("TCON", [TCON(encoding=1, text=track.genre)])

        song_lyrics = lyrics or track.lyrics
        if song_lyrics:
            tags.setall("USLT", [USLT(encoding=1, lang="eng", desc="", text=song_lyrics)])

        if art:
            tags.setall("APIC", [APIC(encoding=1, mime="image/jpeg", type=3, desc="Cover", data=art)])

        tags.save(path, v2_version=3, v1=2)

    @classmethod
    def _tag_flac(cls, path: Path, track: TrackMetadata, art: Optional[bytes], mime: str, lyrics: Optional[str]):
        audio = FLAC(path)
        audio["title"] = [track.title]
        audio["artist"] = track.artists if track.artists else [track.primary_artist]
        audio["album"] = [track.album]
        audio["albumartist"] = [track.album_artist or track.primary_artist]
        audio["tracknumber"] = [str(track.track_number)]
        if track.total_tracks:
            audio["tracktotal"] = [str(track.total_tracks)]
        if track.release_year:
            audio["date"] = [str(track.release_year)]
        if track.genre:
            audio["genre"] = [track.genre]

        song_lyrics = lyrics or track.lyrics
        if song_lyrics:
            audio["lyrics"] = [song_lyrics]

        if art:
            pic = Picture()
            pic.data = art
            pic.type = 3
            pic.mime = "image/jpeg"
            pic.desc = "Cover"
            audio.clear_pictures()
            audio.add_picture(pic)

        audio.save()

    @classmethod
    def _tag_opus(cls, path: Path, track: TrackMetadata, art: Optional[bytes], mime: str, lyrics: Optional[str]):
        audio = OggOpus(path)
        audio["title"] = [track.title]
        audio["artist"] = track.artists if track.artists else [track.primary_artist]
        audio["album"] = [track.album]
        audio["albumartist"] = [track.album_artist or track.primary_artist]
        audio["tracknumber"] = [str(track.track_number)]
        if track.total_tracks:
            audio["tracktotal"] = [str(track.total_tracks)]
        if track.release_year:
            audio["date"] = [str(track.release_year)]
        if track.genre:
            audio["genre"] = [track.genre]

        song_lyrics = lyrics or track.lyrics
        if song_lyrics:
            audio["lyrics"] = [song_lyrics]

        if art:
            pic = Picture()
            pic.data = art
            pic.type = 3
            pic.mime = "image/jpeg"
            pic.desc = "Cover"
            encoded_pic = base64.b64encode(pic.write()).decode("ascii")
            audio["metadata_block_picture"] = [encoded_pic]

        audio.save()

    @classmethod
    def _tag_m4a(cls, path: Path, track: TrackMetadata, art: Optional[bytes], mime: str, lyrics: Optional[str]):
        audio = MP4(path)
        audio["\xa9nam"] = [track.title]
        audio["\xa9ART"] = [track.artist_str]
        audio["\xa9alb"] = [track.album]
        audio["aART"] = [track.album_artist or track.primary_artist]
        audio["trkn"] = [(track.track_number, track.total_tracks or 1)]

        if track.release_year:
            audio["\xa9day"] = [str(track.release_year)]
        if track.genre:
            audio["\xa9gen"] = [track.genre]

        song_lyrics = lyrics or track.lyrics
        if song_lyrics:
            audio["\xa9lyr"] = [song_lyrics]

        if art:
            audio["covr"] = [MP4Cover(art, imageformat=MP4Cover.FORMAT_JPEG)]

        audio.save()

    @classmethod
    def _tag_wav(cls, path: Path, track: TrackMetadata, art: Optional[bytes], mime: str, lyrics: Optional[str]):
        try:
            from mutagen.wave import WAVE
            audio = WAVE(path)
            if audio.tags is None:
                audio.add_tags()
            tags = audio.tags
            tags.setall("TIT2", [TIT2(encoding=1, text=track.title)])
            tags.setall("TPE1", [TPE1(encoding=1, text=track.artist_str)])
            tags.setall("TALB", [TALB(encoding=1, text=track.album)])
            tags.setall("TRCK", [TRCK(encoding=1, text=str(track.track_number))])
            if art:
                tags.setall("APIC", [APIC(encoding=1, mime="image/jpeg", type=3, desc="Cover", data=art)])
            audio.save()
        except Exception:
            pass
