package com.opentunes.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.opentunes.app.core.config.AppConfig
import com.opentunes.app.service.MusicPlayerManager
import com.opentunes.app.ui.download.DownloadScreen
import com.opentunes.app.ui.library.LibraryScreen
import com.opentunes.app.ui.navigation.AppTab
import com.opentunes.app.ui.navigation.BottomNavBar
import com.opentunes.app.ui.player.MiniPlayerBar
import com.opentunes.app.ui.settings.SettingsScreen
import com.opentunes.app.ui.splash.SplashScreen
import com.opentunes.app.ui.theme.OpenTunesTheme
import com.opentunes.app.ui.theme.PureBlack

@Composable
fun MainScreen() {
    val options by AppConfig.options.collectAsState()
    var showSplash by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(AppTab.DOWNLOAD) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    OpenTunesTheme(
        dynamicColor = options.dynamicColors,
        amoledBlack = options.amoledBlack
    ) {
        if (showSplash) {
            SplashScreen(onSplashFinished = { showSplash = false })
        } else {
            val isFullScreenPlayerVisible by MusicPlayerManager.isFullScreenPlayerVisible.collectAsState()
            val currentTrack by MusicPlayerManager.currentTrack.collectAsState()

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    containerColor = PureBlack,
                    bottomBar = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            MiniPlayerBar()
                            BottomNavBar(
                                currentTab = currentTab,
                                onTabSelected = { currentTab = it }
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PureBlack)
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                val forward = targetState.ordinal > initialState.ordinal
                                if (forward) {
                                    (slideInHorizontally { width -> width / 4 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { width -> -width / 4 } + fadeOut())
                                } else {
                                    (slideInHorizontally { width -> -width / 4 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { width -> width / 4 } + fadeOut())
                                }
                            },
                            label = "TabAnimatedContent"
                        ) { tab ->
                            when (tab) {
                                AppTab.DOWNLOAD -> DownloadScreen()
                                AppTab.LIBRARY -> LibraryScreen()
                                AppTab.SETTINGS -> SettingsScreen()
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isFullScreenPlayerVisible && currentTrack != null,
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
                ) {
                    com.opentunes.app.ui.player.FullScreenPlayer(
                        onCollapse = { MusicPlayerManager.setFullScreenPlayerVisible(false) }
                    )
                }
            }
        }
    }
}
