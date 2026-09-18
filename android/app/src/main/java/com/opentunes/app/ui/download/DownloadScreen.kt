package com.opentunes.app.ui.download

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.opentunes.app.R
import com.opentunes.app.core.download.DownloadManager
import com.opentunes.app.core.library.LibraryRepository
import com.opentunes.app.core.library.RecentlyPlayedManager
import com.opentunes.app.core.library.RecommendationManager
import com.opentunes.app.core.models.DownloadTask
import com.opentunes.app.core.models.PlaylistInfo
import com.opentunes.app.core.models.TrackMetadata
import com.opentunes.app.core.models.TrackStatus
import com.opentunes.app.core.spotify.SpotifyExtractor
import com.opentunes.app.core.youtube.YouTubeMusicExtractor
import com.opentunes.app.service.MusicPlayerManager
import com.opentunes.app.ui.theme.AccentBlue
import com.opentunes.app.ui.theme.DarkCard
import com.opentunes.app.ui.theme.DarkCardBorder
import com.opentunes.app.ui.theme.Glassmorphism
import com.opentunes.app.ui.theme.PureBlack
import com.opentunes.app.ui.theme.TextSecondary
import com.opentunes.app.ui.theme.White
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object DownloadSearchState {
    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var searchResults by mutableStateOf<List<TrackMetadata>>(emptyList())
    var resolvedPlaylist by mutableStateOf<PlaylistInfo?>(null)
    var isResolvingUrl by mutableStateOf(false)
}

@Composable
fun DownloadScreen(modifier: Modifier = Modifier) {
    var searchQuery by remember { DownloadSearchState::searchQuery }
    var isSearching by remember { DownloadSearchState::isSearching }
    var searchResults by remember { DownloadSearchState::searchResults }
    var resolvedPlaylist by remember { DownloadSearchState::resolvedPlaylist }
    var isResolvingUrl by remember { DownloadSearchState::isResolvingUrl }

    val tasks by DownloadManager.tasks.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Live search debouncer
    LaunchedEffect(searchQuery) {
        debounceJob?.cancel()
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            searchResults = emptyList()
            resolvedPlaylist = null
            isSearching = false
            return@LaunchedEffect
        }

        // Check if query is a direct URL
        if (SpotifyExtractor.isSpotifyUrl(query) || YouTubeMusicExtractor.isYouTubeUrl(query)) {
            debounceJob = scope.launch {
                isResolvingUrl = true
                try {
                    resolvedPlaylist = if (SpotifyExtractor.isSpotifyUrl(query)) {
                        SpotifyExtractor.getMetadata(query)
                    } else {
                        YouTubeMusicExtractor.getMetadata(query)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error resolving link: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isResolvingUrl = false
                }
            }
        } else {
            // Live song search
            debounceJob = scope.launch {
                delay(350) // Debounce 350ms
                isSearching = true
                try {
                    val results = YouTubeMusicExtractor.searchLive(query)
                    searchResults = results
                } catch (_: Exception) {
                    searchResults = emptyList()
                } finally {
                    isSearching = false
                }
            }
        }
    }

    val activeTasks = tasks.filter {
        it.status.value in listOf(
            TrackStatus.DOWNLOADING,
            TrackStatus.MATCHING,
            TrackStatus.CONVERTING,
            TrackStatus.TAGGING,
            TrackStatus.QUEUED,
            TrackStatus.PAUSED
        )
    }

    val completedTasks = tasks.filter {
        it.status.value in listOf(
            TrackStatus.COMPLETED,
            TrackStatus.SKIPPED,
            TrackStatus.FAILED,
            TrackStatus.CANCELLED
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        // Header (Scaled down compact ASCII logo)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, top = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_cli_logo),
                    contentDescription = "OpenTunes",
                    modifier = Modifier
                        .height(34.dp)
                        .fillMaxWidth(0.72f),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            }
        }

        // Search Input Bar (Liquid Glass with focus glow motion)
        item {
            var isFocused by remember { mutableStateOf(false) }
            val borderBrush = if (isFocused) {
                Glassmorphism.glowBorder(AccentBlue, 0.85f)
            } else {
                Glassmorphism.specularBorder(0.35f, 0.12f)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (isFocused) Color(0x22FFFFFF) else Color(0x14FFFFFF), RoundedCornerShape(26.dp))
                    .border(1.dp, borderBrush, RoundedCornerShape(26.dp))
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    placeholder = {
                        Text(
                            text = "Enter Spotify or YouTube link, or search song...",
                            color = Color(0xFF88888E),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isFocused) AccentBlue else Color(0xFFAAAAAA),
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(onClick = {
                                searchQuery = ""
                                searchResults = emptyList()
                                resolvedPlaylist = null
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color(0xFFAAAAAA)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        cursorColor = AccentBlue
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (searchQuery.isBlank() && searchResults.isNotEmpty()) {
            searchResults = emptyList()
        }

        val showFeeds = searchQuery.isBlank() &&
                !isSearching &&
                !isResolvingUrl &&
                resolvedPlaylist == null &&
                activeTasks.isEmpty() &&
                completedTasks.isEmpty()

        item {
            AnimatedVisibility(
                visible = showFeeds,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                HomeMusicFeeds(
                    onTrackSelected = { track ->
                        playTrackFromFeed(context, track, scope)
                    }
                )
            }
        }

        // Active Download Monitor Section (Placed right below search bar when active/completed tasks exist)
        if (activeTasks.isNotEmpty() || completedTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DOWNLOAD MONITOR",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (completedTasks.isNotEmpty()) {
                        Text(
                            text = "CLEAR FINISHED",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                DownloadManager.clearCompleted()
                            }
                        )
                    }
                }
            }

            items(activeTasks, key = { it.id }) { task ->
                DownloadTaskCard(task = task)
            }

            items(completedTasks, key = { it.id }) { task ->
                CompletedTaskCard(task = task)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Resolved URL Playlist/Track Preview Card
        if (isResolvingUrl) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(32.dp))
                }
            }
        } else if (resolvedPlaylist != null) {
            val pl = resolvedPlaylist!!
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = pl.coverUrl,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.DarkGray),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.title,
                                color = White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${pl.author} • ${pl.totalTracks} songs",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    DownloadManager.enqueuePlaylist(pl)
                                    Toast.makeText(context, "Queued ${pl.totalTracks} songs", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = PureBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download All (${pl.totalTracks})", color = PureBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Live Search Results
        if (isSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(28.dp))
                }
            }
        } else if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
            item {
                Text(
                    text = "SEARCH RESULTS (${searchResults.size})",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(searchResults) { track ->
                val isAlreadyQueued = tasks.any { it.track.title == track.title && it.track.primaryArtist == track.primaryArtist }
                SearchResultItem(
                    track = track,
                    isQueued = isAlreadyQueued,
                    onDownloadClick = {
                        DownloadManager.enqueue(track)
                        Toast.makeText(context, "Queued: ${track.title}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}


@Composable
fun SearchResultItem(
    track: TrackMetadata,
    isQueued: Boolean = false,
    onDownloadClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Glassmorphism.specularBorder(0.25f, 0.08f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
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

            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isQueued) Color(0xFF1E2F23) else Color(0x22FFFFFF), CircleShape)
                    .border(1.dp, if (isQueued) Color(0xFF4CAF50) else Color(0x33FFFFFF), CircleShape)
            ) {
                Icon(
                    imageVector = if (isQueued) Icons.Default.CheckCircle else Icons.Default.Download,
                    contentDescription = if (isQueued) "Queued" else "Download",
                    tint = if (isQueued) Color(0xFF4CAF50) else AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DownloadTaskCard(task: DownloadTask) {
    val status by task.status.collectAsState()
    val progress by task.progressPercent.collectAsState()
    val speed by task.speedStr.collectAsState()
    val eta by task.etaStr.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x18FFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Glassmorphism.glowBorder(AccentBlue, 0.45f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (task.totalTracks > 1) "[${task.index}/${task.totalTracks}] ${task.track.title}" else task.track.title,
                        color = White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${task.track.primaryArtist} • ${status.displayName.uppercase()}",
                        color = AccentBlue,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status == TrackStatus.DOWNLOADING || status == TrackStatus.QUEUED) {
                        IconButton(onClick = { DownloadManager.pauseTask(task.id) }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = White)
                        }
                    } else if (status == TrackStatus.PAUSED) {
                        IconButton(onClick = { DownloadManager.resumeTask(task.id) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = AccentBlue)
                        }
                    }

                    IconButton(onClick = { DownloadManager.cancelTask(task.id) }) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = Color(0xFFEF5350))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { (progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentBlue,
                trackColor = Color(0xFF333336)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${progress.toInt()}%",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                if (speed.isNotBlank()) {
                    Text(
                        text = "$speed • ETA $eta",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedTaskCard(task: DownloadTask) {
    val status by task.status.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x10FFFFFF), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (status == TrackStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (status == TrackStatus.COMPLETED) Color(0xFF4CAF50) else Color(0xFFE57373),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.track.title,
                    color = White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${task.track.primaryArtist} • ${status.displayName} (${task.options.format.uppercase()} ${task.options.bitrate})",
                    color = TextSecondary,
                    fontSize = 11.sp
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

private fun playTrackFromFeed(
    context: android.content.Context,
    track: TrackMetadata,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val sourceUrl = track.sourceUrl
    if (sourceUrl != null && java.io.File(sourceUrl).exists()) {
        MusicPlayerManager.playTrack(context, track)
    } else {
        scope.launch {
            Toast.makeText(context, "Playing: ${track.title}...", Toast.LENGTH_SHORT).show()
            try {
                val targetUrl = track.youtubeUrl ?: sourceUrl ?: "https://www.youtube.com/watch?v=${track.youtubeId}"
                val streamUrl = YouTubeMusicExtractor.getStreamUrl(targetUrl)
                val playable = track.copy(sourceUrl = streamUrl)
                MusicPlayerManager.playTrack(context, playable)
            } catch (e: Exception) {
                Toast.makeText(context, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun HomeMusicFeeds(
    onTrackSelected: (TrackMetadata) -> Unit
) {
    val recentTracks by RecentlyPlayedManager.recentTracks.collectAsState()
    val downloadedTracks by LibraryRepository.tracks.collectAsState()

    val effectiveRecent = remember(recentTracks, downloadedTracks) {
        val baseList = if (recentTracks.isNotEmpty()) recentTracks else downloadedTracks.take(10)
        val trackMap = downloadedTracks.associateBy { it.sourceUrl ?: it.title }
        baseList.map { track ->
            if (track.coverUrl.isNullOrBlank()) {
                val match = trackMap[track.sourceUrl ?: track.title]
                if (match != null && !match.coverUrl.isNullOrBlank()) {
                    track.copy(coverUrl = match.coverUrl)
                } else {
                    track
                }
            } else {
                track
            }
        }
    }

    val recommendedTracks = remember(downloadedTracks) {
        RecommendationManager.getRecommendedTracks(downloadedTracks)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp)
    ) {
        // Section: RECOMMENDED
        if (recommendedTracks.isNotEmpty()) {
            Text(
                text = "RECOMMENDED",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(recommendedTracks, key = { it.sourceId ?: (it.sourceUrl ?: it.title) }) { track ->
                    FeedMusicCard(
                        track = track,
                        onClick = { onTrackSelected(track) }
                    )
                }
            }
        }

        // Section: RECENTLY PLAYED
        if (effectiveRecent.isNotEmpty()) {
            Text(
                text = "RECENTLY PLAYED",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(effectiveRecent, key = { it.sourceUrl ?: (it.sourceId ?: it.title) }) { track ->
                    FeedMusicCard(
                        track = track,
                        onClick = { onTrackSelected(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun FeedMusicCard(
    track: TrackMetadata,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val coverModel = remember(track.coverUrl, track.sourceUrl) {
        if (!track.coverUrl.isNullOrBlank()) {
            track.coverUrl
        } else if (track.sourceUrl != null) {
            val f = java.io.File(track.sourceUrl)
            val coversDir = java.io.File(context.cacheDir, "covers")
            val coverFile = java.io.File(coversDir, "${f.nameWithoutExtension}_cover.jpg")
            if (coverFile.exists() && coverFile.length() > 0L) {
                "file://${coverFile.absolutePath}"
            } else {
                null
            }
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B1C22))
                .border(
                    width = 1.dp,
                    brush = Glassmorphism.specularBorder(0.35f, 0.10f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            if (!coverModel.isNullOrBlank()) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .border(0.5.dp, Color(0x66FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = AccentBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            color = White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.primaryArtist,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
