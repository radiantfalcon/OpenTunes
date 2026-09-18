package com.opentunes.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.opentunes.app.core.config.AppConfig
import com.opentunes.app.core.library.LibraryRepository
import com.opentunes.app.core.models.TrackMetadata
import com.opentunes.app.service.MusicPlayerManager
import com.opentunes.app.ui.theme.AccentBlue
import com.opentunes.app.ui.theme.Glassmorphism
import com.opentunes.app.ui.theme.PureBlack
import com.opentunes.app.ui.theme.TextSecondary
import com.opentunes.app.ui.theme.White
import java.io.File

data class GalleryView(val title: String, val tracks: List<TrackMetadata>)

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val downloadedTracks by LibraryRepository.tracks.collectAsState()
    val isScanning by LibraryRepository.isScanning.collectAsState()

    val currentPlayingTrack by MusicPlayerManager.currentTrack.collectAsState()
    val isPlaying by MusicPlayerManager.isPlaying.collectAsState()
    val isShuffle by MusicPlayerManager.isShuffle.collectAsState()

    var activeGallery by remember { mutableStateOf<GalleryView?>(null) }

    // Hardware back handler returns from gallery detail to playlists
    BackHandler(enabled = activeGallery != null) {
        activeGallery = null
    }

    LaunchedEffect(Unit) {
        LibraryRepository.rescan()
    }

    // Separate tracks into Playlists (subfolders) vs Single Downloads
    val baseDir = remember(AppConfig.options.value.outputDir) {
        File(AppConfig.options.value.outputDir).canonicalFile
    }

    val (playlistGroups, _) = remember(downloadedTracks, baseDir) {
        val groups = mutableMapOf<String, MutableList<TrackMetadata>>()
        val singles = mutableListOf<TrackMetadata>()

        for (t in downloadedTracks) {
            val f = t.sourceUrl?.let { File(it).canonicalFile }
            val parent = f?.parentFile
            if (parent != null && !parent.absolutePath.equals(baseDir.absolutePath, ignoreCase = true)) {
                groups.getOrPut(parent.name) { mutableListOf() }.add(t)
            } else {
                singles.add(t)
            }
        }
        Pair(groups, singles)
    }

    AnimatedContent(
        targetState = activeGallery,
        transitionSpec = {
            if (targetState != null) {
                // Opening gallery detail
                (slideInHorizontally { width -> width / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> -width / 3 } + fadeOut())
            } else {
                // Going back to playlists overview
                (slideInHorizontally { width -> -width / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> width / 3 } + fadeOut())
            }
        },
        label = "LibraryGalleryTransition",
        modifier = modifier.fillMaxSize().background(PureBlack)
    ) { gallery ->
        if (gallery == null) {
            // Level 1: Root Playlists Overview
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Library",
                                    color = White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                IconButton(
                                    onClick = {
                                        MusicPlayerManager.toggleShuffle(context, downloadedTracks)
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isShuffle) AccentBlue.copy(alpha = 0.2f) else Color.Transparent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Randomize Playback",
                                        tint = if (isShuffle) AccentBlue else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            val count = downloadedTracks.size
                            if (count > 0) {
                                val plCount = playlistGroups.size
                                val subtitle = if (plCount > 0) {
                                    "$plCount ${if (plCount == 1) "playlist" else "playlists"} • $count ${if (count == 1) "track" else "tracks"}"
                                } else {
                                    "$count downloaded ${if (count == 1) "track" else "tracks"}"
                                }
                                Text(
                                    text = subtitle,
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { LibraryRepository.rescan(force = true) },
                            enabled = !isScanning
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (isScanning) AccentBlue else TextSecondary
                            )
                        }
                    }
                }

                if (downloadedTracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isScanning) "Scanning music library..." else "No downloaded music found",
                                color = Color(0xFF75757A),
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Section title: DOWNLOADED PLAYLISTS
                    if (playlistGroups.isNotEmpty()) {
                        item {
                            Text(
                                text = "DOWNLOADED PLAYLISTS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // List of downloaded playlists
                        items(playlistGroups.entries.toList(), key = { it.key }) { entry ->
                            PlaylistCard(
                                name = entry.key,
                                tracks = entry.value,
                                onClick = {
                                    activeGallery = GalleryView(entry.key, entry.value)
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // Section title & card for ALL DOWNLOADED SONGS
                    item {
                        Text(
                            text = "ALL MUSIC",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        AllSongsCollageCard(
                            allTracks = downloadedTracks,
                            onClick = {
                                activeGallery = GalleryView("All Downloaded Songs", downloadedTracks)
                            }
                        )
                    }
                }
            }
        } else {
            // Level 2: Full Gallery View for selected playlist or all songs
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { activeGallery = null },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1AFFFFFF))
                                    .border(1.dp, Glassmorphism.specularBorder(0.3f, 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to Playlists",
                                    tint = White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gallery.title,
                                    color = White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${gallery.tracks.size} tracks",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                MusicPlayerManager.toggleShuffle(context, gallery.tracks)
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isShuffle) AccentBlue.copy(alpha = 0.2f) else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle Playlist",
                                tint = if (isShuffle) AccentBlue else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                items(gallery.tracks, key = { it.sourceUrl ?: it.title }) { track ->
                    val isCurrent = currentPlayingTrack?.sourceUrl == track.sourceUrl

                    LibraryTrackItem(
                        track = track,
                        isCurrentPlaying = isCurrent,
                        isPlaying = isPlaying && isCurrent,
                        onClick = {
                            MusicPlayerManager.playTrack(context, track, gallery.tracks)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    name: String,
    tracks: List<TrackMetadata>,
    onClick: () -> Unit
) {
    val firstCover = tracks.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x18FFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, Glassmorphism.specularBorder(0.35f, 0.10f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF22242E))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!firstCover.isNullOrBlank()) {
                    AsyncImage(
                        model = firstCover,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (tracks.size == 1) "1 downloaded track" else "${tracks.size} downloaded tracks",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = Color(0x99FFFFFF),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun AllSongsCollageCard(
    allTracks: List<TrackMetadata>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x22FFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, Glassmorphism.glowBorder(AccentBlue, 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CollageCoverArt(tracks = allTracks)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "All Downloaded Songs",
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (allTracks.size == 1) "1 downloaded track" else "${allTracks.size} downloaded tracks",
                    color = AccentBlue,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = AccentBlue,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun CollageCoverArt(
    tracks: List<TrackMetadata>,
    modifier: Modifier = Modifier
) {
    val validCovers = remember(tracks) {
        tracks.mapNotNull { it.coverUrl }.filter { it.isNotBlank() }.distinct().take(4)
    }

    Box(
        modifier = modifier
            .size(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E2028))
            .border(1.dp, Glassmorphism.specularBorder(0.35f, 0.10f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            validCovers.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = validCovers[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(model = validCovers[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = validCovers[2], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(model = validCovers[3], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                }
            }
            validCovers.isNotEmpty() -> {
                AsyncImage(model = validCovers.first(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            else -> {
                Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun LibraryTrackItem(
    track: TrackMetadata,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCurrentPlaying) Color(0x301E2E42) else Color(0x14FFFFFF),
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                if (isCurrentPlaying) Glassmorphism.glowBorder(AccentBlue, 0.7f) else Glassmorphism.specularBorder(0.25f, 0.08f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF26262A)),
                contentAlignment = Alignment.Center
            ) {
                if (!track.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = "Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = AccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else if (isCurrentPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Selected",
                            tint = AccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isCurrentPlaying) AccentBlue else White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.primaryArtist} • ${formatDuration(track.durationSeconds)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val s = seconds.toInt()
    val m = s / 60
    val remS = s % 60
    return String.format("%d:%02d", m, remS)
}
