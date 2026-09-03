import asyncio
import os
import shutil
import sys
import time
from pathlib import Path
from typing import Dict, List, Optional

from rich.text import Text
from textual import work
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Center, Container, Grid, Horizontal, Middle, ScrollableContainer, Vertical
from textual.widgets import (
    Button,
    DataTable,
    Footer,
    Header,
    Input,
    Label,
    ProgressBar,
    Static,
)

from opentunes import __version__
from opentunes.core.config import get_download_options, load_config, save_config
from opentunes.core.models import DownloadOptions, DownloadProgress, PlaylistInfo, TrackMetadata, TrackStatus
from opentunes.core.pipeline import DownloadPipeline
from opentunes.utils.formatting import format_duration
from opentunes.utils.system import (
    check_internet_connection,
    get_default_music_dir,
    get_ffmpeg_path,
    is_ffmpeg_available,
)

BANNER_BLOCK = """                                          ▄▄                                           
                                          ▓▀ ▀                                         
  ▄▄▓▀▓▄▄ ▐▒▀ ▄▀▓▄▄   ▄▄▓▀▓▄▄ ▐▒▀ ▄▀▓▄▄ ▄▄▒ ▓▄▄▐▓▀░   ░ ▄▐▒▀ ▄▀▓▄▄   ▄▄▓▀▓▄▄   ▄▄▓▀▓▄▄ 
 ▒▀    ▒▀ ▐░     ░   ▒▀    ░  ▐░     ░    ░    ▐░     ░  ▐░     ░   ▒▀    ░  ▐▒▀    ▀▀▀
▐░ ▓   ░ ▓▐░▀░   ░▄ ▐░ ▓▀▀▀▀▀▀▐░▀░   ░▄  ░▒▄░  ▐▒▄▓   ▒▄▓▐░▀░   ░▄ ▐░ ▓▀▀▀▀▀▀ ▀▀▀▀▀▀░▒▄
 ▓░▄   ░▓█ ▒█▒   ▒█▓ ▓░▄   ░▓█ ▒█▒   ▒█▓  ▓█▒  ▐▓█▓   ▓▀▓ ▒█▒   ▒█▓ ▓░▄   ░▓█ ▓░▄   ░▓█
  ▀▀▓▄▓▀▀  ▓▓▓ ▀▄▓▀▀  ▀▀▓▄▓▀▀  ▓▓▓   ▓▀▓  ▀▓▓▄  ▀██▀▄███▀ ▓▓▓   ▓▀▓  ▀▀▓▄▓▀▀   ▀▀▓▄▓▀▀ 
           ▀▓█                                                                         
             ▀                                                                         """

class OpenTunesApp(App):

    TITLE = f"OPENTUNES v{__version__}"
    SUB_TITLE = ""

    CSS = """
    Screen {
        background: #000000;
        color: #ffffff;
        align: center top;
        overflow-x: auto;
    }

    Header {
        background: #000000;
        color: #ffffff;
        text-style: bold;
        border-bottom: solid #333333;
        height: 1;
    }

    Footer {
        background: #000000;
        color: #666666;
        border-top: solid #333333;
        height: 1;
    }

    
    .top-tab-bar {
        align: center middle;
        height: 3;
        width: 100%;
        background: #000000;
        border-bottom: solid #333333;
    }

    .tab-btn {
        margin: 0 1;
        background: #000000;
        color: #737373;
        border: solid #333333;
        text-style: bold;
        min-width: 24;
        text-align: center;
    }

    .tab-btn:hover {
        border: solid #737373;
        color: #ffffff;
        background: #111111;
    }

    .tab-btn:focus {
        border: solid #ffffff;
        color: #ffffff;
        background: #222222;
    }

    .tab-btn-active {
        margin: 0 1;
        background: #ffffff;
        color: #000000;
        border: solid #ffffff;
        text-style: bold;
        min-width: 24;
        text-align: center;
    }

    .tab-btn-active:hover {
        background: #d4d4d4;
        border: solid #ffffff;
        color: #000000;
    }

    .tab-btn-active:focus {
        background: #ffffff;
        border: solid #ffffff;
        color: #000000;
        text-style: bold;
    }

    
    .tab-pane-view {
        align: center top;
        width: 100%;
        height: 1fr;
        padding: 1 0;
        overflow-y: auto;
    }

    .banner-text {
        text-align: center;
        color: #ffffff;
        text-style: bold;
        margin-top: 1;
        margin-bottom: 2;
        width: auto;
        height: auto;
    }

    .section-header {
        text-align: center;
        color: #ffffff;
        text-style: bold;
        margin-top: 0;
        margin-bottom: 1;
        width: 100%;
    }

    
    .center-input {
        border: solid #333333;
        background: #000000;
        color: #ffffff;
        width: 72;
        text-align: center;
        margin-top: 1;
    }

    .center-input:focus {
        border: solid #ffffff;
        background: #0a0a0a;
    }

    .settings-input {
        border: solid #333333;
        background: #000000;
        color: #ffffff;
        width: 66;
        text-align: center;
        margin-top: 0;
        margin-bottom: 0;
    }

    .settings-input:focus {
        border: solid #ffffff;
        background: #0a0a0a;
    }

    
    .btn-row {
        align: center middle;
        width: 100%;
        height: 3;
        margin: 1 0;
    }

    .btn-primary {
        border: solid #ffffff;
        background: #ffffff;
        color: #000000;
        margin: 0 1;
        text-style: bold;
        min-width: 20;
        text-align: center;
    }

    .btn-primary:hover {
        background: #d4d4d4;
        border: solid #ffffff;
        color: #000000;
        text-style: bold;
    }

    .btn-primary:focus {
        background: #ffffff;
        border: solid #ffffff;
        color: #000000;
        text-style: bold;
    }

    .btn-secondary {
        border: solid #333333;
        background: #000000;
        color: #ffffff;
        margin: 0 1;
        text-style: bold;
        min-width: 16;
        text-align: center;
    }

    .btn-secondary:hover {
        border: solid #666666;
        background: #111111;
        color: #ffffff;
    }

    .btn-secondary:focus {
        border: solid #ffffff;
        color: #ffffff;
        background: #222222;
    }

    
    .monitor-card {
        border: solid #ffffff;
        background: #000000;
        padding: 1 2;
        width: 72;
        height: auto;
        margin: 1 0;
        align: center middle;
    }

    .monitor-header {
        color: #ffffff;
        text-style: bold;
        text-align: center;
        width: 100%;
        margin-bottom: 1;
    }

    .monitor-track {
        color: #ffffff;
        text-align: center;
        width: 100%;
        margin-bottom: 1;
    }

    .monitor-status {
        color: #888888;
        text-align: center;
        width: 100%;
        margin-top: 1;
    }

    .monitor-bar {
        margin: 0 0 1 0;
        width: 58;
    }

    
    .search-table {
        border: solid #333333;
        background: #000000;
        width: 72;
        height: 12;
        margin: 1 0;
    }

    .search-table:focus {
        border: solid #ffffff;
    }

    
    .settings-card {
        border: solid #333333;
        background: #000000;
        padding: 0 1;
        width: 72;
        height: auto;
        margin: 0 0;
        align: center middle;
    }

    .settings-subheader {
        color: #ffffff;
        text-style: bold;
        text-align: center;
        width: 100%;
        margin-top: 0;
        margin-bottom: 0;
    }

    .toggle-row {
        align: center middle;
        width: 100%;
        height: 3;
        margin-bottom: 0;
    }

    .toggle-btn {
        border: solid #333333;
        background: #000000;
        color: #737373;
        margin: 0 1;
        text-style: bold;
        min-width: 11;
        text-align: center;
    }

    .toggle-btn:hover {
        border: solid #666666;
        color: #ffffff;
    }

    .toggle-btn:focus {
        border: solid #ffffff;
        color: #ffffff;
        background: #222222;
    }

    .toggle-btn-active {
        border: solid #ffffff;
        background: #ffffff;
        color: #000000;
        margin: 0 1;
        text-style: bold;
        min-width: 11;
        text-align: center;
    }

    .toggle-btn-active:hover {
        background: #d4d4d4;
        color: #000000;
    }

    .toggle-btn-active:focus {
        background: #ffffff;
        border: solid #ffffff;
        color: #000000;
        text-style: bold;
    }

    .settings-status {
        color: #ffffff;
        text-align: center;
        width: 100%;
        text-style: bold;
        margin-top: 0;
    }

    .hidden {
        display: none;
    }

    ProgressBar {
        margin: 0;
    }

    Bar > .bar--bar {
        color: #ffffff;
        background: #222222;
    }

    Bar > .bar--complete {
        color: #ffffff;
    }
    """

    BINDINGS = [
        Binding("1", "switch_tab('download')", "[ 1: Download ]", show=True),
        Binding("2", "switch_tab('search')", "[ 2: Live Search & Discover ]", show=True),
        Binding("3", "switch_tab('settings')", "[ 3: Settings ]", show=True),
        Binding("f1", "switch_tab('download')", "", show=False),
        Binding("f2", "switch_tab('search')", "", show=False),
        Binding("f3", "switch_tab('settings')", "", show=False),
        Binding("tab", "next_tab", "", show=False),
        Binding("ctrl+c", "quit_app", "[ Ctrl+C: Quit ]", show=True),
    ]

    def __init__(self):
        super().__init__()
        self.config = load_config()
        self.download_options = get_download_options()
        self.is_downloading = False
        self.active_tab = "download"
        self.search_debounce_task: Optional[asyncio.Task] = None
        self.search_results_cache: List[dict] = []

        self.selected_format = self.config.get("format", "mp3").lower()
        self.selected_bitrate = self.config.get("bitrate", "320k")
        self.embed_cover = self.config.get("embed_cover", True)
        self.fetch_lyrics = self.config.get("fetch_lyrics", True)
        self.save_lrc = self.config.get("save_lrc", False)
        self.overwrite = self.config.get("overwrite", False)

    def compose(self) -> ComposeResult:
        yield Header(show_clock=True)

        with Horizontal(classes="top-tab-bar"):
            yield Button("DOWNLOAD", id="tab-btn-download", classes="tab-btn-active")
            yield Button("SEARCH & DISCOVER", id="tab-btn-search", classes="tab-btn")
            yield Button("SETTINGS", id="tab-btn-settings", classes="tab-btn")

        with Vertical(id="pane-download", classes="tab-pane-view"):
            with Center():
                yield Static(Text(BANNER_BLOCK, no_wrap=True), classes="banner-text")

            with Center():
                yield Input(
                    placeholder="Enter Spotify or YouTube link, or type song name...",
                    id="download-url-input",
                    classes="center-input",
                )

            with Horizontal(classes="btn-row"):
                yield Button("DOWNLOAD", id="btn-start-download", classes="btn-primary")
                yield Button("CLEAR", id="btn-clear-input", classes="btn-secondary")

            with Center():
                with Vertical(id="download-monitor", classes="monitor-card hidden"):
                    yield Label("DOWNLOADING", id="monitor-title", classes="monitor-header")
                    yield Label("", id="track-info-label", classes="monitor-track")
                    with Center():
                        yield ProgressBar(total=100, show_eta=False, id="track-progress", classes="monitor-bar")
                    with Center():
                        yield ProgressBar(total=100, show_eta=False, id="batch-progress", classes="monitor-bar")
                    yield Label("Status: [ CONNECTING ]", id="metrics-label", classes="monitor-status")

        with Vertical(id="pane-search", classes="tab-pane-view hidden"):
            yield Label("LIVE MUSIC SEARCH & DISCOVERY", classes="section-header")

            with Center():
                yield Input(
                    placeholder="Search music live as you type...",
                    id="live-search-input",
                    classes="center-input",
                )

            with Center():
                yield DataTable(id="search-data-table", classes="search-table", zebra_stripes=True)

            with Horizontal(classes="btn-row"):
                yield Button("DOWNLOAD SELECTED", id="btn-download-search-selected", classes="btn-primary")
                yield Button("CLEAR", id="btn-clear-search", classes="btn-secondary")

        with Vertical(id="pane-settings", classes="tab-pane-view hidden"):
            with Center():
                with Vertical(classes="settings-card"):
                    yield Label("AUDIO FORMAT", classes="settings-subheader")
                    with Horizontal(classes="toggle-row"):
                        for fmt in ["mp3", "flac", "opus", "wav", "m4a"]:
                            is_act = fmt == self.selected_format
                            cls_name = "toggle-btn-active" if is_act else "toggle-btn"
                            yield Button(fmt.upper(), id=f"fmt-{fmt}", classes=cls_name)

                    yield Label("BITRATE", classes="settings-subheader")
                    with Horizontal(classes="toggle-row"):
                        for br in ["320k", "256k", "192k", "128k"]:
                            is_act = br == self.selected_bitrate
                            cls_name = "toggle-btn-active" if is_act else "toggle-btn"
                            yield Button(br, id=f"br-{br}", classes=cls_name)

                    yield Label("OUTPUT DIRECTORY", classes="settings-subheader")
                    yield Input(
                        value=str(self.config.get("output_dir", get_default_music_dir())),
                        id="setting-output-dir",
                        classes="settings-input",
                    )

                    yield Label("OPTIONS", classes="settings-subheader")
                    with Horizontal(classes="toggle-row"):
                        art_cls = "toggle-btn-active" if self.embed_cover else "toggle-btn"
                        yield Button(f"ARTWORK: {'ON' if self.embed_cover else 'OFF'}", id="toggle-artwork", classes=art_cls)

                        lyr_cls = "toggle-btn-active" if self.fetch_lyrics else "toggle-btn"
                        yield Button(f"LYRICS: {'ON' if self.fetch_lyrics else 'OFF'}", id="toggle-lyrics", classes=lyr_cls)

                        lrc_cls = "toggle-btn-active" if self.save_lrc else "toggle-btn"
                        yield Button(f"SAVE LRC: {'ON' if self.save_lrc else 'OFF'}", id="toggle-save-lrc", classes=lrc_cls)

                        ovr_cls = "toggle-btn-active" if self.overwrite else "toggle-btn"
                        yield Button(f"OVERWRITE: {'ON' if self.overwrite else 'OFF'}", id="toggle-overwrite", classes=ovr_cls)

                    with Horizontal(classes="btn-row"):
                        yield Button("SAVE SETTINGS", id="btn-save-settings", classes="btn-primary")

                    yield Label("", id="settings-status-label", classes="settings-status")

        yield Footer()

    def on_mount(self) -> None:
        table = self.query_one("#search-data-table", DataTable)
        table.cursor_type = "row"
        table.add_columns("NO", "TITLE", "ARTIST", "DURATION")

    def action_switch_tab(self, tab_name: str) -> None:
        self.active_tab = tab_name

        p_dl = self.query_one("#pane-download", Vertical)
        p_sr = self.query_one("#pane-search", Vertical)
        p_st = self.query_one("#pane-settings", Vertical)

        b_dl = self.query_one("#tab-btn-download", Button)
        b_sr = self.query_one("#tab-btn-search", Button)
        b_st = self.query_one("#tab-btn-settings", Button)

        for p, b in [(p_dl, b_dl), (p_sr, b_sr), (p_st, b_st)]:
            p.add_class("hidden")
            b.remove_class("tab-btn-active")
            b.add_class("tab-btn")

        if tab_name == "download":
            p_dl.remove_class("hidden")
            b_dl.remove_class("tab-btn")
            b_dl.add_class("tab-btn-active")
            self.query_one("#download-url-input", Input).focus()
        elif tab_name == "search":
            p_sr.remove_class("hidden")
            b_sr.remove_class("tab-btn")
            b_sr.add_class("tab-btn-active")
            self.query_one("#live-search-input", Input).focus()
        elif tab_name == "settings":
            p_st.remove_class("hidden")
            b_st.remove_class("tab-btn")
            b_st.add_class("tab-btn-active")

    def action_next_tab(self) -> None:
        order = ["download", "search", "settings"]
        idx = order.index(self.active_tab)
        next_idx = (idx + 1) % len(order)
        self.action_switch_tab(order[next_idx])

    def action_quit_app(self) -> None:
        self.exit()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        btn_id = event.button.id
        if not btn_id:
            return

        if btn_id == "tab-btn-download":
            self.action_switch_tab("download")
        elif btn_id == "tab-btn-search":
            self.action_switch_tab("search")
        elif btn_id == "tab-btn-settings":
            self.action_switch_tab("settings")

        elif btn_id == "btn-start-download":
            input_w = self.query_one("#download-url-input", Input)
            query = input_w.value.strip()
            if query:
                self.start_download_task(query)
        elif btn_id == "btn-clear-input":
            input_w = self.query_one("#download-url-input", Input)
            input_w.value = ""
            input_w.focus()

        elif btn_id == "btn-download-search-selected":
            self.download_selected_search_track()
        elif btn_id == "btn-clear-search":
            input_w = self.query_one("#live-search-input", Input)
            input_w.value = ""
            table = self.query_one("#search-data-table", DataTable)
            table.clear()
            self.search_results_cache = []
            input_w.focus()

        elif btn_id.startswith("fmt-"):
            fmt = btn_id.replace("fmt-", "")
            self.selected_format = fmt
            for f in ["mp3", "flac", "opus", "wav", "m4a"]:
                btn = self.query_one(f"#fmt-{f}", Button)
                if f == fmt:
                    btn.remove_class("toggle-btn")
                    btn.add_class("toggle-btn-active")
                else:
                    btn.remove_class("toggle-btn-active")
                    btn.add_class("toggle-btn")

        elif btn_id.startswith("br-"):
            br = btn_id.replace("br-", "")
            self.selected_bitrate = br
            for b in ["320k", "256k", "192k", "128k"]:
                btn = self.query_one(f"#br-{b}", Button)
                if b == br:
                    btn.remove_class("toggle-btn")
                    btn.add_class("toggle-btn-active")
                else:
                    btn.remove_class("toggle-btn-active")
                    btn.add_class("toggle-btn")

        elif btn_id == "toggle-artwork":
            self.embed_cover = not self.embed_cover
            btn = self.query_one("#toggle-artwork", Button)
            btn.label = f"ARTWORK: {'ON' if self.embed_cover else 'OFF'}"
            self._update_toggle_style(btn, self.embed_cover)

        elif btn_id == "toggle-lyrics":
            self.fetch_lyrics = not self.fetch_lyrics
            btn = self.query_one("#toggle-lyrics", Button)
            btn.label = f"LYRICS: {'ON' if self.fetch_lyrics else 'OFF'}"
            self._update_toggle_style(btn, self.fetch_lyrics)

        elif btn_id == "toggle-save-lrc":
            self.save_lrc = not self.save_lrc
            btn = self.query_one("#toggle-save-lrc", Button)
            btn.label = f"SAVE LRC: {'ON' if self.save_lrc else 'OFF'}"
            self._update_toggle_style(btn, self.save_lrc)

        elif btn_id == "toggle-overwrite":
            self.overwrite = not self.overwrite
            btn = self.query_one("#toggle-overwrite", Button)
            btn.label = f"OVERWRITE: {'ON' if self.overwrite else 'OFF'}"
            self._update_toggle_style(btn, self.overwrite)

        elif btn_id == "btn-save-settings":
            self.save_settings_from_ui()

    def _update_toggle_style(self, btn: Button, is_active: bool) -> None:
        if is_active:
            btn.remove_class("toggle-btn")
            btn.add_class("toggle-btn-active")
        else:
            btn.remove_class("toggle-btn-active")
            btn.add_class("toggle-btn")

    def on_input_submitted(self, event: Input.Submitted) -> None:
        if event.input.id == "download-url-input":
            query = event.value.strip()
            if query:
                self.start_download_task(query)
        elif event.input.id == "live-search-input":
            query = event.value.strip()
            if query:
                self.execute_live_search(query)

    def on_input_changed(self, event: Input.Changed) -> None:
        if event.input.id == "live-search-input":
            query = event.value.strip()
            if self.search_debounce_task:
                self.search_debounce_task.cancel()

            if len(query) >= 2:
                self.search_debounce_task = asyncio.create_task(self._debounced_search(query))
            elif not query:
                table = self.query_one("#search-data-table", DataTable)
                table.clear()
                self.search_results_cache = []

    async def _debounced_search(self, query: str) -> None:
        try:
            await asyncio.sleep(0.25)
            self.execute_live_search(query)
        except asyncio.CancelledError:
            pass

    @work(thread=True)
    def execute_live_search(self, query: str) -> None:
        import yt_dlp

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

        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                res = ydl.extract_info(f"ytsearch10:{query}", download=False)
                entries = res.get("entries", []) if res else []

                results = []
                for idx, e in enumerate(entries, 1):
                    if not e:
                        continue
                    vid = e.get("id")
                    title = e.get("title") or "Unknown"
                    uploader = e.get("uploader") or e.get("channel") or "Unknown Artist"
                    duration = e.get("duration", 0) or 0
                    url = e.get("url") or (f"https://www.youtube.com/watch?v={vid}" if vid else None)

                    dur_str = format_duration(float(duration)) if duration else "--:--"

                    results.append({
                        "index": idx,
                        "title": title,
                        "artist": uploader,
                        "duration": dur_str,
                        "url": url,
                        "id": vid,
                    })

                self.app.call_from_thread(self._update_search_table, results)
        except Exception:
            pass

    def _update_search_table(self, results: List[dict]) -> None:
        table = self.query_one("#search-data-table", DataTable)
        table.clear()
        self.search_results_cache = results

        for r in results:
            table.add_row(
                str(r["index"]),
                r["title"],
                r["artist"],
                r["duration"],
                key=str(r["index"]),
            )

    def on_data_table_row_selected(self, event: DataTable.RowSelected) -> None:
        self.download_selected_search_track()

    def download_selected_search_track(self) -> None:
        table = self.query_one("#search-data-table", DataTable)
        if not table.row_count:
            return

        cursor_row = table.cursor_row
        if cursor_row is not None and cursor_row < len(self.search_results_cache):
            track_data = self.search_results_cache[cursor_row]
            url = track_data.get("url")
            if url:
                self.action_switch_tab("download")
                input_w = self.query_one("#download-url-input", Input)
                input_w.value = url
                self.start_download_task(url)

    def save_settings_from_ui(self) -> None:
        out_val = self.query_one("#setting-output-dir", Input).value.strip()

        self.config["format"] = self.selected_format
        self.config["bitrate"] = self.selected_bitrate
        self.config["output_dir"] = out_val or str(get_default_music_dir())
        self.config["sequential_naming"] = True
        self.config["embed_cover"] = self.embed_cover
        self.config["fetch_lyrics"] = self.fetch_lyrics
        self.config["save_lrc"] = self.save_lrc
        self.config["overwrite"] = self.overwrite

        save_config(self.config)
        self.download_options = get_download_options()

        status_lbl = self.query_one("#settings-status-label", Label)
        status_lbl.update("[ SETTINGS SAVED ]")

    def start_download_task(self, query_or_url: str) -> None:
        if self.is_downloading:
            return

        self.is_downloading = True

        monitor = self.query_one("#download-monitor", Vertical)
        monitor.remove_class("hidden")

        title_lbl = self.query_one("#monitor-title", Label)
        info_lbl = self.query_one("#track-info-label", Label)
        metrics_lbl = self.query_one("#metrics-label", Label)
        t_prog = self.query_one("#track-progress", ProgressBar)
        b_prog = self.query_one("#batch-progress", ProgressBar)

        title_lbl.update("FETCHING METADATA")
        info_lbl.update(f"{query_or_url}")
        metrics_lbl.update("Status: [ CONNECTING ]")
        t_prog.update(progress=0)
        b_prog.update(progress=0)

        self.run_download_worker(query_or_url)

    @work(thread=True)
    def run_download_worker(self, query_or_url: str) -> None:
        opts = get_download_options()

        try:
            playlist_info = DownloadPipeline.resolve_input(
                query_or_url,
                spotify_client_id=self.config.get("spotify_client_id", ""),
                spotify_client_secret=self.config.get("spotify_client_secret", ""),
            )

            total_tracks = len(playlist_info.tracks)
            folder_name = playlist_info.title if total_tracks > 1 else ""

            self.app.call_from_thread(self._on_metadata_resolved, playlist_info)

            success_count = 0
            for idx, track in enumerate(playlist_info.tracks, 1):
                def prog_callback(p: DownloadProgress):
                    self.app.call_from_thread(self._update_progress_ui, p)

                try:
                    DownloadPipeline.process_track(
                        track,
                        opts,
                        folder_name=folder_name,
                        total_tracks=total_tracks,
                        index=idx,
                        progress_callback=prog_callback,
                    )
                    success_count += 1
                except Exception:
                    pass

                self.app.call_from_thread(self._update_batch_progress, idx, total_tracks)

            self.app.call_from_thread(self._on_download_complete, success_count, total_tracks, opts.output_dir)

        except Exception as e:
            self.app.call_from_thread(self._on_download_error, str(e))
        finally:
            self.is_downloading = False

    def _on_metadata_resolved(self, playlist: PlaylistInfo) -> None:
        title_lbl = self.query_one("#monitor-title", Label)
        info_lbl = self.query_one("#track-info-label", Label)
        title_lbl.update(f"FOUND {len(playlist.tracks)} TRACK(S)")
        info_lbl.update(f"{playlist.title} - {playlist.author}")

    def _update_progress_ui(self, prog: DownloadProgress) -> None:
        title_lbl = self.query_one("#monitor-title", Label)
        info_lbl = self.query_one("#track-info-label", Label)
        metrics_lbl = self.query_one("#metrics-label", Label)
        t_prog = self.query_one("#track-progress", ProgressBar)

        status_str = prog.status.value.upper()
        if prog.current_track_idx > 0 and prog.total_tracks > 0:
            title_lbl.update(f"TRACK [{prog.current_track_idx}/{prog.total_tracks}]")
        if prog.track_title:
            info_lbl.update(f"{prog.track_title}")

        speed_part = f" | {prog.speed_str}" if prog.speed_str else ""
        eta_part = f" | ETA: {prog.eta_str}" if prog.eta_str else ""
        metrics_lbl.update(f"Status: [ {status_str} ]{speed_part}{eta_part}")

        t_prog.update(progress=prog.download_percent)

    def _update_batch_progress(self, current: int, total: int) -> None:
        b_prog = self.query_one("#batch-progress", ProgressBar)
        if total > 0:
            pct = current / total * 100.0
            b_prog.update(progress=pct)

    def _on_download_complete(self, success: int, total: int, out_dir: Path) -> None:
        title_lbl = self.query_one("#monitor-title", Label)
        info_lbl = self.query_one("#track-info-label", Label)
        metrics_lbl = self.query_one("#metrics-label", Label)
        title_lbl.update("DOWNLOAD COMPLETE")
        info_lbl.update(f"Processed: {success} / {total} tracks")
        metrics_lbl.update(f"Saved to: {out_dir}")

    def _on_download_error(self, err_msg: str) -> None:
        title_lbl = self.query_one("#monitor-title", Label)
        info_lbl = self.query_one("#track-info-label", Label)
        metrics_lbl = self.query_one("#metrics-label", Label)
        title_lbl.update("ERROR")
        info_lbl.update(f"{err_msg}")
        metrics_lbl.update("Status: [ FAILED ]")

def run_interactive_tui() -> None:
    app = OpenTunesApp()
    app.run()

class OpenTunesTUI:

    @classmethod
    def start(cls) -> None:
        run_interactive_tui()
