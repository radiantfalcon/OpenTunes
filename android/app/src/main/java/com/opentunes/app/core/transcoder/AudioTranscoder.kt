package com.opentunes.app.core.transcoder

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.opentunes.app.core.models.DownloadOptions
import com.opentunes.app.core.models.TrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AudioTranscoder {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun transcodeAndTag(
        rawAudioFile: File,
        destFile: File,
        track: TrackMetadata,
        options: DownloadOptions,
        plainLyrics: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var coverFile: File? = null

        try {
            if (options.embedCover && !track.coverUrl.isNullOrBlank()) {
                coverFile = downloadCoverArt(track.coverUrl!!)
            }

            val fmt = options.format.lowercase()
            val quality = options.bitrate.lowercase()

            val audioArgs = when (fmt) {
                "mp3" -> {
                    val br = if (quality.endsWith("k")) quality else "${quality}k"
                    listOf("-c:a", "libmp3lame", "-b:a", br, "-id3v2_version", "3", "-write_xing", "1")
                }
                "flac" -> listOf("-c:a", "flac", "-compression_level", "8")
                "opus" -> {
                    val br = if (quality.endsWith("k")) quality else "160k"
                    listOf("-c:a", "libopus", "-b:a", br, "-vbr", "on")
                }
                "wav" -> listOf("-c:a", "pcm_s16le")
                "m4a" -> {
                    val br = if (quality.endsWith("k")) quality else "256k"
                    listOf("-c:a", "aac", "-b:a", br)
                }
                else -> listOf("-c:a", "libmp3lame", "-b:a", "320k")
            }

            val metadataArgs = mutableListOf<String>().apply {
                add("-metadata")
                add("title=${track.title}")
                add("-metadata")
                add("artist=${track.artistStr}")
                add("-metadata")
                add("album=${track.album}")
                add("-metadata")
                add("album_artist=${track.resolvedAlbumArtist}")
                add("-metadata")
                add("track=${track.trackNumber}/${track.totalTracks}")
                if (!track.releaseYear.isNullOrBlank()) {
                    add("-metadata")
                    add("date=${track.releaseYear}")
                }
                val lyr = plainLyrics ?: track.lyrics
                if (!lyr.isNullOrBlank()) {
                    add("-metadata")
                    add("lyrics=$lyr")
                }
            }

            destFile.parentFile?.mkdirs()

            val cmdList = mutableListOf<String>()
            cmdList.add("-y")
            cmdList.add("-i")
            cmdList.add(rawAudioFile.absolutePath)

            if (coverFile != null && coverFile.exists() && coverFile.length() > 0) {
                cmdList.add("-i")
                cmdList.add(coverFile.absolutePath)
                cmdList.add("-map")
                cmdList.add("0:a")
                cmdList.add("-map")
                cmdList.add("1:v?")
                cmdList.add("-c:v")
                cmdList.add("copy")
                cmdList.add("-disposition:v:0")
                cmdList.add("attached_pic")
            }

            cmdList.addAll(audioArgs)
            cmdList.addAll(metadataArgs)
            cmdList.add(destFile.absolutePath)

            val session = FFmpegKit.executeWithArguments(cmdList.toTypedArray())
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode) && destFile.exists() && destFile.length() > 10_000) {
                return@withContext true
            } else {
                // Fallback without cover art if cover art embedding failed
                if (coverFile != null) {
                    val fallbackCmd = mutableListOf<String>()
                    fallbackCmd.add("-y")
                    fallbackCmd.add("-i")
                    fallbackCmd.add(rawAudioFile.absolutePath)
                    fallbackCmd.addAll(audioArgs)
                    fallbackCmd.addAll(metadataArgs)
                    fallbackCmd.add(destFile.absolutePath)

                    val fbSession = FFmpegKit.executeWithArguments(fallbackCmd.toTypedArray())
                    return@withContext ReturnCode.isSuccess(fbSession.returnCode) && destFile.exists() && destFile.length() > 10_000
                }
                return@withContext false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            coverFile?.delete()
        }
    }

    private fun downloadCoverArt(url: String): File? {
        return try {
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val bytes = resp.body?.bytes() ?: return null
                val temp = File.createTempFile("cover_", ".jpg")
                FileOutputStream(temp).use { it.write(bytes) }
                temp
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
