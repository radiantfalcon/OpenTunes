package com.opentunes.app.core.spotify

import com.opentunes.app.core.models.PlaylistInfo
import com.opentunes.app.core.models.TrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SpotifyExtractor {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val PATHFINDER_SHA256 = "a65e12194ed5fc443a1cdebed5fabe33ca5b07b987185d63c72483867ad13cb4"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun isSpotifyUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        return clean.contains("spotify.com") || clean.startsWith("spotify:")
    }

    fun parseSpotifyLink(url: String): Pair<String?, String?> {
        val clean = url.trim()
        if (clean.startsWith("spotify:")) {
            val parts = clean.split(":")
            if (parts.size >= 3) {
                return Pair(parts[1], parts[2])
            }
        }

        try {
            val uri = URI(clean)
            val pathParts = uri.path.split("/").filter { it.isNotEmpty() }
            if (pathParts.size >= 2) {
                val type = pathParts[0]
                val id = pathParts[1].split("?")[0]
                if (type in listOf("track", "playlist", "album", "artist")) {
                    return Pair(type, id)
                }
            }
            if (pathParts.size >= 3) {
                val type = pathParts[1]
                val id = pathParts[2].split("?")[0]
                if (type in listOf("track", "playlist", "album", "artist")) {
                    return Pair(type, id)
                }
            }
        } catch (_: Exception) {}

        val pattern = Pattern.compile("(track|playlist|album|artist)[/:]([a-zA-Z0-9]{22})")
        val matcher = pattern.matcher(clean)
        if (matcher.find()) {
            return Pair(matcher.group(1), matcher.group(2))
        }

        return Pair(null, null)
    }

    suspend fun getMetadata(url: String): PlaylistInfo = withContext(Dispatchers.IO) {
        val (entityType, entityId) = parseSpotifyLink(url)
        if (entityType == null || entityId == null) {
            throw IllegalArgumentException("Invalid Spotify URL: $url")
        }

        fetchViaEmbed(entityType, entityId, url)
    }

    private fun fetchViaEmbed(entityType: String, entityId: String, originalUrl: String): PlaylistInfo {
        val embedUrl = "https://open.spotify.com/embed/$entityType/$entityId"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val html = httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Spotify embed HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }

        val pattern = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>")
        val matcher = pattern.matcher(html)
        if (!matcher.find()) {
            throw IllegalStateException("Failed to parse Spotify embed page data")
        }

        val jsonStr = matcher.group(1) ?: throw IllegalStateException("Empty Next.js data")
        val data = JSONObject(jsonStr)
        val pageProps = data.optJSONObject("props")?.optJSONObject("pageProps") ?: JSONObject()
        val state = pageProps.optJSONObject("state")?.optJSONObject("data") ?: JSONObject()
        val entity = state.optJSONObject("entity") ?: JSONObject()
        val session = pageProps.optJSONObject("state")?.optJSONObject("settings")?.optJSONObject("session")
        val token = session?.optString("accessToken") ?: ""

        return when (entityType) {
            "track" -> parseEmbedTrack(entity, originalUrl)
            "playlist" -> parseEmbedPlaylist(entity, originalUrl, token, entityId)
            "album" -> parseEmbedAlbum(entity, originalUrl)
            else -> parseEmbedTrack(entity, originalUrl)
        }
    }

    private fun extractCoverArt(entity: JSONObject): String? {
        val visImages = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
        if (visImages != null && visImages.length() > 0) {
            return visImages.getJSONObject(0).optString("url")
        }

        val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
        if (coverSources != null && coverSources.length() > 0) {
            var bestUrl = ""
            var maxHeight = 0
            for (i in 0 until coverSources.length()) {
                val src = coverSources.getJSONObject(i)
                val h = src.optInt("height", 0)
                if (h >= maxHeight) {
                    maxHeight = h
                    bestUrl = src.optString("url")
                }
            }
            if (bestUrl.isNotEmpty()) return bestUrl
        }

        val images = entity.optJSONArray("images") ?: entity.optJSONObject("album")?.optJSONArray("images")
        if (images != null && images.length() > 0) {
            return images.getJSONObject(0).optString("url")
        }

        return null
    }

    private fun parseEmbedTrack(entity: JSONObject, originalUrl: String): PlaylistInfo {
        val title = entity.optString("name").ifBlank { entity.optString("title", "Unknown Track") }
        val artistsList = mutableListOf<String>()
        val artistsJson = entity.optJSONArray("artists")
        if (artistsJson != null) {
            for (i in 0 until artistsJson.length()) {
                val a = artistsJson.getJSONObject(i).optString("name")
                if (a.isNotBlank()) artistsList.add(a)
            }
        }
        if (artistsList.isEmpty()) {
            val sub = entity.optString("subtitle")
            if (sub.isNotBlank()) artistsList.add(sub)
        }
        if (artistsList.isEmpty()) artistsList.add("Unknown Artist")

        val albumName = entity.optJSONObject("album")?.optString("name") ?: "Single"
        val durMs = entity.optLong("duration", 0L)
        val durSec = durMs / 1000.0
        val cover = extractCoverArt(entity)
        val relDate = entity.optJSONObject("releaseDate")
        val year = relDate?.optString("year")

        val track = TrackMetadata(
            title = title,
            artists = artistsList,
            album = albumName,
            albumArtist = artistsList.first(),
            durationSeconds = durSec,
            trackNumber = 1,
            totalTracks = 1,
            releaseYear = year,
            coverUrl = cover,
            sourceUrl = originalUrl,
            sourceId = entity.optString("id"),
            sourceProvider = "spotify"
        )

        return PlaylistInfo(
            title = title,
            author = artistsList.first(),
            description = "Track by ${artistsList.joinToString()}",
            coverUrl = cover,
            tracks = listOf(track),
            sourceUrl = originalUrl,
            sourceType = "spotify_track"
        )
    }

    private fun parseEmbedAlbum(entity: JSONObject, originalUrl: String): PlaylistInfo {
        val albumTitle = entity.optString("name").ifBlank { entity.optString("title", "Spotify Album") }
        val author = entity.optString("subtitle").ifBlank { "Unknown Artist" }
        val cover = extractCoverArt(entity)

        val rawTracks = entity.optJSONArray("trackList") ?: JSONArray()
        val tracks = mutableListOf<TrackMetadata>()
        val total = rawTracks.length()

        for (i in 0 until total) {
            val item = rawTracks.getJSONObject(i)
            val title = item.optString("title").ifBlank { item.optString("name", "Track ${i + 1}") }
            val sub = item.optString("subtitle", author)
            val artists = sub.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(author) }
            val durMs = item.optLong("duration", 0L)
            val durSec = durMs / 1000.0
            val trackCover = extractCoverArt(item) ?: cover
            val trackId = item.optString("uid").ifBlank { item.optString("id") }
            val trackUrl = if (trackId.isNotBlank()) "https://open.spotify.com/track/$trackId" else originalUrl

            tracks.add(
                TrackMetadata(
                    title = title,
                    artists = artists,
                    album = albumTitle,
                    albumArtist = author,
                    durationSeconds = durSec,
                    trackNumber = i + 1,
                    totalTracks = total,
                    coverUrl = trackCover,
                    sourceUrl = trackUrl,
                    sourceId = trackId,
                    sourceProvider = "spotify"
                )
            )
        }

        return PlaylistInfo(
            title = albumTitle,
            author = author,
            description = "Album by $author",
            coverUrl = cover,
            tracks = tracks,
            sourceUrl = originalUrl,
            sourceType = "spotify_album"
        )
    }

    private fun parseEmbedPlaylist(
        entity: JSONObject,
        originalUrl: String,
        token: String,
        entityId: String
    ): PlaylistInfo {
        val playlistTitle = entity.optString("name").ifBlank { entity.optString("title", "Spotify Playlist") }
        val author = entity.optString("subtitle").ifBlank { "Spotify" }
        val description = entity.optString("description", "")
        val coverUrl = extractCoverArt(entity)

        val rawTracks = mutableListOf<JSONObject>()
        val trackListJson = entity.optJSONArray("trackList")
        if (trackListJson != null) {
            for (i in 0 until trackListJson.length()) {
                rawTracks.add(trackListJson.getJSONObject(i))
            }
        }

        // If >= 100 tracks and we have a session token, paginate using Pathfinder to get ALL songs
        if (token.isNotBlank() && entityId.isNotBlank() && rawTracks.size >= 100) {
            val paged = fetchPlaylistPathfinder(entityId, token, offset = rawTracks.size)
            rawTracks.addAll(paged)
        }

        val tracks = mutableListOf<TrackMetadata>()
        val total = rawTracks.size
        for (i in 0 until total) {
            val item = rawTracks[i]
            val title = item.optString("title").ifBlank { item.optString("name", "Track ${i + 1}") }
            val sub = item.optString("subtitle")
            val artists = if (sub.isNotBlank()) {
                sub.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else listOf("Unknown Artist")
            val durMs = item.optLong("duration", 0L)
            val durSec = durMs / 1000.0
            val trackCover = item.optString("cover").ifBlank { extractCoverArt(item) ?: coverUrl }
            val trackId = item.optString("uid").ifBlank { item.optString("id") }
            val trackUrl = if (trackId.isNotBlank()) "https://open.spotify.com/track/$trackId" else originalUrl
            val albumName = item.optString("album", playlistTitle)

            tracks.add(
                TrackMetadata(
                    title = title,
                    artists = artists,
                    album = albumName,
                    albumArtist = artists.first(),
                    durationSeconds = durSec,
                    trackNumber = i + 1,
                    totalTracks = total,
                    coverUrl = trackCover,
                    sourceUrl = trackUrl,
                    sourceId = trackId,
                    sourceProvider = "spotify"
                )
            )
        }

        return PlaylistInfo(
            title = playlistTitle,
            author = author,
            description = description,
            coverUrl = coverUrl,
            tracks = tracks,
            sourceUrl = originalUrl,
            sourceType = "spotify_playlist"
        )
    }

    private fun fetchPlaylistPathfinder(entityId: String, token: String, offset: Int = 100): List<JSONObject> {
        val collected = mutableListOf<JSONObject>()
        var currOffset = offset
        val baseHttpUrl = "https://api-partner.spotify.com/pathfinder/v1/query".toHttpUrlOrNull() ?: return collected

        while (true) {
            val variables = JSONObject().apply {
                put("uri", "spotify:playlist:$entityId")
                put("offset", currOffset)
                put("limit", 100)
                put("enableWatchFeedEntrypoint", false)
            }
            val extensions = JSONObject().apply {
                put("persistedQuery", JSONObject().apply {
                    put("version", 1)
                    put("sha256Hash", PATHFINDER_SHA256)
                })
            }

            val requestUrl = baseHttpUrl.newBuilder()
                .addQueryParameter("operationName", "fetchPlaylist")
                .addQueryParameter("variables", variables.toString())
                .addQueryParameter("extensions", extensions.toString())
                .build()

            val request = Request.Builder()
                .url(requestUrl)
                .header("Authorization", "Bearer $token")
                .header("app-platform", "WebPlayer")
                .header("User-Agent", USER_AGENT)
                .build()

            try {
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return collected
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val content = root.optJSONObject("data")?.optJSONObject("playlistV2")?.optJSONObject("content")
                        ?: return collected
                    val items = content.optJSONArray("items") ?: return collected
                    val totalCount = content.optInt("totalCount", 0)

                    if (items.length() == 0) return collected

                    for (i in 0 until items.length()) {
                        val itemV2 = items.getJSONObject(i).optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                        if (itemV2.optString("__typename") == "Track") {
                            val name = itemV2.optString("name")
                            if (name.isBlank()) continue

                            val artistsArr = itemV2.optJSONObject("artists")?.optJSONArray("items")
                            val artistsList = mutableListOf<String>()
                            if (artistsArr != null) {
                                for (j in 0 until artistsArr.length()) {
                                    val artName = artistsArr.getJSONObject(j).optJSONObject("profile")?.optString("name")
                                    if (!artName.isNullOrBlank()) artistsList.add(artName)
                                }
                            }

                            val durMs = itemV2.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0L) ?: 0L
                            val uri = itemV2.optString("uri")
                            val tid = uri.split(":").lastOrNull() ?: ""
                            val albumOfTrack = itemV2.optJSONObject("albumOfTrack")
                            val albumName = albumOfTrack?.optString("name", "") ?: ""
                            val coverSources = albumOfTrack?.optJSONObject("coverArt")?.optJSONArray("sources")
                            val cover = if (coverSources != null && coverSources.length() > 0) {
                                coverSources.getJSONObject(0).optString("url")
                            } else ""

                            val obj = JSONObject().apply {
                                put("title", name)
                                put("subtitle", artistsList.joinToString(", "))
                                put("duration", durMs)
                                put("id", tid)
                                put("uid", tid)
                                put("cover", cover)
                                put("album", albumName)
                            }
                            collected.add(obj)
                        }
                    }

                    currOffset += items.length()
                    if (totalCount > 0 && currOffset >= totalCount) {
                        return collected
                    }
                }
            } catch (_: Exception) {
                break
            }
        }
        return collected
    }
}
