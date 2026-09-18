package com.opentunes.app.core.lyrics

import com.opentunes.app.core.models.TrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object LyricsFetcher {
    private const val API_URL = "https://lrclib.net/api/get"
    private const val USER_AGENT = "OpenTunes/1.0 (https://github.com/opentunes/opentunes)"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class LyricsResult(
        val plainLyrics: String?,
        val syncedLyrics: String?
    )

    suspend fun fetchLyrics(track: TrackMetadata): LyricsResult = withContext(Dispatchers.IO) {
        val urlBuilder = API_URL.toHttpUrlOrNull()?.newBuilder() ?: return@withContext LyricsResult(null, null)

        urlBuilder.addQueryParameter("track_name", track.title)
        urlBuilder.addQueryParameter("artist_name", track.primaryArtist)
        if (track.album.isNotBlank() && track.album != "Single" && track.album != "Unknown Album") {
            urlBuilder.addQueryParameter("album_name", track.album)
        }
        if (track.durationSeconds > 0) {
            urlBuilder.addQueryParameter("duration", track.durationSeconds.roundToInt().toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext LyricsResult(null, null)
                    val json = JSONObject(body)
                    val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
                    val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
                    return@withContext LyricsResult(plain, synced)
                }
            }
        } catch (_: Exception) {}

        LyricsResult(null, null)
    }

    suspend fun saveLrcFile(lrcContent: String, audioFile: File): File? = withContext(Dispatchers.IO) {
        if (lrcContent.isBlank()) return@withContext null
        try {
            val nameWithoutExt = audioFile.nameWithoutExtension
            val lrcFile = File(audioFile.parentFile, "$nameWithoutExt.lrc")
            lrcFile.writeText(lrcContent, Charsets.UTF_8)
            lrcFile
        } catch (_: Exception) {
            null
        }
    }
}
