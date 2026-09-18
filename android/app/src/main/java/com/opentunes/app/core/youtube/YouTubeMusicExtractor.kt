package com.opentunes.app.core.youtube

import com.opentunes.app.core.models.PlaylistInfo
import com.opentunes.app.core.models.TrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo as NewPipePlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.net.URI
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object YouTubeMusicExtractor {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var initialized = false

    fun init() {
        if (initialized) return
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val httpMethod = request.httpMethod()
                val url = request.url()
                val headers = request.headers()
                val dataToSend = request.dataToSend()

                val builder = okhttp3.Request.Builder().url(url)
                for ((key, values) in headers) {
                    for (v in values) {
                        builder.addHeader(key, v)
                    }
                }

                when {
                    "POST".equals(httpMethod, ignoreCase = true) -> {
                        val body = (dataToSend ?: ByteArray(0)).toRequestBody("application/json".toMediaTypeOrNull())
                        builder.post(body)
                    }
                    "HEAD".equals(httpMethod, ignoreCase = true) -> builder.head()
                    else -> builder.get()
                }

                val resp = okHttpClient.newCall(builder.build()).execute()
                val respHeaders = resp.headers.toMultimap()
                val respBody = resp.body?.string() ?: ""

                return Response(resp.code, resp.message, respHeaders, respBody, resp.request.url.toString())
            }
        })
        initialized = true
    }

    fun isYouTubeUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        return clean.contains("youtube.com") || clean.contains("youtu.be") || clean.contains("music.youtube.com")
    }

    fun isPlaylistUrl(url: String): Boolean {
        val videoId = parseVideoId(url)
        if (videoId != null && !url.contains("list=")) {
            return false
        }
        return url.contains("/playlist") || url.contains("list=") || url.contains("/browse/MPREb_")
    }

    fun parseVideoId(url: String): String? {
        val clean = url.trim()
        val shortMatch = Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})").matcher(clean)
        if (shortMatch.find()) return shortMatch.group(1)

        val embedMatch = Pattern.compile("youtube\\.com/(?:shorts|embed)/([a-zA-Z0-9_-]{11})").matcher(clean)
        if (embedMatch.find()) return embedMatch.group(1)

        try {
            val uri = URI(clean)
            if (uri.query != null) {
                val params = uri.query.split("&")
                for (p in params) {
                    val kv = p.split("=")
                    if (kv.size == 2 && kv[0] == "v" && kv[1].length == 11) {
                        return kv[1]
                    }
                }
            }
        } catch (_: Exception) {}

        val vMatch = Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})").matcher(clean)
        if (vMatch.find()) return vMatch.group(1)

        return null
    }

    suspend fun searchLive(query: String): List<TrackMetadata> = withContext(Dispatchers.IO) {
        init()
        val results = mutableListOf<TrackMetadata>()
        try {
            val qh = ServiceList.YouTube.searchQHFactory.fromQuery(query, Collections.singletonList("music_songs"), "")
            val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, qh)

            for (item in searchInfo.relatedItems) {
                if (item is StreamInfoItem) {
                    val vid = parseVideoId(item.url) ?: ""
                    var artist = item.uploaderName ?: "Unknown Artist"
                    var title = item.name ?: "Unknown Track"

                    if (title.contains(" - ")) {
                        val parts = title.split(" - ", limit = 2)
                        artist = parts[0].trim()
                        title = parts[1].trim()
                    }

                    title = cleanTrackTitle(title)

                    results.add(
                        TrackMetadata(
                            title = title,
                            artists = listOf(artist),
                            album = "Single",
                            albumArtist = artist,
                            durationSeconds = item.duration.toDouble(),
                            coverUrl = item.thumbnails.maxByOrNull { it.height }?.url,
                            sourceUrl = item.url,
                            sourceId = vid,
                            sourceProvider = "youtube",
                            youtubeId = vid,
                            youtubeUrl = item.url
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    suspend fun getMetadata(url: String): PlaylistInfo = withContext(Dispatchers.IO) {
        init()
        if (isPlaylistUrl(url)) {
            fetchPlaylist(url)
        } else {
            val vid = parseVideoId(url)
            val canonicalUrl = if (vid != null) "https://www.youtube.com/watch?v=$vid" else url
            fetchSingleTrack(canonicalUrl, vid)
        }
    }

    private fun fetchSingleTrack(canonicalUrl: String, videoId: String?): PlaylistInfo {
        val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, canonicalUrl)
        var title = streamInfo.name ?: "YouTube Track"
        var artist = streamInfo.uploaderName ?: "Unknown Artist"

        if (title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            artist = parts[0].trim()
            title = parts[1].trim()
        }

        title = cleanTrackTitle(title)
        val cover = streamInfo.thumbnails.maxByOrNull { it.height }?.url
        val vid = videoId ?: parseVideoId(canonicalUrl) ?: ""

        val track = TrackMetadata(
            title = title,
            artists = listOf(artist),
            album = "Single",
            albumArtist = artist,
            durationSeconds = streamInfo.duration.toDouble(),
            coverUrl = cover,
            sourceUrl = canonicalUrl,
            sourceId = vid,
            sourceProvider = "youtube",
            youtubeId = vid,
            youtubeUrl = canonicalUrl
        )

        return PlaylistInfo(
            title = title,
            author = artist,
            description = streamInfo.description?.content ?: "",
            coverUrl = cover,
            tracks = listOf(track),
            sourceUrl = canonicalUrl,
            sourceType = "youtube_track"
        )
    }

    private fun fetchPlaylist(url: String): PlaylistInfo {
        val pl = NewPipePlaylistInfo.getInfo(ServiceList.YouTube, url)
        val playlistTitle = pl.name ?: "YouTube Playlist"
        val author = pl.uploaderName ?: "YouTube"
        val coverUrl = pl.thumbnails.maxByOrNull { it.height }?.url

        val allItems = mutableListOf<StreamInfoItem>()
        allItems.addAll(pl.relatedItems.filterIsInstance<StreamInfoItem>())

        var hasMore = pl.hasNextPage()
        var nextPage = if (hasMore) pl.nextPage else null

        while (hasMore && nextPage != null) {
            try {
                val pageResult = NewPipePlaylistInfo.getMoreItems(ServiceList.YouTube, url, nextPage)
                val newItems = pageResult.items.filterIsInstance<StreamInfoItem>()
                if (newItems.isEmpty()) break
                allItems.addAll(newItems)
                hasMore = pageResult.hasNextPage()
                nextPage = if (hasMore) pageResult.nextPage else null
            } catch (_: Exception) {
                break
            }
        }

        val tracks = mutableListOf<TrackMetadata>()
        val total = allItems.size

        for (i in 0 until total) {
            val item = allItems[i]
            var title = item.name ?: "Track ${i + 1}"
            var artist = item.uploaderName ?: author

            if (title.contains(" - ")) {
                val parts = title.split(" - ", limit = 2)
                artist = parts[0].trim()
                title = parts[1].trim()
            }

            title = cleanTrackTitle(title)
            val vid = parseVideoId(item.url) ?: ""
            val trackCover = item.thumbnails.maxByOrNull { it.height }?.url ?: coverUrl

            tracks.add(
                TrackMetadata(
                    title = title,
                    artists = listOf(artist),
                    album = playlistTitle,
                    albumArtist = author,
                    durationSeconds = item.duration.toDouble(),
                    trackNumber = i + 1,
                    totalTracks = total,
                    coverUrl = trackCover,
                    sourceUrl = item.url,
                    sourceId = vid,
                    sourceProvider = "youtube",
                    youtubeId = vid,
                    youtubeUrl = item.url
                )
            )
        }

        return PlaylistInfo(
            title = playlistTitle,
            author = author,
            description = pl.description?.content ?: "",
            coverUrl = coverUrl,
            tracks = tracks,
            sourceUrl = url,
            sourceType = "youtube_playlist"
        )
    }

    suspend fun getStreamUrl(youtubeUrl: String): String = withContext(Dispatchers.IO) {
        init()
        var lastEx: Exception? = null
        for (attempt in 1..3) {
            try {
                val info = StreamInfo.getInfo(ServiceList.YouTube, youtubeUrl)
                val audioStreams = info.audioStreams
                if (audioStreams.isNotEmpty()) {
                    val best = audioStreams.maxByOrNull { it.averageBitrate } ?: audioStreams.first()
                    val url = best.url ?: best.content
                    if (!url.isNullOrBlank()) return@withContext url
                }

                // Fallback: video streams containing audio track (FFmpeg easily extracts audio)
                val videoStreams = info.videoStreams
                if (videoStreams.isNotEmpty()) {
                    val bestVid = videoStreams.firstOrNull()
                    val url = bestVid?.url ?: bestVid?.content
                    if (!url.isNullOrBlank()) return@withContext url
                }
            } catch (e: Exception) {
                lastEx = e
                kotlinx.coroutines.delay(400L * attempt)
            }
        }
        throw lastEx ?: IllegalStateException("No streams found for $youtubeUrl")
    }

    suspend fun getBestAudioStream(youtubeUrl: String): AudioStream = withContext(Dispatchers.IO) {
        init()
        var lastEx: Exception? = null
        for (attempt in 1..3) {
            try {
                val info = StreamInfo.getInfo(ServiceList.YouTube, youtubeUrl)
                val audioStreams = info.audioStreams
                if (audioStreams.isNotEmpty()) {
                    return@withContext audioStreams.maxByOrNull { it.averageBitrate } ?: audioStreams.first()
                }
            } catch (e: Exception) {
                lastEx = e
                kotlinx.coroutines.delay(400L * attempt)
            }
        }
        throw lastEx ?: IllegalStateException("No audio streams found for $youtubeUrl")
    }

    private fun cleanTrackTitle(raw: String): String {
        return raw.replace(
            Regex("(?i)\\s*[\\(\\[](Official\\s*(Audio|Video|Music\\s*Video|Lyric\\s*Video|HD|4K)?|Lyrics|Audio)[\\)\\]]"),
            ""
        ).trim()
    }
}
