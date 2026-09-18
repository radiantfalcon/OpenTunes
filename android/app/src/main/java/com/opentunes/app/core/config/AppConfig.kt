package com.opentunes.app.core.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.opentunes.app.core.models.DownloadOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object AppConfig {
    private const val PREFS_NAME = "opentunes_preferences"
    private lateinit var prefs: SharedPreferences

    private val _options = MutableStateFlow(DownloadOptions())
    val options = _options.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadConfig()
    }

    private fun getDefaultMusicDir(): String {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val openTunesDir = File(musicDir, "OpenTunes")
        if (!openTunesDir.exists()) {
            openTunesDir.mkdirs()
        }
        return openTunesDir.absolutePath
    }

    private fun loadConfig() {
        val defaultDir = getDefaultMusicDir()
        val loaded = DownloadOptions(
            format = prefs.getString("format", "mp3") ?: "mp3",
            bitrate = prefs.getString("bitrate", "320k") ?: "320k",
            outputDir = prefs.getString("output_dir", defaultDir) ?: defaultDir,
            sequentialNaming = prefs.getBoolean("sequential_naming", true),
            fetchLyrics = prefs.getBoolean("fetch_lyrics", true),
            saveLrc = prefs.getBoolean("save_lrc", false),
            embedCover = prefs.getBoolean("embed_cover", true),
            overwrite = prefs.getBoolean("overwrite", false),
            concurrentDownloads = prefs.getInt("concurrent_downloads", 3),
            dynamicColors = prefs.getBoolean("dynamic_colors", true),
            amoledBlack = prefs.getBoolean("amoled_black", true)
        )
        _options.value = loaded
    }

    fun update(modify: (DownloadOptions) -> Unit) {
        val current = _options.value.copy()
        modify(current)
        _options.value = current
        save(current)
    }

    private fun save(opts: DownloadOptions) {
        prefs.edit()
            .putString("format", opts.format)
            .putString("bitrate", opts.bitrate)
            .putString("output_dir", opts.outputDir)
            .putBoolean("sequential_naming", opts.sequentialNaming)
            .putBoolean("fetch_lyrics", opts.fetchLyrics)
            .putBoolean("save_lrc", opts.saveLrc)
            .putBoolean("embed_cover", opts.embedCover)
            .putBoolean("overwrite", opts.overwrite)
            .putInt("concurrent_downloads", opts.concurrentDownloads)
            .putBoolean("dynamic_colors", opts.dynamicColors)
            .putBoolean("amoled_black", opts.amoledBlack)
            .apply()
    }
}
