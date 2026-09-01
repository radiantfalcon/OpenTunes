import argparse
import sys
import time
from pathlib import Path
from typing import List, Optional
from rich.console import Console
from rich.progress import (
    BarColumn,
    MofNCompleteColumn,
    Progress,
    SpinnerColumn,
    TaskID,
    TextColumn,
    TimeRemainingColumn,
    TransferSpeedColumn,
)

from opentunes.core.config import get_download_options, load_config
from opentunes.core.models import DownloadOptions, DownloadProgress, PlaylistInfo, TrackStatus
from opentunes.core.pipeline import DownloadPipeline
from opentunes.ui.mobile import MobileUI
from opentunes.ui.progress import DownloadUI
from opentunes.utils.system import (
    check_internet_connection,
    get_default_music_dir,
    get_ffmpeg_install_instructions,
    get_ffmpeg_path,
    get_platform_name,
    is_ffmpeg_available,
    is_narrow_screen,
)

console = Console()

def run_diagnostics():
    console.print("\n[bold white]=== [ OPENTUNES SYSTEM DIAGNOSTICS ] ===[/bold white]")
    console.print("═" * 45)

    plat = get_platform_name()
    console.print(f"• [bold white]PLATFORM:[/bold white] [white]{plat}[/white]")
    console.print(f"• [bold white]PYTHON VERSION:[/bold white] [white]{sys.version.split()[0]}[/white]")

    if is_ffmpeg_available():
        fpath = get_ffmpeg_path()
        console.print(f"• [bold white]FFMPEG:[/bold white] [bold white][ OK ][/bold white] ({fpath})")
    else:
        console.print("• [bold white]FFMPEG:[/bold white] [bold white][ NOT FOUND ][/bold white]")
        console.print(f"  [dim]Install command: {get_ffmpeg_install_instructions()}[/dim]")

    online = check_internet_connection()
    if online:
        console.print("• [bold white]INTERNET LINK:[/bold white] [bold white][ ONLINE ][/bold white]")
    else:
        console.print("• [bold white]INTERNET LINK:[/bold white] [bold white][ OFFLINE ][/bold white]")

    out_dir = get_default_music_dir()
    console.print(f"• [bold white]DEFAULT MUSIC FOLDER:[/bold white] [white]{out_dir}[/white]")
    console.print("═" * 45 + "\n")

def execute_download(
    query_or_url: str,
    options: DownloadOptions,
    is_batch: bool = False,
    mobile: bool = False,
) -> int:
    ui = DownloadUI(is_mobile=options.mobile_mode or mobile)
    mobile_ui = MobileUI()

    if not is_ffmpeg_available():
        ui.display_error_popup(
            title="FFmpeg Missing",
            message="FFmpeg is required to convert and encode audio files.",
            hint=f"Install FFmpeg using: {get_ffmpeg_install_instructions()}",
        )
        return 1

    cfg = load_config()
    sp_id = cfg.get("spotify_client_id", "")
    sp_secret = cfg.get("spotify_client_secret", "")

    with console.status("[bold white]Fetching metadata...[/bold white]", spinner="dots"):
        try:
            playlist = DownloadPipeline.resolve_input(
                query_or_url,
                spotify_client_id=sp_id,
                spotify_client_secret=sp_secret,
            )
        except Exception as e:
            ui.display_error_popup(
                title="Metadata Resolution Failed",
                message=str(e),
                hint="Check the URL format or ensure your network connection is active.",
            )
            return 1

    if not playlist.tracks:
        ui.display_error_popup(
            title="No Tracks Found",
            message="The provided link or query did not contain any playable tracks.",
        )
        return 1

    folder_name = playlist.title if playlist.total_tracks > 1 else ""
    target_dir = options.output_dir / folder_name if folder_name else options.output_dir

    if options.mobile_mode or mobile:
        mobile_ui.print_header(f"{playlist.title} ({playlist.total_tracks} tracks)")
    else:
        ui.display_playlist_header(playlist, target_dir, options.format, options.bitrate)

    successful = 0
    failed = 0

    if options.mobile_mode or mobile:
        for idx, track in enumerate(playlist.tracks, 1):
            mobile_ui.print_track_start(idx, playlist.total_tracks, track.title, track.primary_artist)
            try:
                out_path = DownloadPipeline.process_track(
                    track,
                    options,
                    folder_name=folder_name,
                    total_tracks=playlist.total_tracks,
                    index=idx,
                )
                mobile_ui.print_success(out_path.name)
                successful += 1
            except Exception as e:
                mobile_ui.print_error(str(e))
                failed += 1

        mobile_ui.print_summary(successful, playlist.total_tracks, target_dir)
        return 0 if failed == 0 else 1

    with Progress(
        SpinnerColumn(),
        TextColumn("[bold white]{task.description}[/bold white]"),
        BarColumn(bar_width=30, complete_style="bold white", finished_style="white"),
        MofNCompleteColumn(),
        TextColumn("[progress.percentage]{task.percentage:>3.0f}%"),
        TimeRemainingColumn(),
        console=console,
    ) as progress:
        overall_task = progress.add_task(
            "Overall Playlist",
            total=playlist.total_tracks,
        )
        current_task = progress.add_task(
            "Current Track",
            total=100,
            visible=True,
        )

        for idx, track in enumerate(playlist.tracks, 1):
            track_label = f"[{idx}/{playlist.total_tracks}] {track.primary_artist} - {track.title}"
            if len(track_label) > 40:
                track_label = track_label[:37] + "..."

            progress.update(
                current_task,
                description=f"Matching: {track_label}",
                completed=0,
            )

            def progress_callback(p: DownloadProgress):
                if p.status == TrackStatus.MATCHING:
                    progress.update(current_task, description=f"Matching: {track_label}")
                elif p.status == TrackStatus.DOWNLOADING:
                    speed = f" | {p.speed_str}" if p.speed_str else ""
                    progress.update(
                        current_task,
                        description=f"Downloading: {track_label}{speed}",
                        completed=p.download_percent,
                    )
                elif p.status == TrackStatus.CONVERTING:
                    progress.update(current_task, description=f"Converting: {track_label}", completed=100)
                elif p.status == TrackStatus.TAGGING:
                    progress.update(current_task, description=f"Tagging & Art: {track_label}", completed=100)

            try:
                DownloadPipeline.process_track(
                    track,
                    options,
                    folder_name=folder_name,
                    total_tracks=playlist.total_tracks,
                    index=idx,
                    progress_callback=progress_callback,
                )
                successful += 1
            except Exception as e:
                console.print(f"\n[bold white][ ERROR ] Failed to download {track.title}: {e}[/bold white]")
                failed += 1

            progress.update(current_task, completed=100)
            progress.update(overall_task, advance=1)

    if not is_batch:
        ui.display_completion_summary(successful, failed, playlist.total_tracks, target_dir)

    return 0 if failed == 0 else 1
