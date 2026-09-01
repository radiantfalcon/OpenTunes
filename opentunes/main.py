import argparse
import sys
from pathlib import Path

from opentunes import __version__
from opentunes.core.config import get_download_options
from opentunes.ui.cli import execute_download, run_diagnostics
from opentunes.ui.mobile import MobileTUI
from opentunes.ui.progress import DownloadUI
from opentunes.ui.tui import OpenTunesApp
from opentunes.utils.system import is_narrow_screen, is_termux

def normalize_quality(q: str | None) -> str | None:
    if not q:
        return None
    q_low = q.lower().strip()
    if q_low in ("320", "320k"):
        return "320k"
    if q_low in ("256", "256k"):
        return "256k"
    if q_low in ("192", "192k"):
        return "192k"
    if q_low in ("128", "128k"):
        return "128k"
    if q_low in ("best", "lossless", "max"):
        return "best"
    return q

def parse_args():
    parser = argparse.ArgumentParser(
        prog="opentunes",
        description="OpenTunes - High-Quality Cross-Platform Music Downloader for Spotify & YouTube",
    )

    parser.add_argument(
        "url_or_query",
        nargs="?",
        default=None,
        help="Spotify/YouTube URL (track, playlist, album, artist) or song search query",
    )
    parser.add_argument(
        "-f",
        "--format",
        choices=["mp3", "flac", "wav", "opus", "m4a"],
        default=None,
        help="Target audio format (default: mp3)",
    )
    parser.add_argument(
        "-q",
        "--quality",
        "--bitrate",
        dest="bitrate",
        default=None,
        help="Audio quality / bitrate (e.g. 320k, 256k, 192k, 128k, best)",
    )
    parser.add_argument(
        "-o",
        "--output",
        dest="output_dir",
        default=None,
        help="Destination directory for downloaded music",
    )
    parser.add_argument(
        "--no-lyrics",
        dest="fetch_lyrics",
        action="store_false",
        default=None,
        help="Disable lyrics fetching and embedding",
    )
    parser.add_argument(
        "--lrc",
        dest="save_lrc",
        action="store_true",
        default=None,
        help="Save synced lyrics as external .lrc file",
    )
    parser.add_argument(
        "--batch",
        type=str,
        default=None,
        help="Path to text file with one URL/query per line",
    )
    parser.add_argument(
        "--search",
        type=str,
        default=None,
        help="Search for a song and download the best match",
    )
    parser.add_argument(
        "--mobile",
        "--simple",
        dest="mobile",
        action="store_true",
        default=False,
        help="Enable mobile / Termux compact simple UI mode",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        default=False,
        help="Force overwrite existing downloaded files",
    )
    parser.add_argument(
        "-i",
        "--interactive",
        "--tui",
        dest="tui",
        action="store_true",
        help="Launch full-screen interactive Textual TUI app",
    )
    parser.add_argument(
        "--check",
        "--diagnostics",
        dest="diagnostics",
        action="store_true",
        help="Run system and FFmpeg diagnostics",
    )
    parser.add_argument(
        "-v",
        "--version",
        action="version",
        version=f"OpenTunes {__version__}",
    )

    return parser.parse_args()

def main():
    try:
        import static_ffmpeg
        static_ffmpeg.add_paths()
    except Exception:
        pass

    args = parse_args()

    if args.diagnostics:
        run_diagnostics()
        sys.exit(0)

    if args.tui or (not args.url_or_query and not args.batch and not args.search):
        if not sys.stdin.isatty():
            print("OpenTunes: No input URL provided. Use 'opentunes --help' for usage.")
            sys.exit(1)

        if args.mobile or is_termux() or (is_narrow_screen() and not args.tui):
            mobile_tui = MobileTUI()
            mobile_tui.start()
            sys.exit(0)
        else:

            app = OpenTunesApp()
            app.run()
            sys.exit(0)

    bitrate = normalize_quality(args.bitrate)
    overrides = {
        "format": args.format,
        "bitrate": bitrate,
        "output_dir": args.output_dir,
        "fetch_lyrics": args.fetch_lyrics,
        "save_lrc": args.save_lrc,
        "overwrite": args.overwrite,
        "mobile_mode": args.mobile or is_narrow_screen(),
    }
    options = get_download_options(overrides)

    if args.search:
        code = execute_download(args.search, options, mobile=args.mobile)
        sys.exit(code)

    if args.batch:
        bpath = Path(args.batch).expanduser()
        if not bpath.exists():
            ui = DownloadUI()
            ui.display_error_popup("Batch File Not Found", f"Cannot open {bpath}")
            sys.exit(1)

        with open(bpath, "r", encoding="utf-8") as f:
            urls = [line.strip() for line in f if line.strip() and not line.startswith("#")]

        for u in urls:
            execute_download(u, options, is_batch=True, mobile=args.mobile)
        sys.exit(0)

    if args.url_or_query:
        code = execute_download(args.url_or_query, options, mobile=args.mobile)
        sys.exit(code)

if __name__ == "__main__":
    main()
