package com.simpletorrent.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Foreground service used only while a user-initiated torrent transfer is active.
 * This maps to the Play Console dataSync -> network upload/download use case.
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
                pauseActiveTransfers()
                stopServiceNow()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification("下載引擎啟動中…"))
                ticking = true
                handler.removeCallbacks(tick)
                handler.post(tick)
            }
        }
        // Do not recreate a transfer service unless the user explicitly starts/resumes work.
        return START_NOT_STICKY
    }

    /** Android 15+ enforces a time budget for dataSync foreground services. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            fgsType == ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        ) {
            pauseActiveTransfers()
            stopServiceNow()
        }
    }

    override fun onDestroy() {
        ticking = false
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pauseActiveTransfers() {
        TorrentEngine.allSnapshots()
            .filter {
                it.state == TorrentItem.State.DOWNLOADING ||
                    it.state == TorrentItem.State.METADATA ||
                    it.state == TorrentItem.State.CHECKING ||
                    it.state == TorrentItem.State.SEEDING
            }
            .forEach { TorrentEngine.pause(it.infoHash) }
    }

    private fun stopServiceNow() {
        ticking = false
        handler.removeCallbacks(tick)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val items = TorrentEngine.allSnapshots()
        val activeItems = items.filter {
            it.state == TorrentItem.State.DOWNLOADING ||
                it.state == TorrentItem.State.METADATA ||
                it.state == TorrentItem.State.CHECKING ||
                it.state == TorrentItem.State.SEEDING
        }

        if (activeItems.isEmpty()) {
            stopServiceNow()
            return
        }

        val activeCount = activeItems.count { it.state == TorrentItem.State.DOWNLOADING }
        val totalDown = items.sumOf { it.downloadRateBps }
        val text = if (activeCount > 0) {
            "下載中 $activeCount 個任務・${formatSpeed(totalDown)}"
        } else {
            "處理中（取得資訊／檢查檔案／做種）"
        }

        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(this, 0, openIntent, piFlags)

        val stopIntent = Intent(this, TorrentService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Simple Torrent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "暫停傳輸", stopPi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Torrent 傳輸進度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "顯示使用者啟動的 Torrent 下載與上傳狀態"
            }
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
