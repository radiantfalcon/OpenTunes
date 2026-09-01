import os
import sys
from pathlib import Path
from rich.console import Console
from rich.prompt import Prompt

from opentunes.core.config import get_download_options, load_config, save_config
from opentunes.core.models import DownloadProgress, PlaylistInfo, TrackStatus
from opentunes.utils.system import get_default_music_dir

class MobileUI:

    def __init__(self):
        self.console = Console()

    def print_header(self, title: str):
        self.console.print(f"\n[bold white]{title}[/bold white]")

    def print_track_start(self, index: int, total: int, title: str, artist: str):
        self.console.print(f"[bold white][{index:02d}/{total:02d}][/bold white] [white]{artist} - {title}[/white]")

    def print_status(self, status: str, detail: str = ""):
        if detail:
            self.console.print(f"  {status}: {detail}")
        else:
            self.console.print(f"  {status}")

    def print_success(self, filename: str):
        self.console.print(f"  [bold white][ OK ][/bold white] {filename}")

    def print_error(self, message: str):
        self.console.print(f"  [bold white][ FAIL ][/bold white] {message}")

    def print_summary(self, success: int, total: int, out_dir: Path):
        self.console.print(f"\n[bold white]Done: {success}/{total} tracks saved to {out_dir}[/bold white]\n")

class MobileTUI:

    def __init__(self):
        self.console = Console()
        self.ui = MobileUI()

    def start(self):
        from opentunes.ui.cli import execute_download

        while True:
            self.console.clear()
            self._print_header()

            choice = Prompt.ask(
                "\n[bold white]Choice[/bold white]",
                choices=["1", "2", "3", "4", "5"],
                default="1",
            )

            if choice == "1":
                query = Prompt.ask("\n[bold white]Enter URL or song name[/bold white]").strip()
                if query:
                    options = get_download_options()
                    self.console.print("")
                    execute_download(query, options, mobile=True)
                    Prompt.ask("\n[dim]Press Enter to continue...[/dim]")

            elif choice == "2":
                file_str = Prompt.ask("\n[bold white]Enter text file path[/bold white]").strip()
                if file_str:
                    batch_file = Path(file_str).expanduser()
                    if not batch_file.exists():
                        self.console.print(f"[bold white][ ERROR ] File not found: {batch_file}[/bold white]")
                        Prompt.ask("\n[dim]Press Enter to continue...[/dim]")
                        continue

                    with open(batch_file, "r", encoding="utf-8") as f:
                        urls = [l.strip() for l in f if l.strip() and not l.startswith("#")]

                    if not urls:
                        self.console.print("[bold white][ ERROR ] No URLs found.[/bold white]")
                        Prompt.ask("\n[dim]Press Enter to continue...[/dim]")
                        continue

                    options = get_download_options()
                    self.console.print(f"\n[bold white]{len(urls)} items to download.[/bold white]\n")
                    for idx, u in enumerate(urls, 1):
                        self.console.print(f"\n[bold white]Item {idx}/{len(urls)}[/bold white]")
                        execute_download(u, options, mobile=True)
                    Prompt.ask("\n[dim]Press Enter to continue...[/dim]")

            elif choice == "3":
                self._handle_settings()

            elif choice == "4":
                from opentunes.ui.cli import run_diagnostics
                self.console.clear()
                run_diagnostics()
                Prompt.ask("\n[dim]Press Enter to continue...[/dim]")

            elif choice == "5":
                sys.exit(0)

    def _print_header(self):
        cfg = load_config()
        fmt = cfg.get("format", "mp3").upper()
        bitrate = cfg.get("bitrate", "320k")
        out_dir = cfg.get("output_dir", str(get_default_music_dir()))

        self.console.print("[bold white]OPENTUNES[/bold white]")
        self.console.print(f"[dim]{fmt} {bitrate} | {out_dir}[/dim]\n")
        self.console.print("[bold white]1.[/bold white] Download URL or Search")
        self.console.print("[bold white]2.[/bold white] Batch Download (.txt)")
        self.console.print("[bold white]3.[/bold white] Settings")
        self.console.print("[bold white]4.[/bold white] Diagnostics")
        self.console.print("[bold white]5.[/bold white] Exit")

    def _handle_settings(self):
        cfg = load_config()
        while True:
            self.console.clear()
            self.console.print("[bold white]SETTINGS[/bold white]\n")
            self.console.print(f"1. Format: [bold white]{cfg.get('format', 'mp3').upper()}[/bold white]")
            self.console.print(f"2. Bitrate: [bold white]{cfg.get('bitrate', '320k')}[/bold white]")
            self.console.print(f"3. Output Directory: [bold white]{cfg.get('output_dir', str(get_default_music_dir()))}[/bold white]")
            self.console.print(f"4. Lyrics: [bold white]{'ON' if cfg.get('fetch_lyrics', True) else 'OFF'}[/bold white]")
            self.console.print(f"5. Synced .LRC: [bold white]{'ON' if cfg.get('save_lrc', False) else 'OFF'}[/bold white]")
            self.console.print("6. Back")

            c = Prompt.ask("\n[bold white]Select[/bold white]", choices=["1", "2", "3", "4", "5", "6"], default="6")
            if c == "1":
                cfg["format"] = Prompt.ask("Format", choices=["mp3", "flac", "opus", "wav", "m4a"], default=cfg.get("format", "mp3"))
                save_config(cfg)
            elif c == "2":
                cfg["bitrate"] = Prompt.ask("Bitrate", choices=["320k", "256k", "192k", "128k"], default=cfg.get("bitrate", "320k"))
                save_config(cfg)
            elif c == "3":
                new_dir = Prompt.ask("Path", default=str(cfg.get("output_dir", get_default_music_dir())))
                p = Path(new_dir).expanduser()
                p.mkdir(parents=True, exist_ok=True)
                cfg["output_dir"] = str(p)
                save_config(cfg)
            elif c == "4":
                cfg["fetch_lyrics"] = not cfg.get("fetch_lyrics", True)
                save_config(cfg)
            elif c == "5":
                cfg["save_lrc"] = not cfg.get("save_lrc", False)
                save_config(cfg)
            elif c == "6":
                break
