package com.opentunes.app.core.library

import android.content.Context
import android.content.SharedPreferences
import com.opentunes.app.core.models.TrackMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object RecentlyPlayedManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    private val _recentTracks = MutableStateFlow<List<TrackMetadata>>(emptyList())
    val recentTracks = _recentTracks.asStateFlow()

    private const val PREFS_NAME = "opentunes_recent_played"
    private const val KEY_RECENT = "recent_tracks_json"
    private const val MAX_RECENT = 25

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadRecent()
    }

    fun recordPlayed(track: TrackMetadata) {
        scope.launch {
            var toRecord = track
            if (toRecord.coverUrl.isNullOrBlank()) {
                val match = LibraryRepository.tracks.value.find {
                    (it.sourceUrl != null && it.sourceUrl == track.sourceUrl) ||
                            it.title.equals(track.title, ignoreCase = true)
                }
                if (match?.coverUrl != null && match.coverUrl.isNotBlank()) {
                    toRecord = toRecord.copy(coverUrl = match.coverUrl)
                }
            }
            val current = _recentTracks.value.toMutableList()
            // Deduplicate
            current.removeAll {
                (it.sourceUrl != null && it.sourceUrl == toRecord.sourceUrl) ||
                        (it.title.equals(toRecord.title, ignoreCase = true) &&
                                it.primaryArtist.equals(toRecord.primaryArtist, ignoreCase = true))
            }
            // Add to head
            current.add(0, toRecord)
            val trimmed = current.take(MAX_RECENT)
            _recentTracks.value = trimmed
            saveRecent(trimmed)
        }
    }

    private fun loadRecent() {
        val p = prefs ?: return
        val raw = p.getString(KEY_RECENT, null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<TrackMetadata>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val artistsArr = obj.optJSONArray("artists")
                val artists = mutableListOf<String>()
                if (artistsArr != null) {
                    for (j in 0 until artistsArr.length()) {
                        artists.add(artistsArr.getString(j))
                    }
                }
                list.add(
                    TrackMetadata(
                        title = obj.optString("title", "Unknown"),
                        artists = if (artists.isNotEmpty()) artists else listOf(obj.optString("artist", "Unknown")),
                        album = obj.optString("album", "OpenTunes"),
                        albumArtist = obj.optString("albumArtist", "Unknown"),
                        durationSeconds = obj.optDouble("duration", 0.0),
                        sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() },
                        sourceId = obj.optString("sourceId").takeIf { it.isNotBlank() },
                        coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                        sourceProvider = obj.optString("sourceProvider", "local")
                    )
                )
            }
            _recentTracks.value = list
        } catch (_: Exception) {}
    }

    private fun saveRecent(list: List<TrackMetadata>) {
        val p = prefs ?: return
        try {
            val arr = JSONArray()
            for (t in list) {
                val obj = JSONObject().apply {
                    put("title", t.title)
                    val artArr = JSONArray()
                    t.artists.forEach { artArr.put(it) }
                    put("artists", artArr)
                    put("album", t.album)
                    put("albumArtist", t.albumArtist)
                    put("duration", t.durationSeconds)
                    put("sourceUrl", t.sourceUrl ?: "")
                    put("sourceId", t.sourceId ?: "")
                    put("coverUrl", t.coverUrl ?: "")
                    put("sourceProvider", t.sourceProvider)
                }
                arr.put(obj)
            }
            p.edit().putString(KEY_RECENT, arr.toString()).apply()
        } catch (_: Exception) {}
    }
}
