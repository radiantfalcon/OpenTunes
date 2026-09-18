package com.opentunes.app.core.library

import com.opentunes.app.core.models.TrackMetadata
import java.io.File
import java.util.Random

object RecommendationManager {
    // Fresh random seed generated once per process lifecycle
    private val sessionSeed: Long = System.currentTimeMillis()
    private var sessionTrackUrls: List<String> = emptyList()
    private var lastTrackCount: Int = -1

    /**
     * Returns a randomized subset of downloaded tracks.
     * The selection is randomized per app launch/process session, but remains
     * stable while navigating tabs or interacting within the same app session.
     * Resolves dynamically against the live [allTracks] list so updated covers
     * and metadata propagate immediately.
     */
    fun getRecommendedTracks(allTracks: List<TrackMetadata>, limit: Int = 10): List<TrackMetadata> {
        if (allTracks.isEmpty()) return emptyList()

        if (sessionTrackUrls.isEmpty() || allTracks.size != lastTrackCount) {
            lastTrackCount = allTracks.size
            val rng = Random(sessionSeed)
            sessionTrackUrls = allTracks.mapNotNull { it.sourceUrl }.shuffled(rng).take(limit)
        }

        val trackMap = allTracks.associateBy { it.sourceUrl }
        return sessionTrackUrls.mapNotNull { url ->
            val track = trackMap[url] ?: return@mapNotNull null
            if (track.coverUrl.isNullOrBlank() && track.sourceUrl != null) {
                // Safeguard check for local cache file
                val f = File(track.sourceUrl)
                val parentCache = f.parentFile?.parentFile?.let { File(it, "cache/covers") }
                val coverFile = File(parentCache, "${f.nameWithoutExtension}_cover.jpg")
                if (coverFile.exists() && coverFile.length() > 0L) {
                    track.copy(coverUrl = "file://${coverFile.absolutePath}")
                } else {
                    track
                }
            } else {
                track
            }
        }
    }
}
