package com.opentunes.app.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.opentunes.app.service.MusicPlayerManager
import com.opentunes.app.ui.theme.AccentBlue
import com.opentunes.app.ui.theme.DarkCard
import com.opentunes.app.ui.theme.Glassmorphism
import com.opentunes.app.ui.theme.TextSecondary
import com.opentunes.app.ui.theme.White

@Composable
fun MiniPlayerBar(modifier: Modifier = Modifier) {
    val track by MusicPlayerManager.currentTrack.collectAsState()
    val isPlaying by MusicPlayerManager.isPlaying.collectAsState()
    val position by MusicPlayerManager.currentPosition.collectAsState()
    val duration by MusicPlayerManager.duration.collectAsState()

    if (track == null) return

    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xE61E222D),
                        Color(0xF212141A)
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .border(
                width = 1.dp,
                brush = Glassmorphism.specularBorder(0.38f, 0.12f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { MusicPlayerManager.setFullScreenPlayerVisible(true) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                    },
                    modifier = Modifier.weight(1f),
                    label = "MiniPlayerTrackTransition"
                ) { currentTrack ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = currentTrack?.coverUrl,
                            contentDescription = "Cover Art",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                .background(Color.DarkGray),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack?.title ?: "",
                                color = White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack?.artistStr ?: "",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { MusicPlayerManager.skipPrevious() }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = { MusicPlayerManager.togglePlayPause() }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = { MusicPlayerManager.skipNext() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Interactive Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(position),
                    color = TextSecondary,
                    fontSize = 10.sp
                )

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
                        .weight(1f)
                        .height(18.dp)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = Color(0xFF38383A)
                    )
                )

                Text(
                    text = formatTime(duration),
                    color = TextSecondary,
                    fontSize = 10.sp
                )
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
