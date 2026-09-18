package com.opentunes.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opentunes.app.ui.theme.AccentBlue
import com.opentunes.app.ui.theme.ActiveIndicator
import com.opentunes.app.ui.theme.GlassBorder
import com.opentunes.app.ui.theme.GlassSurfaceDark
import com.opentunes.app.ui.theme.PureBlack
import com.opentunes.app.ui.theme.TextSecondary
import com.opentunes.app.ui.theme.White

enum class AppTab(val label: String) {
    DOWNLOAD("Download"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

@Composable
fun BottomNavBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.drawBehind {
            drawLine(
                color = Color(0x28FFFFFF),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx()
            )
        },
        containerColor = Color(0xF20F1014),
        contentColor = White
    ) {
        NavigationBarItem(
            selected = currentTab == AppTab.DOWNLOAD,
            onClick = { onTabSelected(AppTab.DOWNLOAD) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download"
                )
            },
            label = { Text("Download") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentBlue,
                selectedTextColor = White,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = ActiveIndicator
            )
        )

        NavigationBarItem(
            selected = currentTab == AppTab.LIBRARY,
            onClick = { onTabSelected(AppTab.LIBRARY) },
            icon = {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = "Library"
                )
            },
            label = { Text("Library") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentBlue,
                selectedTextColor = White,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = ActiveIndicator
            )
        )

        NavigationBarItem(
            selected = currentTab == AppTab.SETTINGS,
            onClick = { onTabSelected(AppTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            },
            label = { Text("Settings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentBlue,
                selectedTextColor = White,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = ActiveIndicator
            )
        )
    }
}
