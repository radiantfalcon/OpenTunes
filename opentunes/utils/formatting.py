import re
import unicodedata

def sanitize_filename(filename: str, max_length: int = 180) -> str:
    if not filename:
        return "unnamed_track"

    filename = unicodedata.normalize("NFKD", filename)

    cleaned = re.sub(r'[\/\\:\*\?"<>\|\x00-\x1f\x7f-\x9f]', "_", filename)

    cleaned = re.sub(r"[ \t]+", " ", cleaned)
    cleaned = re.sub(r"_+", "_", cleaned).strip()

    cleaned = cleaned.rstrip(". ")

    reserved_names = {
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    }
    if cleaned.upper() in reserved_names:
        cleaned = f"{cleaned}_track"

    if not cleaned:
        cleaned = "unnamed_track"

    if len(cleaned) > max_length:
        cleaned = cleaned[:max_length].rstrip(". ")

    return cleaned

def clean_artist_name(artist: str) -> str:
    if not artist:
        return "Unknown Artist"
    cleaned = re.sub(r"\s*-\s*Topic$", "", artist, flags=re.IGNORECASE).strip()
    cleaned = re.sub(r"\s*VEVO$", "", cleaned, flags=re.IGNORECASE).strip()
    return cleaned or "Unknown Artist"

def format_duration(seconds: float | int | None) -> str:
    if seconds is None or seconds < 0:
        return "00:00"
    seconds = int(round(seconds))
    hours, remainder = divmod(seconds, 3600)
    minutes, secs = divmod(remainder, 60)
    if hours > 0:
        return f"{hours:02d}:{minutes:02d}:{secs:02d}"
    return f"{minutes:02d}:{secs:02d}"

def format_bytes(bytes_count: float | int | None) -> str:
    if bytes_count is None or bytes_count <= 0:
        return "0 B"
    units = ["B", "KB", "MB", "GB", "TB"]
    unit_index = 0
    val = float(bytes_count)
    while val >= 1024.0 and unit_index < len(units) - 1:
        val /= 1024.0
        unit_index += 1
    return f"{val:.1f} {units[unit_index]}"

def pad_number(index: int, total: int, min_digits: int = 3) -> str:
    digits = max(min_digits, len(str(total)))
    return f"{index:0{digits}d}"
