package com.simpletorrent.app

import android.content.Context
import android.util.Log
import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionHandle
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.TorrentAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert // [新增] 匯入下載完成的 Alert
import java.io.File

object TorrentEngine {

    private const val TAG = "TorrentEngine"
    private var session: SessionManager? = null
    private var listener: ((String) -> Unit)? = null
    private var onEngineError: ((String) -> Unit)? = null

    // [新增] 控制下載完成後是否自動暫停的開關 (預設為 false)
    var autoPauseOnFinish: Boolean = false

    fun setErrorListener(cb: (String) -> Unit) {
        onEngineError = cb
    }

    val isRunning: Boolean get() = session?.isRunning == true

    fun start(context: Context) {
        if (session != null) return

        val params = SessionParams(
            SettingsPack().apply {
                connectionsLimit(200)
                activeDownloads(8)
                activeSeeds(8)
            }
        )

        session = SessionManager().apply {
            addListener(object : AlertListener {
                override fun types(): IntArray? = null

                override fun alert(alert: Alert<*>) {
                    val ta = alert as? TorrentAlert<*> ?: return
                    val h = ta.handle() ?: return
                    try {
                        val hash = h.infoHash().toString()

                        // [新增] 攔截下載完成事件，如果開關有開，則自動暫停
                        if (alert is TorrentFinishedAlert && autoPauseOnFinish) {
                            pause(hash)
                            Log.i(TAG, "下載完成，觸發自動暫停: $hash")
                        }

                        // 通知 UI 畫面需要更新
                        listener?.invoke(hash)
                    } catch (t: Throwable) {
                        Log.w(TAG, "處理 alert 失敗", t)
                    }
                }
            })
            start(params)
        }
        Log.i(TAG, "libtorrent session 已啟動")
    }

    fun stop() {
        session?.stop()
        session = null
    }

    fun setUpdateListener(cb: ((String) -> Unit)?) {
        listener = cb
    }

    fun addMagnet(uri: String, saveDir: File) {
        saveDir.mkdirs()
        Thread {
            try {
                val data = session?.fetchMagnet(uri, 30, saveDir) ?: return@Thread
                val ti = TorrentInfo.bdecode(data)
                session?.download(ti, saveDir)
            } catch (t: Throwable) {
                Log.e(TAG, "新增磁力連結失敗: $uri", t)
                onEngineError?.invoke("新增磁力連結失敗:${t.message ?: t.javaClass.simpleName}")
            }
        }.start()
    }

    fun addTorrentFile(torrentFile: File, saveDir: File) {
        saveDir.mkdirs()
        Thread {
            try {
                val info = TorrentInfo(torrentFile)
                session?.download(info, saveDir)
            } catch (t: Throwable) {
                Log.e(TAG, "新增 .torrent 檔失敗", t)
                onEngineError?.invoke("新增 .torrent 檔失敗:${t.message ?: t.javaClass.simpleName}")
            }
        }.start()
    }

    private fun getHandle(infoHash: String): TorrentHandle? {
        val s = session?.swig() ?: return null
        val vec = s.get_torrents()
        for (i in 0 until vec.size) {
            val thNative = vec.get(i)
            val th = TorrentHandle(thNative)
            if (th.infoHash().toString() == infoHash) {
                return th
            }
        }
        return null
    }

    fun pause(infoHash: String) {
        val h = getHandle(infoHash) ?: return
        if (!h.isValid) return
        h.unsetFlags(TorrentFlags.AUTO_MANAGED)
        h.pause()
    }

    fun resume(infoHash: String) {
        val h = getHandle(infoHash) ?: return
        if (!h.isValid) return
        h.setFlags(TorrentFlags.AUTO_MANAGED)
        h.resume()
    }

    fun remove(infoHash: String, deleteFiles: Boolean) {
        val h = getHandle(infoHash) ?: return
        if (!h.isValid) return
        if (deleteFiles) {
            session?.remove(h, SessionHandle.DELETE_FILES)
        } else {
            session?.remove(h)
        }
    }

    fun fileList(infoHash: String): List<Pair<String, Long>> {
        val h = getHandle(infoHash) ?: return emptyList()
        if (!h.isValid) return emptyList()
        val ti = h.torrentFile() ?: return emptyList()
        val files = ti.files()
        return (0 until files.numFiles()).map { i ->
            files.filePath(i) to files.fileSize(i)
        }
    }

    fun setFileSelection(infoHash: String, selectedIndices: Set<Int>) {
        val h = getHandle(infoHash) ?: return
        if (!h.isValid) return
        val ti = h.torrentFile() ?: return
        val count = ti.files().numFiles()
        val priorities = Array(count) { i ->
            if (i in selectedIndices) Priority.NORMAL else Priority.IGNORE
        }
        h.prioritizeFiles(priorities)
    }

    fun applyLimits(downloadKBps: Int, uploadKBps: Int) {
        val s = session ?: return
        val pack = SettingsPack()
        pack.downloadRateLimit(if (downloadKBps <= 0) 0 else downloadKBps * 1024)
        pack.uploadRateLimit(if (uploadKBps <= 0) 0 else uploadKBps * 1024)
        s.applySettings(pack)
    }

    fun snapshot(infoHash: String): TorrentItem? = getHandle(infoHash)?.let { toItem(it) }

    fun allSnapshots(): List<TorrentItem> {
        val s = session?.swig() ?: return emptyList()
        val result = mutableListOf<TorrentItem>()
        try {
            val vec = s.get_torrents()
            val size = vec.size
            for (i in 0 until size) {
                val th = TorrentHandle(vec.get(i))
                toItem(th)?.let { result.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "取得所有快照失敗", e)
        }
        return result
    }

    private fun toItem(h: TorrentHandle): TorrentItem? {
        if (!h.isValid) return null

        val st: TorrentStatus = h.status()
        val ti = h.torrentFile()
        val paused = st.flags().and_(TorrentFlags.PAUSED).nonZero()

        val state = when {
            ti == null -> TorrentItem.State.METADATA
            paused -> TorrentItem.State.PAUSED
            st.isFinished && st.isSeeding -> TorrentItem.State.SEEDING
            st.isFinished -> TorrentItem.State.FINISHED
            st.state() == TorrentStatus.State.CHECKING_FILES ||
                    st.state() == TorrentStatus.State.CHECKING_RESUME_DATA -> TorrentItem.State.CHECKING
            else -> TorrentItem.State.DOWNLOADING
        }

        // [修正] 優先從 Metadata (ti) 拿真實檔名，拿不到再從狀態 (st) 拿
        val realName = ti?.name().takeIf { !it.isNullOrBlank() }
            ?: st.name().takeIf { !it.isNullOrBlank() }
            ?: "取得資訊中..."

        return TorrentItem(
            infoHash = h.infoHash().toString(),
            name = realName, // 使用我們剛剛判斷好的變數
            progress = st.progress(),
            downloadRateBps = st.downloadPayloadRate().toLong(),
            uploadRateBps = st.uploadPayloadRate().toLong(),
            totalSizeBytes = ti?.totalSize() ?: 0L,
            state = state,
            savePath = h.savePath() ?: "",
            numFiles = ti?.numFiles() ?: 0,
            seeds = st.numSeeds(),
            peers = st.numPeers()
        )
    }
}
