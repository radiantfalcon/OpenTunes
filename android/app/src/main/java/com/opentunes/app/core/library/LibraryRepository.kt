package com.opentunes.app.core.library

import android.content.Context
import android.media.MediaMetadataRetriever
import com.opentunes.app.core.config.AppConfig
import com.opentunes.app.core.models.TrackMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class CachedTrack(
    val lastModified: Long,
    val fileSize: Long,
    val metadata: TrackMetadata
)

object LibraryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context

    private val _tracks = MutableStateFlow<List<TrackMetadata>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _trackCount = MutableStateFlow(0)
    val trackCount = _trackCount.asStateFlow()

    private val cache = ConcurrentHashMap<String, CachedTrack>()
    private val audioExtensions = setOf("mp3", "flac", "opus", "wav", "m4a", "ogg")

    fun init(context: Context) {
        appContext = context.applicationContext
        // Initial scan at app startup in background
        rescan()
    }

    fun rescan(force: Boolean = false) {
        scope.launch {
            scanInternal(force)
        }
    }

    fun notifyFileDownloaded(file: File) {
        scope.launch {
            if (!file.exists() || file.length() < 10_000) return@launch
            val enriched = enrichFile(file)
            cache[file.absolutePath] = CachedTrack(file.lastModified(), file.length(), enriched)

            // Insert or update in tracks list
            val currentList = _tracks.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.sourceUrl == file.absolutePath }
            if (existingIndex >= 0) {
                currentList[existingIndex] = enriched
            } else {
                currentList.add(enriched)
            }

            val sorted = sortFiles(currentList)
            _tracks.value = sorted
            _trackCount.value = sorted.size
        }
    }

    private suspend fun scanInternal(force: Boolean) = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        _isScanning.value = true

        val outputDir = AppConfig.options.value.outputDir
        val baseDir = File(outputDir)

        if (!baseDir.exists() || !baseDir.isDirectory) {
            _tracks.value = emptyList()
            _trackCount.value = 0
            _isScanning.value = false
            return@withContext
        }

        val files = baseDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in audioExtensions }
            .toList()

        // Phase 1: Instant Fast-Path Emit (<10ms)
        val initialTracks = mutableListOf<TrackMetadata>()
        val filesToEnrich = mutableListOf<File>()

        for (f in files) {
            val cached = cache[f.absolutePath]
            if (!force && cached != null && cached.lastModified == f.lastModified() && cached.fileSize == f.length()) {
                initialTracks.add(cached.metadata)
            } else {
                val fastTrack = fastMetadataFromFile(f)
                initialTracks.add(fastTrack)
                filesToEnrich.add(f)
            }
        }

        val fastSorted = sortFiles(initialTracks)
        _tracks.value = fastSorted
        _trackCount.value = fastSorted.size

        // If all files were already cached and up to date, finish immediately
        if (filesToEnrich.isEmpty()) {
            _isScanning.value = false
            return@withContext
        }

        // Phase 2: Background MMR Enrichment
        val enrichedMap = mutableMapOf<String, TrackMetadata>()
        for (f in filesToEnrich) {
            val enriched = enrichFile(f)
            cache[f.absolutePath] = CachedTrack(f.lastModified(), f.length(), enriched)
            enrichedMap[f.absolutePath] = enriched
        }

        // Update list with enriched tags & cover art
        val finalList = _tracks.value.map { track ->
            enrichedMap[track.sourceUrl] ?: track
        }
        val finalSorted = sortFiles(finalList)
        _tracks.value = finalSorted
        _trackCount.value = finalSorted.size
        _isScanning.value = false
    }

    private fun fastMetadataFromFile(f: File): TrackMetadata {
        val fileName = f.nameWithoutExtension
        var title = fileName
        var artist = "Unknown Artist"

        if (fileName.contains(" - ")) {
            val parts = fileName.split(" - ")
            if (parts.size >= 3) {
                // "001 - Title - Artist"
                title = parts[1].trim()
                artist = parts.subList(2, parts.size).joinToString(" - ").trim()
            } else if (parts.size == 2) {
                // Check if part 0 is just numbers: "001 - Title" vs "Title - Artist"
                if (parts[0].trim().matches(Regex("^\\d+$"))) {
                    title = parts[1].trim()
                } else {
                    title = parts[0].trim()
                    artist = parts[1].trim()
                }
            }
        }

        val coversDir = File(appContext.cacheDir, "covers")
        val coverFile = File(coversDir, "${f.nameWithoutExtension}_cover.jpg")
        val coverUrl = if (coverFile.exists() && coverFile.length() > 0L) {
            "file://${coverFile.absolutePath}"
        } else {
            null
        }

        return TrackMetadata(
            title = title,
            artists = listOf(artist),
            album = f.parentFile?.name ?: "OpenTunes Library",
            albumArtist = artist,
            durationSeconds = 0.0,
            sourceUrl = f.absolutePath,
            sourceId = f.absolutePath,
            coverUrl = coverUrl,
            sourceProvider = "local"
        )
    }

    private fun enrichFile(f: File): TrackMetadata {
        val coversDir = File(appContext.cacheDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()

        val fileName = f.nameWithoutExtension
        var title = fileName
        var artist = "Unknown Artist"
        var album = f.parentFile?.name ?: "OpenTunes Library"
        var durationSec = 0.0
        var coverPath: String? = null

        val coverFile = File(coversDir, "${f.nameWithoutExtension}_cover.jpg")
        if (coverFile.exists() && coverFile.length() > 0L) {
            coverPath = coverFile.absolutePath
        }

        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(f.absolutePath)

            val tagTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val tagArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val tagAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            if (!tagTitle.isNullOrBlank()) title = tagTitle
            if (!tagArtist.isNullOrBlank()) artist = tagArtist
            if (!tagAlbum.isNullOrBlank()) album = tagAlbum
            if (durMs > 0) durationSec = durMs / 1000.0

            if (coverPath == null) {
                val artBytes = mmr.embeddedPicture
                if (artBytes != null && artBytes.isNotEmpty()) {
                    coverFile.writeBytes(artBytes)
                    coverPath = coverFile.absolutePath
                }
            }

            mmr.release()
        } catch (_: Exception) {
            // Fallback to name parsing if tags unreadable
            if (title == fileName && fileName.contains(" - ")) {
                val parts = fileName.split(" - ")
                if (parts.size >= 3) {
                    title = parts[1].trim()
                    artist = parts.subList(2, parts.size).joinToString(" - ").trim()
                } else if (parts.size == 2) {
                    title = parts[0].trim()
                    artist = parts[1].trim()
                }
            }
        }

        return TrackMetadata(
            title = title,
            artists = listOf(artist),
            album = album,
            albumArtist = artist,
            durationSeconds = durationSec,
            sourceUrl = f.absolutePath,
            sourceId = f.absolutePath,
            coverUrl = coverPath?.let { "file://$it" },
            sourceProvider = "local"
        )
    }

    private fun extractSequenceNumber(path: String): Int {
        val name = File(path).nameWithoutExtension
        val match = Regex("^(\\d+)").find(name)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 999999
    }

    private fun sortFiles(tracks: List<TrackMetadata>): List<TrackMetadata> {
        return tracks.sortedWith(
            compareBy<TrackMetadata> { File(it.sourceUrl ?: "").parent ?: "" }
                .thenBy { extractSequenceNumber(it.sourceUrl ?: "") }
                .thenBy { it.title.lowercase() }
        )
    }
}
