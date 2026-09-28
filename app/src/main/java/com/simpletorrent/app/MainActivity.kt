package com.simpletorrent.app

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.addCallback
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val uiHandler = Handler(Looper.getMainLooper())
    private var refreshing = false
    private var modalOpen = false

    private val pickTorrentFile = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { importTorrentFile(it) } }

    private val requestNotifPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* 不管使用者是否同意,都繼續啟動服務;只是沒同意就沒有通知 */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.clearCache(true)
        webView.addJavascriptInterface(Bridge(), "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(
                    "(function(){const el=document.getElementById('appVersion')" +
                        "||document.querySelector('.about-version');" +
                        "if(el)el.textContent=" +
                        JSONObject.quote("Simple Torrent V${BuildConfig.VERSION_NAME}") +
                        ";})()",
                    null
                )
            }
        }
        webView.loadUrl("file:///android_asset/www/index.html")

        onBackPressedDispatcher.addCallback(this) {
            if (modalOpen) {
                webView.evaluateJavascript("window.closeAnySheetForBack && window.closeAnySheetForBack()", null)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        TorrentEngine.setErrorListener { message -> notifyJs(message) }

        val store = SettingsStore(this)
        TorrentEngine.autoPauseOnFinish = store.autoPauseOnFinish

        ensureNotificationPermission()
        startEngineService()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshing = true
        uiHandler.removeCallbacks(refreshLoop)
        uiHandler.post(refreshLoop)
    }

    override fun onPause() {
        refreshing = false
        uiHandler.removeCallbacks(refreshLoop)
        super.onPause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(refreshLoop)
        super.onDestroy()
    }

    private val refreshLoop = object : Runnable {
        override fun run() {
            pushTorrents()
            if (refreshing) uiHandler.postDelayed(this, 1000)
        }
    }

    private fun pushTorrents() {
        val json = torrentsJson()
        webView.evaluateJavascript("window.onTorrentsUpdate && window.onTorrentsUpdate($json)", null)
    }

    private fun torrentsJson(): String {
        val arr = JSONArray()
        TorrentEngine.allSnapshots().forEach { t ->
            arr.put(JSONObject().apply {
                put("infoHash", t.infoHash)
                put("name", t.name)
                put("progress", t.progress)
                put("downloadRateBps", t.downloadRateBps)
                put("uploadRateBps", t.uploadRateBps)
                put("totalSizeBytes", t.totalSizeBytes)
                put("state", t.state.name)
                put("numFiles", t.numFiles)
                put("seeds", t.seeds)
                put("peers", t.peers)
            })
        }
        return arr.toString()
    }

    private fun notifyJs(message: String) {
        webView.post {
            webView.evaluateJavascript("window.showToast && window.showToast(${JSONObject.quote(message)})", null)
        }
    }

    private fun startEngineService() {
        val intent = Intent(this, TorrentService::class.java).apply { action = TorrentService.ACTION_START }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent) {
        val uri = intent.data ?: return
        when {
            uri.scheme == "magnet" -> addMagnet(uri.toString())
            else -> importTorrentFile(uri)
        }
    }

    private fun addMagnet(uri: String) {
        val dir = File(SettingsStore(this).downloadDir(this))
        TorrentEngine.addMagnet(uri, dir)
        notifyJs("已加入下載佇列")
        pushTorrents()
    }

    private fun importTorrentFile(uri: Uri) {
        try {
            val tmp = File(cacheDir, "import_${System.currentTimeMillis()}.torrent")
            contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            val dir = File(SettingsStore(this).downloadDir(this))
            TorrentEngine.addTorrentFile(tmp, dir)
            notifyJs("已加入下載佇列")
            pushTorrents()
        } catch (e: Exception) {
            notifyJs("讀取 .torrent 檔失敗: ${e.message}")
        }
    }

    /** 給 WebView JS 呼叫的橋接介面 */
    inner class Bridge {

        @JavascriptInterface
        fun getTorrents(): String = torrentsJson()

        @JavascriptInterface
        fun getAppVersion(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun addMagnet(uri: String) {
            uiHandler.post { this@MainActivity.addMagnet(uri) }
        }

        @JavascriptInterface
        fun pickTorrentFile() {
            uiHandler.post { pickTorrentFile.launch("application/x-bittorrent") }
        }

        @JavascriptInterface
        fun pauseResume(infoHash: String, resume: Boolean) {
            uiHandler.post {
                if (resume) TorrentEngine.resume(infoHash) else TorrentEngine.pause(infoHash)
                pushTorrents()
            }
        }

        @JavascriptInterface
        fun removeTorrent(infoHash: String, deleteFiles: Boolean) {
            uiHandler.post {
                TorrentEngine.remove(infoHash, deleteFiles)
                pushTorrents()
            }
        }

        @JavascriptInterface
        fun getFileList(infoHash: String): String {
            val arr = JSONArray()
            TorrentEngine.fileList(infoHash).forEachIndexed { index, pair ->
                arr.put(JSONObject().apply {
                    put("index", index)
                    put("name", pair.first)
                    put("size", pair.second)
                })
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun setFileSelection(infoHash: String, selectedIndicesJson: String) {
            uiHandler.post {
                val json = JSONArray(selectedIndicesJson)
                val set = mutableSetOf<Int>()
                for (i in 0 until json.length()) set.add(json.getInt(i))
                TorrentEngine.setFileSelection(infoHash, set)
            }
        }

        @JavascriptInterface
        fun openDownloadFolder(infoHash: String) {
            uiHandler.post {
                val path = TorrentEngine.snapshot(infoHash)?.savePath
                    ?.takeIf { it.isNotBlank() }
                    ?: SettingsStore(this@MainActivity).downloadDir(this@MainActivity)
                openFolder(path)
            }
        }

        @JavascriptInterface
        fun getSettings(): String {
            val store = SettingsStore(this@MainActivity)
            return JSONObject().apply {
                put("downloadLimitKBps", store.downloadLimitKBps)
                put("uploadLimitKBps", store.uploadLimitKBps)
                put("autoPauseOnFinish", store.autoPauseOnFinish)
            }.toString()
        }

        @JavascriptInterface
        fun saveSettings(downloadKBps: Int, uploadKBps: Int, autoPause: Boolean) {
            uiHandler.post {
                val store = SettingsStore(this@MainActivity)
                store.downloadLimitKBps = downloadKBps
                store.uploadLimitKBps = uploadKBps
                store.autoPauseOnFinish = autoPause
                TorrentEngine.applyLimits(downloadKBps, uploadKBps)
                TorrentEngine.autoPauseOnFinish = autoPause
            }
        }

        @JavascriptInterface
        fun pasteFromClipboard(): String {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) return ""
            val item = clipboard.primaryClip?.getItemAt(0)
            return item?.text?.toString() ?: ""
        }

        @JavascriptInterface
        fun setModalOpen(open: Boolean) {
            uiHandler.post { modalOpen = open }
        }

        @JavascriptInterface
        fun setStatusBarTheme(dark: Boolean) {
            uiHandler.post {
                val color = if (dark) 0xFF0F1115.toInt() else 0xFFF3F4F8.toInt()
                window.statusBarColor = color
                window.navigationBarColor = color
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            uiHandler.post {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    notifyJs("找不到可以開啟的應用程式")
                }
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            notifyJs(msg)
        }
    }

    private fun openFolder(path: String) {
        val dir = File(path).apply { mkdirs() }
        val primaryRoot = Environment.getExternalStorageDirectory()
        val relative = try {
            dir.relativeTo(primaryRoot).invariantSeparatorsPath
        } catch (_: IllegalArgumentException) {
            null
        }

        if (relative != null) {
            val documentUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$relative"
            )

            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                )
                return
            } catch (_: Exception) {
                try {
                    startActivity(
                        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                    )
                    return
                } catch (_: Exception) {
                }
            }
        }

        notifyJs("無法開啟下載資料夾：${dir.absolutePath}")
    }
}
