```text
                                          ▄▄                                           
                                          ▓▀ ▀                                         
  ▄▄▓▀▓▄▄ ▐▒▀ ▄▀▓▄▄   ▄▄▓▀▓▄▄ ▐▒▀ ▄▀▓▄▄ ▄▄▒ ▓▄▄▐▓▀░   ░ ▄▐▒▀ ▄▀▓▄▄   ▄▄▓▀▓▄▄   ▄▄▓▀▓▄▄ 
 ▒▀    ▒▀ ▐░     ░   ▒▀    ░  ▐░     ░    ░    ▐░     ░  ▐░     ░   ▒▀    ░  ▐▒▀    ▀▀▀
▐░ ▓   ░ ▓▐░▀░   ░▄ ▐░ ▓▀▀▀▀▀▀▐░▀░   ░▄  ░▒▄░  ▐▒▄▓   ▒▄▓▐░▀░   ░▄ ▐░ ▓▀▀▀▀▀▀ ▀▀▀▀▀▀░▒▄
 ▓░▄   ░▓█ ▒█▒   ▒█▓ ▓░▄   ░▓█ ▒█▒   ▒█▓  ▓█▒  ▐▓█▓   ▓▀▓ ▒█▒   ▒█▓ ▓░▄   ░▓█ ▓░▄   ░▓█
  ▀▀▓▄▓▀▀  ▓▓▓ ▀▄▓▀▀  ▀▀▓▄▓▀▀  ▓▓▓   ▓▀▓  ▀▓▓▄  ▀██▀▄███▀ ▓▓▓   ▓▀▓  ▀▀▓▄▓▀▀   ▀▀▓▄▓▀▀ 
           ▀▓█                                                                         
             ▀                                                                         
```

Open-Source Music downloader for Spotify, YouTube, and YouTube Music. Downloads songs, albums, and playlists with embedded cover art and lyrics.

---

### Installation

#### Direct via Pip (Recommended)
```bash
pip install git+https://github.com/radiantfalcon/OpenTunes.git
```
*(On modern Linux distros like Arch, Debian, or Ubuntu with externally managed Python, add `--break-system-packages` or use `pipx`)*:
```bash
pip install --break-system-packages git+https://github.com/radiantfalcon/OpenTunes.git
# or
pipx install git+https://github.com/radiantfalcon/OpenTunes.git
```
Once installed, run OpenTunes from anywhere:
```bash
opentunes
```

---

### Manual Setup

#### Linux / macOS
```bash
git clone https://github.com/radiantfalcon/OpenTunes.git
cd OpenTunes
./setup.sh
./run.sh
```

#### Android (Termux)
```bash
pkg update && pkg install -y python ffmpeg git
git clone https://github.com/radiantfalcon/OpenTunes.git
cd OpenTunes
./setup.sh
termux-setup-storage
./run.sh --mobile
```

#### Windows
```cmd
git clone https://github.com/radiantfalcon/OpenTunes.git
cd OpenTunes
setup.bat
opentunes.bat
```

---

### How to Use

#### 1. Interactive Mode
Run without arguments to open the interactive dashboard:
```bash
./run.sh
```
Key shortcuts inside the app:
- `1` : Download tab
- `2` : Live Search & Discover tab (searches live as you type)
- `3` : Settings tab (formats, bitrates, lyrics, output path)
- `Ctrl + C` : Quit

#### 2. Command Line Examples

Download a Spotify playlist:
```bash
./run.sh "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
```

Download a Spotify album in lossless FLAC:
```bash
./run.sh "https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa" -f flac
```

Download a YouTube / YouTube Music playlist in Opus:
```bash
./run.sh "https://music.youtube.com/playlist?list=PL..." -f opus -q 160k
```

Search and download a song directly by name:
```bash
./run.sh --search "Queen Bohemian Rhapsody"
```

Download to a custom folder:
```bash
./run.sh "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT" -o ~/Music/Rock
```

Batch download from a text file:
```bash
./run.sh --batch urls.txt
```
*(urls.txt can have one Spotify link, YouTube link, or song query per line)*

Save synced lyrics as a separate `.lrc` file:
```bash
./run.sh "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT" --lrc
```

Run in simple mobile mode (recommended on narrow terminals / Termux):
```bash
./run.sh --mobile
```

Check system environment and FFmpeg installation:
```bash
./run.sh --check
```

---

### CLI Reference

| Flag | Short | Description | Default |
|---|---|---|---|
| `url_or_query` | | Spotify/YouTube URL or song name | Launches interactive app |
| `--format` | `-f` | Audio format: `mp3`, `flac`, `opus`, `wav`, `m4a` | `mp3` |
| `--quality` | `-q` | Audio bitrate: `320k`, `256k`, `192k`, `128k`, `best` | `320k` |
| `--output` | `-o` | Destination folder for downloads | `~/Music` |
| `--lrc` | | Export synced `.lrc` lyrics file next to audio | `false` |
| `--no-lyrics` | | Skip fetching and embedding lyrics | `false` |
| `--batch` | | Path to text file with links/queries | `none` |
| `--search` | | Search query to download best match | `none` |
| `--mobile` | | Simple prompt menu for mobile Termux | Auto-detected |
| `--overwrite` | | Force overwrite existing files | `false` |
| `--interactive`| `-i` | Force full-screen TUI | `false` |
| `--check` | | Check system dependencies and FFmpeg | |
| `--version` | `-v` | Print version | |

---

### Audio Formats & Bitrates

- **MP3**: Constant bitrate (`320k`, `256k`, `192k`, `128k`) with universal ID3v2.3 tags and embedded artwork.
- **FLAC**: Lossless compression with Vorbis metadata and embedded artwork.
- **OPUS**: High-efficiency VBR audio (`160k`, `128k`, `96k`) with Vorbis comments.
- **WAV**: Uncompressed 16-bit PCM audio.
- **M4A / AAC**: AAC encoding (`256k`, `320k`) with MP4 metadata.

---

### Configuration

Settings persist across runs and are saved at:
- **Linux / Android**: `~/.config/opentunes/config.json`
- **Windows**: `%USERPROFILE%\.config\opentunes\config.json`

Example `config.json`:
```json
{
  "format": "mp3",
  "bitrate": "320k",
  "output_dir": "~/Music",
  "fetch_lyrics": true,
  "save_lrc": false,
  "embed_cover": true,
  "overwrite": false,
  "max_retries": 3,
  "rate_limit_delay": 1.2
}
```

You can also change these settings interactively through the Settings tab (`[3]`) in the desktop app or the Settings menu in mobile mode.

---

### Requirements

- **Python**: 3.8 or newer
- **FFmpeg**: Required for audio conversion and metadata embedding

Installing FFmpeg:
- **Ubuntu / Debian**: `sudo apt install ffmpeg`
- **Arch Linux**: `sudo pacman -S ffmpeg`
- **macOS**: `brew install ffmpeg`
- **Android (Termux)**: `pkg install ffmpeg`
- **Windows**: `winget install Gyan.FFmpeg`

---

### License

MIT
