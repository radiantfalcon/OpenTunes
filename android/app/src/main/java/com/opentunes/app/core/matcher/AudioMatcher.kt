package com.opentunes.app.core.matcher

import com.opentunes.app.core.models.TrackMetadata
import com.opentunes.app.core.youtube.YouTubeMusicExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AudioMatcher {

    suspend fun getRankedCandidates(track: TrackMetadata, limit: Int = 6): List<String> = withContext(Dispatchers.IO) {
        YouTubeMusicExtractor.init()

        val candidates = mutableMapOf<String, Double>()

        // 1. Direct candidate if YouTube URL is provided
        if (!track.youtubeUrl.isNullOrBlank() && track.sourceProvider == "youtube") {
            candidates[track.youtubeUrl!!] = 100.0
        }

        val cleanTitle = track.title.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").trim()
        val queries = mutableListOf<String>()
        queries.add("${track.primaryArtist} - $cleanTitle Official Audio")
        queries.add("${track.primaryArtist} $cleanTitle audio")
        queries.add("$cleanTitle ${track.primaryArtist}")
        queries.add("${track.title} ${track.primaryArtist}")
        queries.add("${track.primaryArtist} - ${track.title}")
        if (cleanTitle.isNotBlank()) queries.add(cleanTitle)

        // Tier 1: Search YouTube Music ("music_songs")
        for (query in queries) {
            val items = searchYouTubeItems(query, Collections.singletonList("music_songs"))
            for (item in items.take(6)) {
                val url = item.url
                val score = scoreCandidate(track, item)
                val existing = candidates[url] ?: 0.0
                if (score > existing) {
                    candidates[url] = score
                }
            }
        }

        // Tier 2: If fewer than 3 candidates found, search standard YouTube videos
        if (candidates.size < 3) {
            for (query in queries.take(4)) {
                val items = searchYouTubeItems(query, Collections.singletonList("videos"))
                for (item in items.take(4)) {
                    val url = item.url
                    val score = scoreCandidate(track, item)
                    val existing = candidates[url] ?: 0.0
                    if (score > existing) {
                        candidates[url] = score
                    }
                }
            }
        }

        // Tier 3: If still empty, search all YouTube categories without filter
        if (candidates.isEmpty()) {
            for (query in queries.take(3)) {
                val items = searchYouTubeItems(query, Collections.emptyList())
                for (item in items.take(4)) {
                    val url = item.url
                    val score = scoreCandidate(track, item)
                    val existing = candidates[url] ?: 0.0
                    if (score > existing) {
                        candidates[url] = score
                    }
                }
            }
        }

        // Tier 4: Live search fallback
        if (candidates.isEmpty()) {
            val fallback = YouTubeMusicExtractor.searchLive("$cleanTitle ${track.primaryArtist}")
            for (item in fallback.take(limit)) {
                val url = item.youtubeUrl ?: continue
                candidates[url] = 50.0
            }
        }

        val sorted = candidates.entries.sortedByDescending { it.value }.take(limit).map { it.key }
        if (sorted.isNotEmpty()) {
            track.youtubeUrl = sorted.first()
            track.youtubeId = YouTubeMusicExtractor.parseVideoId(sorted.first())
        }
        sorted
    }

    suspend fun getBroadFallbackCandidates(track: TrackMetadata, limit: Int = 4): List<String> = withContext(Dispatchers.IO) {
        YouTubeMusicExtractor.init()
        val candidates = mutableListOf<String>()
        val cleanTitle = track.title.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").trim()
        val queries = listOf(
            "$cleanTitle ${track.primaryArtist}",
            "${track.title} ${track.primaryArtist}",
            track.title
        )
        for (q in queries) {
            val items = searchYouTubeItems(q, Collections.emptyList())
            for (item in items) {
                val url = item.url
                if (!candidates.contains(url)) {
                    candidates.add(url)
                    if (candidates.size >= limit) return@withContext candidates
                }
            }
        }
        candidates
    }

    private fun searchYouTubeItems(query: String, filter: List<String>): List<StreamInfoItem> {
        return try {
            val qh = ServiceList.YouTube.searchQHFactory.fromQuery(query, filter, "")
            val info = SearchInfo.getInfo(ServiceList.YouTube, qh)
            info.relatedItems.filterIsInstance<StreamInfoItem>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun matchTrack(track: TrackMetadata): String {
        return getRankedCandidates(track, 1).firstOrNull()
            ?: throw IllegalStateException("No audio candidates found on YouTube for '${track.artistStr} - ${track.title}'")
    }

    private fun scoreCandidate(target: TrackMetadata, candidate: StreamInfoItem): Double {
        val candTitle = (candidate.name ?: "").lowercase()
        val candChannel = (candidate.uploaderName ?: "").lowercase()
        val candDuration = candidate.duration.toDouble()

        val targetTitle = target.title.lowercase()
        val targetArtist = target.primaryArtist.lowercase()
        val targetDuration = target.durationSeconds

        var score = 0.0

        val cleanCandTitle = candTitle.replace(
            Regex("(?i)[\\(\\[](official\\s*(audio|video|music\\s*video|lyric\\s*video|hd|4k)?|lyrics|audio)[\\)\\]]"),
            ""
        ).trim()

        // Title match
        val titleRatio = tokenSimilarity(targetTitle, cleanCandTitle)
        score += (titleRatio / 100.0) * 45.0

        // Artist match
        val artistInChannel = candChannel.contains(targetArtist) || targetArtist.contains(candChannel)
        val artistInTitle = candTitle.contains(targetArtist)
        if (artistInChannel || artistInTitle) {
            score += 25.0
        } else {
            for (art in target.artists.drop(1)) {
                if (candTitle.contains(art.lowercase()) || candChannel.contains(art.lowercase())) {
                    score += 15.0
                    break
                }
            }
        }

        // Duration match
        if (targetDuration > 0 && candDuration > 0) {
            val diff = abs(targetDuration - candDuration)
            when {
                diff <= 4.0 -> score += 20.0
                diff <= 8.0 -> score += 15.0
                diff <= 15.0 -> score += 5.0
                diff <= 30.0 -> score -= 15.0
                else -> score -= 40.0
            }
        } else {
            score += 10.0
        }

        // Official / Topic channel bonus
        if (candChannel.endsWith("- topic")) {
            score += 10.0
        } else if (candTitle.contains("official audio") || candTitle.contains("official music video")) {
            score += 6.0
        }

        // Unwanted keywords penalty
        val unwanted = listOf("cover", "karaoke", "live", "concert", "reaction", "slowed", "reverb", "8d audio", "1 hour", "10 hours", "extended mix")
        for (kw in unwanted) {
            if (candTitle.contains(kw) && !targetTitle.contains(kw)) {
                if (kw in listOf("cover", "karaoke", "reaction", "1 hour", "10 hours")) {
                    score -= 40.0
                } else {
                    score -= 20.0
                }
            }
        }

        return max(0.0, min(100.0, score))
    }

    private fun tokenSimilarity(s1: String, s2: String): Double {
        val t1 = s1.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        val t2 = s2.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (t1.isEmpty() || t2.isEmpty()) return 50.0
        val intersection = t1.intersect(t2).size
        val union = t1.union(t2).size
        return (intersection.toDouble() / union.toDouble()) * 100.0
    }
}
