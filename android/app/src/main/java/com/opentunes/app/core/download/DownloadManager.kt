package com.opentunes.app.core.download

import android.content.Context
import android.media.MediaScannerConnection
import com.opentunes.app.core.config.AppConfig
import com.opentunes.app.core.library.LibraryRepository
import com.opentunes.app.core.lyrics.LyricsFetcher
import com.opentunes.app.core.matcher.AudioMatcher
import com.opentunes.app.core.models.DownloadOptions
import com.opentunes.app.core.models.DownloadTask
import com.opentunes.app.core.models.PlaylistInfo
import com.opentunes.app.core.models.TrackMetadata
import com.opentunes.app.core.models.TrackStatus
import com.opentunes.app.core.transcoder.AudioTranscoder
import com.opentunes.app.core.youtube.YouTubeMusicExtractor
import com.opentunes.app.service.DownloadForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val jobMap = ConcurrentHashMap<String, Job>()
    private val pausedMap = ConcurrentHashMap<String, Boolean>()

    private val downloadSemaphore = Semaphore(3)

    private val httpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun enqueue(track: TrackMetadata, playlistTitle: String = "", index: Int = 1, total: Int = 1) {
        val options = AppConfig.options.value.copy()
        val taskId = UUID.randomUUID().toString()
        val task = DownloadTask(
            id = taskId,
            track = track,
            options = options,
            index = index,
            totalTracks = total,
            playlistTitle = playlistTitle
        )

        _tasks.value = _tasks.value + task
        DownloadForegroundService.startService(appContext)

        val job = scope.launch {
            processTask(task)
        }
        jobMap[taskId] = job
    }

    fun enqueuePlaylist(playlist: PlaylistInfo) {
        val total = playlist.tracks.size
        for (i in 0 until total) {
            enqueue(
                track = playlist.tracks[i],
                playlistTitle = playlist.title,
                index = i + 1,
                total = total
            )
        }
    }

    fun pauseTask(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status.value == TrackStatus.DOWNLOADING || task.status.value == TrackStatus.QUEUED) {
            pausedMap[taskId] = true
            task.status.value = TrackStatus.PAUSED
        }
    }

    fun resumeTask(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status.value == TrackStatus.PAUSED) {
            pausedMap[taskId] = false
            task.status.value = TrackStatus.QUEUED
            val job = scope.launch {
                processTask(task)
            }
            jobMap[taskId] = job
        }
    }

    fun cancelTask(taskId: String) {
        jobMap[taskId]?.cancel()
        jobMap.remove(taskId)
        pausedMap.remove(taskId)

        val task = _tasks.value.find { it.id == taskId }
        task?.status?.value = TrackStatus.CANCELLED
        checkAllFinished()
    }

    fun clearCompleted() {
        _tasks.value = _tasks.value.filter {
            it.status.value == TrackStatus.QUEUED ||
            it.status.value == TrackStatus.DOWNLOADING ||
            it.status.value == TrackStatus.MATCHING ||
            it.status.value == TrackStatus.CONVERTING ||
            it.status.value == TrackStatus.TAGGING ||
            it.status.value == TrackStatus.PAUSED
        }
    }

    private suspend fun processTask(task: DownloadTask) {
        // Check if paused
        if (pausedMap[task.id] == true) {
            task.status.value = TrackStatus.PAUSED
            return
        }

        val destFile = getDestinationFile(task)

        // Skip if already exists and not overwrite
        if (destFile.exists() && destFile.length() > 50_000 && !task.options.overwrite) {
            task.status.value = TrackStatus.SKIPPED
            task.progressPercent.value = 100f
            task.outputPath.value = destFile.absolutePath
            checkAllFinished()
            return
        }

        var tempRawAudio: File? = null

        try {
            downloadSemaphore.withPermit {
                if (pausedMap[task.id] == true) {
                    task.status.value = TrackStatus.PAUSED
                    return@withPermit
                }

                // 1. MATCHING: Get up to 6 ranked candidates
                task.status.value = TrackStatus.MATCHING
                val candidates = AudioMatcher.getRankedCandidates(task.track, limit = 6).toMutableList()
                if (candidates.isEmpty()) {
                    val broad = AudioMatcher.getBroadFallbackCandidates(task.track, limit = 4)
                    candidates.addAll(broad)
                }

                if (candidates.isEmpty()) {
                    task.status.value = TrackStatus.FAILED
                    task.errorMessage.value = "No audio candidates found on YouTube"
                    return@withPermit
                }

                var downloadSucceeded = false
                var lastErrorMsg = "Download failed"

                for ((candIdx, candUrl) in candidates.withIndex()) {
                    if (pausedMap[task.id] == true) {
                        task.status.value = TrackStatus.PAUSED
                        return@withPermit
                    }

                    // Attempt each candidate up to 3 times with progressive backoff
                    for (attempt in 1..3) {
                        if (pausedMap[task.id] == true) {
                            task.status.value = TrackStatus.PAUSED
                            return@withPermit
                        }

                        var rawAudio: File? = null
                        try {
                            task.status.value = TrackStatus.DOWNLOADING
                            val streamUrl = YouTubeMusicExtractor.getStreamUrl(candUrl)

                            rawAudio = File.createTempFile("raw_audio_", ".tmp")
                            tempRawAudio = rawAudio

                            val success = downloadStreamWithProgress(candUrl, streamUrl, rawAudio, task)
                            if (!success || pausedMap[task.id] == true) {
                                if (pausedMap[task.id] == true) {
                                    task.status.value = TrackStatus.PAUSED
                                    return@withPermit
                                }
                                rawAudio.delete()
                                delay(1000L * attempt)
                                continue
                            }

                            if (rawAudio.length() < 30_000) {
                                rawAudio.delete()
                                continue
                            }

                            // 4. LYRICS
                            var plainLyrics: String? = null
                            if (task.options.fetchLyrics) {
                                try {
                                    val lyr = LyricsFetcher.fetchLyrics(task.track)
                                    plainLyrics = lyr.plainLyrics
                                    if (task.options.saveLrc && !lyr.syncedLyrics.isNullOrBlank()) {
                                        LyricsFetcher.saveLrcFile(lyr.syncedLyrics, destFile)
                                    }
                                } catch (_: Exception) {}
                            }

                            // 5. CONVERTING & TAGGING
                            task.status.value = TrackStatus.CONVERTING
                            val converted = AudioTranscoder.transcodeAndTag(
                                rawAudioFile = rawAudio,
                                destFile = destFile,
                                track = task.track,
                                options = task.options,
                                plainLyrics = plainLyrics
                            )

                            if (converted && destFile.exists() && destFile.length() > 10_000) {
                                task.status.value = TrackStatus.COMPLETED
                                task.progressPercent.value = 100f
                                task.outputPath.value = destFile.absolutePath
                                task.speedStr.value = ""
                                task.etaStr.value = ""

                                // Scan into Android MediaStore
                                MediaScannerConnection.scanFile(
                                    appContext,
                                    arrayOf(destFile.absolutePath),
                                    null,
                                    null
                                )

                                // Real-time notification to LibraryRepository
                                LibraryRepository.notifyFileDownloaded(destFile)

                                downloadSucceeded = true
                                break // Success on this candidate!
                            } else {
                                if (destFile.exists()) destFile.delete()
                                rawAudio.delete()
                            }
                        } catch (e: Exception) {
                            lastErrorMsg = e.message ?: "Candidate $candIdx attempt $attempt error"
                            rawAudio?.delete()
                            delay(1000L * attempt)
                        }
                    }

                    if (downloadSucceeded) {
                        break // Done with all candidates!
                    }
                }

                // Final Fallback: if all primary candidates failed, try broad search candidates
                if (!downloadSucceeded && pausedMap[task.id] != true) {
                    val broadFallback = AudioMatcher.getBroadFallbackCandidates(task.track, limit = 4)
                        .filter { !candidates.contains(it) }

                    for (fallbackUrl in broadFallback) {
                        if (pausedMap[task.id] == true) {
                            task.status.value = TrackStatus.PAUSED
                            return@withPermit
                        }

                        var rawAudio: File? = null
                        try {
                            task.status.value = TrackStatus.DOWNLOADING
                            val streamUrl = YouTubeMusicExtractor.getStreamUrl(fallbackUrl)

                            rawAudio = File.createTempFile("raw_audio_", ".tmp")
                            tempRawAudio = rawAudio

                            val success = downloadStreamWithProgress(fallbackUrl, streamUrl, rawAudio, task)
                            if (success && rawAudio.length() >= 30_000) {
                                task.status.value = TrackStatus.CONVERTING
                                val converted = AudioTranscoder.transcodeAndTag(
                                    rawAudioFile = rawAudio,
                                    destFile = destFile,
                                    track = task.track,
                                    options = task.options,
                                    plainLyrics = null
                                )

                                if (converted && destFile.exists() && destFile.length() > 10_000) {
                                    task.status.value = TrackStatus.COMPLETED
                                    task.progressPercent.value = 100f
                                    task.outputPath.value = destFile.absolutePath
                                    task.speedStr.value = ""
                                    task.etaStr.value = ""
                                    MediaScannerConnection.scanFile(appContext, arrayOf(destFile.absolutePath), null, null)
                                    LibraryRepository.notifyFileDownloaded(destFile)
                                    downloadSucceeded = true
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            lastErrorMsg = e.message ?: "Fallback candidate error"
                        } finally {
                            rawAudio?.delete()
                        }
                    }
                }

                if (!downloadSucceeded && pausedMap[task.id] != true) {
                    task.status.value = TrackStatus.FAILED
                    task.errorMessage.value = lastErrorMsg
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            task.status.value = TrackStatus.FAILED
            task.errorMessage.value = e.message ?: "Unknown error"
        } finally {
            tempRawAudio?.delete()
            jobMap.remove(task.id)
            checkAllFinished()
        }
    }

    private suspend fun downloadStreamWithProgress(
        candUrl: String,
        initialStreamUrl: String,
        targetFile: File,
        task: DownloadTask
    ): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        var streamUrl = initialStreamUrl

        // 1. Determine total content length using Range 0-0 or HEAD
        var totalBytes = -1L
        try {
            val headReq = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-0")
                .build()
            httpClient.newCall(headReq).execute().use { resp ->
                val cr = resp.header("Content-Range")
                if (!cr.isNullOrBlank() && cr.contains("/")) {
                    totalBytes = cr.substringAfter("/").trim().toLongOrNull() ?: -1L
                }
                if (totalBytes <= 0) {
                    totalBytes = resp.body?.contentLength() ?: -1L
                }
            }
        } catch (_: Exception) {}

        val outputStream = FileOutputStream(targetFile)
        var downloadedBytes = 0L
        var lastUpdate = System.currentTimeMillis()
        var bytesSinceLastUpdate = 0L

        try {
            if (totalBytes > 0) {
                // High-speed chunked downloading via HTTP Range (bypasses YouTube CDN throttling)
                val chunkSize = 1024 * 1024L // 1 MB chunks
                while (downloadedBytes < totalBytes) {
                    if (pausedMap[task.id] == true) {
                        return@withContext false
                    }

                    val endByte = (downloadedBytes + chunkSize - 1).coerceAtMost(totalBytes - 1)
                    var chunkSuccess = false

                    for (chunkAttempt in 1..3) {
                        try {
                            val chunkReq = Request.Builder()
                                .url(streamUrl)
                                .header("User-Agent", userAgent)
                                .header("Range", "bytes=$downloadedBytes-$endByte")
                                .build()

                            val chunkResp = httpClient.newCall(chunkReq).execute()
                            if (!chunkResp.isSuccessful && chunkResp.code != 206) {
                                chunkResp.close()
                                if (chunkResp.code == 403 || chunkResp.code == 410) {
                                    // Token expired, re-fetch stream URL
                                    try {
                                        val newUrl = YouTubeMusicExtractor.getStreamUrl(candUrl)
                                        if (newUrl.isNotBlank()) {
                                            streamUrl = newUrl
                                        }
                                    } catch (_: Exception) {}
                                }
                                delay(400L * chunkAttempt)
                                continue
                            }

                            val isEntireStream = chunkResp.code == 200
                            chunkResp.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(65536)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    if (pausedMap[task.id] == true) {
                                        return@withContext false
                                    }
                                    outputStream.write(buffer, 0, read)
                                    downloadedBytes += read
                                    bytesSinceLastUpdate += read

                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdate >= 250) {
                                        val durationSec = (now - lastUpdate) / 1000.0
                                        val speed = if (durationSec > 0) bytesSinceLastUpdate / durationSec else 0.0
                                        val speedMb = speed / (1024.0 * 1024.0)
                                        val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
                                        val etaSec = if (speed > 0) (remainingBytes / speed).toInt() else 0

                                        task.progressPercent.value = (downloadedBytes.toFloat() / totalBytes) * 100f
                                        task.speedStr.value = String.format("%.1f MB/s", speedMb)
                                        task.etaStr.value = "${etaSec}s"

                                        lastUpdate = now
                                        bytesSinceLastUpdate = 0L
                                    }
                                }
                            }

                            chunkSuccess = true
                            if (isEntireStream) {
                                downloadedBytes = totalBytes
                                break
                            }
                            break
                        } catch (e: Exception) {
                            if (pausedMap[task.id] == true) return@withContext false
                            if (chunkAttempt == 3) {
                                break
                            }
                            delay(400L * chunkAttempt)
                        }
                    }

                    if (!chunkSuccess && downloadedBytes < totalBytes) {
                        break
                    }
                }
            }

            // Fallback for unknown total length or if range reading didn't complete
            if (downloadedBytes == 0L || (totalBytes > 0 && downloadedBytes < totalBytes)) {
                for (fallbackAttempt in 1..3) {
                    try {
                        val req = Request.Builder()
                            .url(streamUrl)
                            .header("User-Agent", userAgent)
                            .build()
                        val resp = httpClient.newCall(req).execute()
                        if (!resp.isSuccessful) {
                            resp.close()
                            if (resp.code == 403 || resp.code == 410) {
                                try {
                                    val refreshed = YouTubeMusicExtractor.getBestAudioStream(candUrl)
                                    val newUrl = refreshed.url ?: refreshed.content
                                    if (!newUrl.isNullOrBlank()) {
                                        streamUrl = newUrl
                                    }
                                } catch (_: Exception) {}
                            }
                            delay(500L * fallbackAttempt)
                            continue
                        }
                        resp.body?.byteStream()?.use { input ->
                            val buffer = ByteArray(65536)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                if (pausedMap[task.id] == true) {
                                    return@withContext false
                                }
                                outputStream.write(buffer, 0, read)
                                downloadedBytes += read
                                bytesSinceLastUpdate += read

                                val now = System.currentTimeMillis()
                                if (now - lastUpdate >= 250) {
                                    val durationSec = (now - lastUpdate) / 1000.0
                                    val speed = if (durationSec > 0) bytesSinceLastUpdate / durationSec else 0.0
                                    val speedMb = speed / (1024.0 * 1024.0)

                                    if (totalBytes > 0) {
                                        task.progressPercent.value = (downloadedBytes.toFloat() / totalBytes) * 100f
                                    }
                                    task.speedStr.value = String.format("%.1f MB/s", speedMb)
                                    lastUpdate = now
                                    bytesSinceLastUpdate = 0L
                                }
                            }
                        }
                        break
                    } catch (e: Exception) {
                        if (pausedMap[task.id] == true) return@withContext false
                        delay(500L * fallbackAttempt)
                    }
                }
            }

            outputStream.flush()
            task.progressPercent.value = 100f
            return@withContext (targetFile.length() > 30_000)
        } catch (e: Exception) {
            e.printStackTrace()
            task.errorMessage.value = e.message ?: "Download error"
            return@withContext false
        } finally {
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    private fun getDestinationFile(task: DownloadTask): File {
        val baseDir = File(task.options.outputDir)
        val targetDir = if (task.playlistTitle.isNotBlank()) {
            File(baseDir, sanitizeFilename(task.playlistTitle))
        } else {
            baseDir
        }
        targetDir.mkdirs()

        val ext = task.options.format.lowercase().removePrefix(".")
        val artist = sanitizeFilename(task.track.primaryArtist)
        val title = sanitizeFilename(task.track.title)

        val digits = max(3, task.totalTracks.toString().length)
        val indexStr = task.index.toString().padStart(digits, '0')

        val fileName = if (task.options.sequentialNaming) {
            "$indexStr - $title - $artist.$ext"
        } else {
            "$title - $artist.$ext"
        }

        return File(targetDir, fileName)
    }

    private fun sanitizeFilename(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.isBlank()) "track" else cleaned
    }

    private fun checkAllFinished() {
        val active = _tasks.value.any {
            it.status.value == TrackStatus.DOWNLOADING ||
            it.status.value == TrackStatus.MATCHING ||
            it.status.value == TrackStatus.CONVERTING ||
            it.status.value == TrackStatus.TAGGING ||
            it.status.value == TrackStatus.QUEUED
        }
        if (!active) {
            DownloadForegroundService.stopService(appContext)
        }
    }
}

