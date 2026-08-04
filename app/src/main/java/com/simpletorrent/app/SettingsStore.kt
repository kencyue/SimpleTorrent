package com.simpletorrent.app

import android.content.Context
import android.os.Environment
import java.io.File

/** 簡單包一層 SharedPreferences,存全域速度限制與下載目錄 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("simple_torrent_prefs", Context.MODE_PRIVATE)

    // [新增] 儲存自動暫停的設定，預設為 false
    var autoPauseOnFinish: Boolean
        get() = prefs.getBoolean("auto_pause_on_finish", false)
        set(value) = prefs.edit().putBoolean("auto_pause_on_finish", value).apply()

    var downloadLimitKBps: Int
        get() = prefs.getInt("download_limit_kbps", 0) // 0 = 不限制
        set(value) = prefs.edit().putInt("download_limit_kbps", value).apply()

    var uploadLimitKBps: Int
        get() = prefs.getInt("upload_limit_kbps", 0)
        set(value) = prefs.edit().putInt("upload_limit_kbps", value).apply()

    fun downloadDir(context: Context): String {
        // 如果使用者有自訂過路徑，就用自訂的
        val stored = prefs.getString("download_dir", null)
        if (stored != null) return stored

        // 預設路徑改為公開的 Downloads/SimpleTorrent
        val publicDownloadsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SimpleTorrent"
        )
        return publicDownloadsDir.absolutePath
    }
}