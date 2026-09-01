from pathlib import Path
from typing import List, Optional
from rich import box
from rich.console import Console
from rich.panel import Panel
from rich.progress import (
    BarColumn,
    DownloadColumn,
    MofNCompleteColumn,
    Progress,
    SpinnerColumn,
    TaskID,
    TextColumn,
    TimeElapsedColumn,
    TimeRemainingColumn,
    TransferSpeedColumn,
)
from rich.table import Table
from rich.text import Text

from opentunes.core.models import DownloadProgress, PlaylistInfo, TrackMetadata, TrackStatus

console = Console()

class DownloadUI:

    def __init__(self, is_mobile: bool = False):
        self.is_mobile = is_mobile
        self.console = Console()
        self.progress: Optional[Progress] = None
        self.overall_task_id: Optional[TaskID] = None
        self.current_task_id: Optional[TaskID] = None

    def display_banner(self):
        banner_text = Text()
        banner_text.append("+===========================================+\n", style="bold white")
        banner_text.append("|            O P E N T U N E S              |\n", style="bold black on white")
        banner_text.append("|   High-Fidelity Music Downloader v1.0.0   |\n", style="bold white")
        banner_text.append("+===========================================+", style="bold white")
        self.console.print(banner_text)

    def display_playlist_header(self, playlist: PlaylistInfo, output_dir: Path, audio_fmt: str, bitrate: str):
        table = Table(show_header=False, box=box.HEAVY, padding=(0, 1))
        table.add_column("Key", style="bold white")
        table.add_column("Value", style="white")

        table.add_row("COLLECTION:", playlist.title)
        table.add_row("CREATOR:", playlist.author)
        table.add_row("TOTAL TRACKS:", str(playlist.total_tracks))
        table.add_row("DESTINATION:", str(output_dir))
        table.add_row("FORMAT / BITRATE:", f"{audio_fmt.upper()} ({bitrate})")

        panel = Panel(
            table,
            title="[bold white][ READY TO DOWNLOAD ][/bold white]",
            border_style="white",
            box=box.HEAVY,
            expand=False,
        )
        self.console.print(panel)

    def display_error_popup(self, title: str, message: str, hint: Optional[str] = None):
        err_text = Text()
        err_text.append(f"[ERR] {message}\n", style="bold white")
        if hint:
            err_text.append(f"\nHINT: {hint}", style="dim")

        panel = Panel(
            err_text,
            title=f"[bold white][ ERROR: {title.upper()} ][/bold white]",
            border_style="white",
            box=box.HEAVY,
            expand=False,
        )
        self.console.print(panel)

    def display_completion_summary(self, successful: int, failed: int, total: int, out_dir: Path):
        text = Text()
        text.append(f"[ BATCH COMPLETED ]\n\n", style="bold white")
        text.append(f"• SUCCESSFULLY DOWNLOADED: {successful} / {total}\n", style="bold white")
        if failed > 0:
            text.append(f"• FAILED TRACKS: {failed}\n", style="dim")
        text.append(f"• SAVED TO: {out_dir}\n", style="white underline")

        panel = Panel(
            text,
            title="[bold white][ STATUS: COMPLETE ][/bold white]",
            border_style="white",
            box=box.HEAVY,
            expand=False,
        )
        self.console.print(panel)
