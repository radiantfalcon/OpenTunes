package com.opentunes.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.opentunes.app.service.MusicPlayerManager
import com.opentunes.app.service.RepeatMode
import com.opentunes.app.ui.theme.AccentBlue
import com.opentunes.app.ui.theme.Glassmorphism
import com.opentunes.app.ui.theme.PureBlack
import com.opentunes.app.ui.theme.TextSecondary
import com.opentunes.app.ui.theme.White

@Composable
fun FullScreenPlayer(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track by MusicPlayerManager.currentTrack.collectAsState()
    val isPlaying by MusicPlayerManager.isPlaying.collectAsState()
    val position by MusicPlayerManager.currentPosition.collectAsState()
    val duration by MusicPlayerManager.duration.collectAsState()
    val isShuffle by MusicPlayerManager.isShuffle.collectAsState()
    val repeatMode by MusicPlayerManager.repeatMode.collectAsState()

    BackHandler {
        onCollapse()
    }

    if (track == null) {
        onCollapse()
        return
    }

    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = PureBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "PLAYING FROM LIBRARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = track?.album ?: "OpenTunes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.size(44.dp))
            }

            // Center Album Artwork with smooth track transitions
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                label = "FullScreenArtworkTransition"
            ) { currentTrack ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, Glassmorphism.specularBorder(0.4f, 0.1f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentTrack?.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentTrack?.coverUrl,
                            contentDescription = "Album Art",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "No Artwork",
                            tint = TextSecondary,
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            }

            // Bottom Section: Track Info, Scrubber, Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title and Artist with AnimatedContent
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "FullScreenTitleTransition"
                ) { currentTrack ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = currentTrack?.title ?: "",
                            color = White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentTrack?.artistStr ?: "",
                            color = TextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Interactive Scrubber Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (isDragging) dragProgress else progress,
                        onValueChange = {
                            isDragging = true
                            dragProgress = it
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            if (duration > 0) {
                                MusicPlayerManager.seekTo((dragProgress * duration).toLong())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentBlue,
                            activeTrackColor = AccentBlue,
                            inactiveTrackColor = Color(0xFF38383A)
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentLabel = if (isDragging) (dragProgress * duration).toLong() else position
                        Text(
                            text = formatTime(currentLabel),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Music Controls Bar: Randomize, Previous, Play/Pause, Next, Repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Randomize / Shuffle Button
                    IconButton(
                        onClick = { MusicPlayerManager.toggleShuffle() },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isShuffle) AccentBlue.copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Randomize",
                            tint = if (isShuffle) AccentBlue else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous Track Button
                    IconButton(
                        onClick = { MusicPlayerManager.skipPrevious() },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Song",
                            tint = White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause Primary Button
                    Surface(
                        onClick = { MusicPlayerManager.togglePlayPause() },
                        shape = CircleShape,
                        color = White,
                        modifier = Modifier.size(68.dp),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = PureBlack,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // Next Track Button
                    IconButton(
                        onClick = { MusicPlayerManager.skipNext() },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Song",
                            tint = White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Repeat Button
                    val isRepeatActive = repeatMode != RepeatMode.OFF
                    IconButton(
                        onClick = { MusicPlayerManager.toggleRepeat() },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isRepeatActive) AccentBlue.copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (isRepeatActive) AccentBlue else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
