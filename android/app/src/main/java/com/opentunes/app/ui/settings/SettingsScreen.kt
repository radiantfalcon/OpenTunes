package com.opentunes.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentunes.app.core.config.AppConfig
import com.opentunes.app.ui.theme.AccentBlue
import com.opentunes.app.ui.theme.AccentBlueOnPill
import com.opentunes.app.ui.theme.DarkCard
import com.opentunes.app.ui.theme.DarkCardBorder
import com.opentunes.app.ui.theme.Glassmorphism
import com.opentunes.app.ui.theme.PureBlack
import com.opentunes.app.ui.theme.TextSecondary
import com.opentunes.app.ui.theme.UnselectedPillBorder
import com.opentunes.app.ui.theme.White

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val options by AppConfig.options.collectAsState()

    val formats = listOf("MP3", "FLAC", "OPUS", "WAV", "M4A")
    val bitrates = listOf("320k", "256k", "192k", "128k")
    val concurrencyOptions = listOf(1, 2, 3, 4, 5)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp)
    ) {
        // Header
        item {
            Text(
                text = "Settings",
                color = White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Section: AUDIO FORMAT
        item {
            Text(
                text = "AUDIO FORMAT",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                formats.forEach { fmt ->
                    val isSelected = options.format.equals(fmt, ignoreCase = true)
                    PillButton(
                        text = fmt,
                        isSelected = isSelected,
                        onClick = { AppConfig.update { it.format = fmt.lowercase() } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: BITRATE (Only visible when format is MP3)
        item {
            AnimatedVisibility(
                visible = options.format.equals("mp3", ignoreCase = true),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Text(
                        text = "BITRATE",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        bitrates.forEach { br ->
                            val isSelected = options.bitrate.equals(br, ignoreCase = true)
                            PillButton(
                                text = br,
                                isSelected = isSelected,
                                onClick = { AppConfig.update { it.bitrate = br } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Section: CONCURRENT DOWNLOADS (1-5 SONGS)
        item {
            Text(
                text = "CONCURRENT DOWNLOADS (1-5 SONGS)",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                concurrencyOptions.forEach { num ->
                    val isSelected = options.concurrentDownloads == num
                    PillButton(
                        text = num.toString(),
                        isSelected = isSelected,
                        onClick = { AppConfig.update { it.concurrentDownloads = num } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section: OUTPUT DIRECTORY
        item {
            Text(
                text = "OUTPUT DIRECTORY",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, UnselectedPillBorder, RoundedCornerShape(16.dp))
                    .background(PureBlack)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = options.outputDir,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Section: OPTIONS Card
        item {
            Text(
                text = "OPTIONS",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SettingSwitchRow(
                        title = "Embed Artwork",
                        subtitle = "Write high-resolution cover art into audio tags",
                        checked = options.embedCover,
                        onCheckedChange = { chk -> AppConfig.update { it.embedCover = chk } }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SettingSwitchRow(
                        title = "Fetch Lyrics",
                        subtitle = "Download plain and synced lyrics via LRCLIB",
                        checked = options.fetchLyrics,
                        onCheckedChange = { chk -> AppConfig.update { it.fetchLyrics = chk } }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SettingSwitchRow(
                        title = "Save Synced .LRC",
                        subtitle = "Save synced lyrics file alongside song",
                        checked = options.saveLrc,
                        onCheckedChange = { chk -> AppConfig.update { it.saveLrc = chk } }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SettingSwitchRow(
                        title = "Overwrite Existing",
                        subtitle = "Redownload existing tracks if found",
                        checked = options.overwrite,
                        onCheckedChange = { chk -> AppConfig.update { it.overwrite = chk } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Section: APPEARANCE Card
        item {
            Text(
                text = "APPEARANCE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SettingSwitchRow(
                        title = "Material You Dynamic Colors",
                        subtitle = "Tint interface based on wallpaper colors (Android 12+)",
                        checked = options.dynamicColors,
                        onCheckedChange = { chk -> AppConfig.update { it.dynamicColors = chk } }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SettingSwitchRow(
                        title = "AMOLED Pure Black",
                        subtitle = "Pitch black surfaces for OLED battery savings",
                        checked = options.amoledBlack,
                        onCheckedChange = { chk -> AppConfig.update { it.amoledBlack = chk } }
                    )
                }
            }
        }
    }
}

@Composable
fun PillButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                brush = if (isSelected) Glassmorphism.glowBorder(AccentBlue, 0.9f) else Glassmorphism.specularBorder(0.25f, 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .background(if (isSelected) AccentBlue else Color(0x14FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) AccentBlueOnPill else White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentBlueOnPill,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = Color(0xFFA0A0A5),
                uncheckedTrackColor = Color(0xFF38383C),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
