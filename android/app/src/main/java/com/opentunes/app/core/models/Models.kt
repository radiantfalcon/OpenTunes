package com.opentunes.app.core.models

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

enum class TrackStatus(val displayName: String) {
    QUEUED("Queued"),
    MATCHING("Matching"),
    DOWNLOADING("Downloading"),
    CONVERTING("Converting"),
    TAGGING("Tagging"),
    COMPLETED("Completed"),
    SKIPPED("Skipped"),
    PAUSED("Paused"),
    CANCELLED("Cancelled"),
    FAILED("Failed")
}

data class TrackMetadata(
    val title: String,
    val artists: List<String> = emptyList(),
    val album: String = "Unknown Album",
    val albumArtist: String = "",
    val durationSeconds: Double = 0.0,
    val trackNumber: Int = 1,
    val totalTracks: Int = 1,
    val discNumber: Int = 1,
    val releaseYear: String? = null,
    val isrc: String? = null,
    val genre: String? = null,
    val coverUrl: String? = null,
    var lyrics: String? = null,
    val sourceUrl: String? = null,
    val sourceId: String? = null,
    val sourceProvider: String = "unknown",
    var youtubeId: String? = null,
    var youtubeUrl: String? = null
) {
    val artistStr: String
        get() = if (artists.isEmpty()) "Unknown Artist" else artists.joinToString(", ")

    val primaryArtist: String
        get() = if (artists.isEmpty()) "Unknown Artist" else artists.first()

    val resolvedAlbumArtist: String
        get() = albumArtist.ifBlank { primaryArtist }
}

data class PlaylistInfo(
    val title: String,
    val author: String = "Unknown",
    val description: String = "",
    val coverUrl: String? = null,
    val tracks: List<TrackMetadata> = emptyList(),
    val sourceUrl: String = "",
    val sourceType: String = "playlist"
) {
    val totalTracks: Int
        get() = tracks.size
}

data class DownloadOptions(
    var format: String = "mp3",
    var bitrate: String = "320k",
    var outputDir: String = "/storage/emulated/0/Music/OpenTunes",
    var sequentialNaming: Boolean = true,
    var fetchLyrics: Boolean = true,
    var saveLrc: Boolean = false,
    var embedCover: Boolean = true,
    var overwrite: Boolean = false,
    var concurrentDownloads: Int = 3,
    var dynamicColors: Boolean = true,
    var amoledBlack: Boolean = true
)

data class DownloadTask(
    val id: String,
    val track: TrackMetadata,
    val options: DownloadOptions,
    val index: Int = 1,
    val totalTracks: Int = 1,
    val playlistTitle: String = "",
    val status: MutableStateFlow<TrackStatus> = MutableStateFlow(TrackStatus.QUEUED),
    val progressPercent: MutableStateFlow<Float> = MutableStateFlow(0f),
    val speedStr: MutableStateFlow<String> = MutableStateFlow(""),
    val etaStr: MutableStateFlow<String> = MutableStateFlow(""),
    val errorMessage: MutableStateFlow<String?> = MutableStateFlow(null),
    val outputPath: MutableStateFlow<String?> = MutableStateFlow(null)
)
