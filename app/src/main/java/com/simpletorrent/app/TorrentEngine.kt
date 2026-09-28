package com.simpletorrent.app

import android.content.Context
import android.net.Uri
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
import java.util.concurrent.ConcurrentHashMap

object TorrentEngine {

    private const val TAG = "TorrentEngine"
    private const val METADATA_TIMEOUT_SECONDS = 120
    private var session: SessionManager? = null
    private var listener: ((String) -> Unit)? = null
    private var onEngineError: ((String) -> Unit)? = null
    private val pendingMagnets = ConcurrentHashMap<String, PendingMagnet>()

    private data class PendingMagnet(
        val infoHash: String,
        val name: String,
        val savePath: String,
        @Volatile var finalDownloadStarted: Boolean = false
    )

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
        pendingMagnets.clear()
    }

    fun setUpdateListener(cb: ((String) -> Unit)?) {
        listener = cb
    }

    fun addMagnet(uri: String, saveDir: File) {
        saveDir.mkdirs()
        val manager = session
        if (manager == null || !manager.isRunning) {
            onEngineError?.invoke("Torrent 引擎尚未啟動，請稍後再試")
            return
        }

        val infoHash = magnetInfoHash(uri)
        if (infoHash == null) {
            onEngineError?.invoke("Magnet 格式錯誤，找不到有效的 btih")
            return
        }

        if (pendingMagnets.containsKey(infoHash) || getHandle(infoHash) != null) {
            onEngineError?.invoke("這個 Torrent 已經在下載清單中")
            return
        }

        val displayName = try {
            Uri.parse(uri).getQueryParameter("dn")?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        } ?: "取得資訊中..."

        val pending = PendingMagnet(
            infoHash = infoHash,
            name = displayName,
            savePath = saveDir.absolutePath
        )
        pendingMagnets[infoHash] = pending

        Thread {
            try {
                // Restore the original v2.0.0 metadata acquisition path. fetchMagnet()
                // temporarily adds a torrent with metadata-focused flags and waits for
                // metadata. A synthetic pending row keeps the UI stable while that
                // temporary handle is created and removed internally by jlibtorrent.
                val data = manager.fetchMagnet(uri, METADATA_TIMEOUT_SECONDS, saveDir)
                if (pendingMagnets[infoHash] !== pending) return@Thread

                if (data == null || data.isEmpty()) {
                    throw IllegalStateException("取得 metadata 逾時")
                }

                val ti = TorrentInfo.bdecode(data)
                val actualHash = ti.infoHashV1().toString().lowercase()
                if (actualHash != infoHash) {
                    throw IllegalStateException("Magnet info hash 驗證失敗")
                }

                pending.finalDownloadStarted = true
                manager.download(ti, saveDir)
            } catch (t: Throwable) {
                val wasPending = pendingMagnets.remove(infoHash, pending)
                if (!wasPending) return@Thread

                Log.e(TAG, "新增磁力連結失敗: $uri", t)
                val message = if (t.message?.contains("metadata", ignoreCase = true) == true) {
                    "取得 metadata 超過 120 秒，已停止並移除任務，請確認 Magnet 是否有效後重試"
                } else {
                    "新增磁力連結失敗:${t.message ?: t.javaClass.simpleName}"
                }
                onEngineError?.invoke(message)
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
            if (th.infoHash().toString().equals(infoHash, ignoreCase = true)) {
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
        val normalized = infoHash.lowercase()
        pendingMagnets.remove(normalized)

        val h = getHandle(normalized)
        if (h != null && h.isValid) {
            if (deleteFiles) {
                session?.remove(h, SessionHandle.DELETE_FILES)
            } else {
                session?.remove(h)
            }
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

    fun snapshot(infoHash: String): TorrentItem? {
        val normalized = infoHash.lowercase()
        pendingMagnets[normalized]?.let { return pendingItem(it) }
        return getHandle(normalized)?.let { toItem(it) }
    }

    fun allSnapshots(): List<TorrentItem> {
        val result = mutableListOf<TorrentItem>()
        val seen = mutableSetOf<String>()
        val s = session?.swig()

        try {
            if (s != null) {
                val vec = s.get_torrents()
                val size = vec.size
                for (i in 0 until size) {
                    val th = TorrentHandle(vec.get(i))
                    if (!th.isValid) continue

                    val hash = th.infoHash().toString().lowercase()
                    val pending = pendingMagnets[hash]

                    // While fetchMagnet() is running, hide libtorrent's temporary handle
                    // and expose a synthetic pending item instead. After metadata is ready
                    // and the final download handle appears, switch to the real item in
                    // this same snapshot so the UI card never disappears between handles.
                    if (pending != null) {
                        if (pending.finalDownloadStarted && th.status().hasMetadata()) {
                            toItem(th)?.let {
                                result.add(it)
                                seen.add(hash)
                            }
                            pendingMagnets.remove(hash, pending)
                        }
                        continue
                    }

                    toItem(th)?.let {
                        result.add(it)
                        seen.add(hash)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "取得所有快照失敗", e)
        }

        pendingMagnets.values.forEach { pending ->
            if (pending.infoHash !in seen) {
                result.add(pendingItem(pending))
                seen.add(pending.infoHash)
            }
        }

        return result
    }

    private fun pendingItem(pending: PendingMagnet) = TorrentItem(
        infoHash = pending.infoHash,
        name = pending.name,
        progress = 0f,
        downloadRateBps = 0L,
        uploadRateBps = 0L,
        totalSizeBytes = 0L,
        state = TorrentItem.State.METADATA,
        savePath = pending.savePath,
        numFiles = 0,
        seeds = 0,
        peers = 0
    )

    private fun magnetInfoHash(uri: String): String? {
        val raw = Regex(
            pattern = "(?i)(?:^|[?&])xt=urn:btih:([A-F0-9]{40}|[A-Z2-7]{32})(?=&|$)"
        ).find(uri)?.groupValues?.getOrNull(1) ?: return null

        return if (raw.length == 40) {
            raw.lowercase()
        } else {
            base32BtihToHex(raw)
        }
    }

    private fun base32BtihToHex(raw: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val output = ByteArray(20)
        var outIndex = 0
        var buffer = 0
        var bits = 0

        for (char in raw.uppercase()) {
            val value = alphabet.indexOf(char)
            if (value < 0) return null

            buffer = (buffer shl 5) or value
            bits += 5

            while (bits >= 8) {
                bits -= 8
                if (outIndex >= output.size) return null
                output[outIndex++] = ((buffer shr bits) and 0xff).toByte()
                buffer = if (bits == 0) 0 else buffer and ((1 shl bits) - 1)
            }
        }

        if (outIndex != output.size) return null
        return output.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun toItem(h: TorrentHandle): TorrentItem? {
        if (!h.isValid) return null

        val st: TorrentStatus = h.status()
        val ti = h.torrentFile()
        val paused = st.flags().and_(TorrentFlags.PAUSED).nonZero()

        val state = when {
            !st.hasMetadata() -> TorrentItem.State.METADATA
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
            totalSizeBytes = ti?.totalSize() ?: st.totalWanted(),
            state = state,
            savePath = h.savePath() ?: "",
            numFiles = ti?.numFiles() ?: 0,
            seeds = st.numSeeds(),
            peers = st.numPeers()
        )
    }
}
