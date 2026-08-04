package com.simpletorrent.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * 前景服務:只要有任務在下載,就會顯示一個常駐通知,
 * 避免系統把 app 砍掉導致下載中斷。
 */
class TorrentService : Service() {

    companion object {
        const val CHANNEL_ID = "torrent_downloads"
        const val NOTIF_ID = 1
        const val ACTION_START = "com.simpletorrent.app.action.START"
        const val ACTION_STOP = "com.simpletorrent.app.action.STOP"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            updateNotification()
            if (ticking) handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        TorrentEngine.start(applicationContext)
        val prefs = SettingsStore(applicationContext)
        TorrentEngine.applyLimits(prefs.downloadLimitKBps, prefs.uploadLimitKBps)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // [新增] 使用者按下通知的「關閉」按鈕，立即結束服務
                ticking = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification("下載引擎啟動中..."))
                ticking = true
                handler.removeCallbacks(tick) // 避免重複執行
                handler.post(tick)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ticking = false
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        val items = TorrentEngine.allSnapshots()

        // [新增] 判斷哪些狀態算「活躍中」(需要背景持續執行)
        val activeItems = items.filter {
            it.state == TorrentItem.State.DOWNLOADING ||
                    it.state == TorrentItem.State.METADATA ||
                    it.state == TorrentItem.State.CHECKING ||
                    it.state == TorrentItem.State.SEEDING
        }

        // [新增] 如果完全沒有活躍任務，自動關閉背景服務與通知！
        if (activeItems.isEmpty()) {
            ticking = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // 若有活躍任務，繼續更新通知上的數字與速度
        val activeCount = activeItems.count { it.state == TorrentItem.State.DOWNLOADING }
        val totalDown = items.sumOf { it.downloadRateBps }

        val text = if (activeCount > 0) {
            "下載中 $activeCount 個任務・${formatSpeed(totalDown)}"
        } else {
            "處理中 (取得資訊/檢查檔案/做種)"
        }

        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)

        // 確保相容各版本的 PendingIntent Flags
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pi = PendingIntent.getActivity(this, 0, openIntent, piFlags)

        // [新增] 建立一個給「關閉按鈕」專用的 Intent
        val stopIntent = Intent(this, TorrentService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Simple Torrent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            // [新增] 在推播通知下方加入「關閉背景執行」按鈕
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "關閉背景執行", stopPi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "下載進度", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "$bytesPerSec B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return "%.1f KB/s".format(kb)
    return "%.1f MB/s".format(kb / 1024.0)
}