package com.simpletorrent.app

import android.content.Context
import android.os.Environment
import java.io.File

/** Stores local app settings. No broad/shared-storage permission is required. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("simple_torrent_prefs", Context.MODE_PRIVATE)

    var autoPauseOnFinish: Boolean
        get() = prefs.getBoolean("auto_pause_on_finish", false)
        set(value) = prefs.edit().putBoolean("auto_pause_on_finish", value).apply()

    var downloadLimitKBps: Int
        get() = prefs.getInt("download_limit_kbps", 0)
        set(value) = prefs.edit().putInt("download_limit_kbps", value).apply()

    var uploadLimitKBps: Int
        get() = prefs.getInt("upload_limit_kbps", 0)
        set(value) = prefs.edit().putInt("upload_limit_kbps", value).apply()

    fun downloadDir(context: Context): String {
        // Use app-specific external storage so Android 10+ scoped-storage rules are respected
        // without MANAGE_EXTERNAL_STORAGE or legacy storage permissions.
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val base = external ?: File(context.filesDir, "downloads")
        return File(base, "SimpleTorrent").apply { mkdirs() }.absolutePath
    }
}
