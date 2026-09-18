package com.opentunes.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.opentunes.app.core.models.TrackMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class RepeatMode {
    OFF, ALL, ONE
}

object MusicPlayerManager {
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _currentTrack = MutableStateFlow<TrackMetadata?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _isFullScreenPlayerVisible = MutableStateFlow(false)
    val isFullScreenPlayerVisible = _isFullScreenPlayerVisible.asStateFlow()

    private var playlist = listOf<TrackMetadata>()
    private var currentIndex = -1
    private var pendingPlaylist: List<TrackMetadata>? = null
    private var pendingIndex = -1

    fun setFullScreenPlayerVisible(visible: Boolean) {
        _isFullScreenPlayerVisible.value = visible
    }

    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.shuffleModeEnabled = _isShuffle.value
        exoPlayer.repeatMode = toExoRepeatMode(_repeatMode.value)

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startProgressTracking() else stopProgressTracking()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffle.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentIdx = exoPlayer.currentMediaItemIndex
                if (currentIdx in playlist.indices) {
                    currentIndex = currentIdx
                    val t = playlist[currentIdx]
                    _currentTrack.value = t
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                    _currentPosition.value = 0L
                    com.opentunes.app.core.library.RecentlyPlayedManager.recordPlayed(t)
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("MusicPlayerManager", "Player error: ${error.errorCodeName} (${error.message})", error)
                player?.let { p ->
                    try {
                        val pos = p.currentPosition
                        val idx = p.currentMediaItemIndex
                        p.stop()
                        p.prepare()
                        if (idx in playlist.indices) {
                            p.seekTo(idx, pos)
                        }
                        p.play()
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlayerManager", "Auto-recovery failed: ${e.message}", e)
                    }
                }
            }
        })

        // Process pending playback if queued before service attached
        pendingPlaylist?.let { pl ->
            val idx = pendingIndex.coerceIn(0, pl.lastIndex)
            applyPlaylistToPlayer(exoPlayer, pl, idx)
            pendingPlaylist = null
            pendingIndex = -1
        }
    }

    private fun toMediaItem(t: TrackMetadata): MediaItem {
        val localFile = File(t.sourceUrl ?: "")
        val mediaUri = if (localFile.exists()) Uri.fromFile(localFile) else Uri.parse(t.sourceUrl ?: "")
        return MediaItem.Builder()
            .setMediaId(t.sourceUrl ?: t.title)
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artistStr)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.coverUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    private fun applyPlaylistToPlayer(exo: ExoPlayer, pl: List<TrackMetadata>, idx: Int) {
        val mediaItems = pl.map { toMediaItem(it) }
        try {
            exo.stop()
            exo.clearMediaItems()
            exo.shuffleModeEnabled = _isShuffle.value
            exo.repeatMode = toExoRepeatMode(_repeatMode.value)
            exo.setMediaItems(mediaItems, idx, 0L)
            exo.prepare()
            exo.play()
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerManager", "Error applying playlist: ${e.message}", e)
        }
    }

    fun playTrack(context: Context, track: TrackMetadata, fullPlaylist: List<TrackMetadata> = listOf(track)) {
        playlist = if (fullPlaylist.isNotEmpty()) fullPlaylist else listOf(track)
        val index = playlist.indexOfFirst { it.sourceUrl == track.sourceUrl || it.sourceId == track.sourceId }.coerceAtLeast(0)
        currentIndex = index
        _currentTrack.value = playlist[index]
        com.opentunes.app.core.library.RecentlyPlayedManager.recordPlayed(playlist[index])

        val serviceIntent = Intent(context, MusicPlaybackService::class.java)
        try {
            context.startService(serviceIntent)
        } catch (_: Exception) {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        }

        val p = player
        if (p != null) {
            applyPlaylistToPlayer(p, playlist, index)
        } else {
            pendingPlaylist = playlist
            pendingIndex = index
        }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun skipNext() {
        player?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            } else if (playlist.isNotEmpty()) {
                it.seekTo(0, 0L)
            }
        }
    }

    fun skipPrevious() {
        player?.let {
            if (it.hasPreviousMediaItem()) {
                it.seekToPreviousMediaItem()
            } else if (playlist.isNotEmpty()) {
                it.seekTo(0, 0L)
            }
        }
    }

    fun toggleShuffle(context: Context? = null, availableTracks: List<TrackMetadata>? = null) {
        val nextShuffle = !_isShuffle.value
        _isShuffle.value = nextShuffle
        player?.shuffleModeEnabled = nextShuffle

        // If turning ON and nothing is currently playing, start playing a random track
        if (nextShuffle && _currentTrack.value == null && !availableTracks.isNullOrEmpty() && context != null) {
            val randomTrack = availableTracks.random()
            playTrack(context, randomTrack, availableTracks)
        }
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = nextMode
        player?.repeatMode = toExoRepeatMode(nextMode)
    }

    private fun toExoRepeatMode(mode: RepeatMode): Int {
        return when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }


    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive) {
                player?.let {
                    _currentPosition.value = it.currentPosition.coerceAtLeast(0L)
                    _duration.value = it.duration.coerceAtLeast(0L)
                }
                delay(300)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
