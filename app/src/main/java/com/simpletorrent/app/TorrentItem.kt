package com.simpletorrent.app

/** 單一 torrent 在 UI 上要顯示的資訊快照 */
data class TorrentItem(
    val infoHash: String,
    var name: String,
    var progress: Float = 0f,        // 0.0 - 1.0
    var downloadRateBps: Long = 0,   // bytes/sec
    var uploadRateBps: Long = 0,
    var totalSizeBytes: Long = 0,
    var state: State = State.DOWNLOADING,
    var savePath: String = "",
    var numFiles: Int = 0,
    var seeds: Int = 0,
    var peers: Int = 0
) {
    enum class State { DOWNLOADING, PAUSED, SEEDING, CHECKING, FINISHED, ERROR, METADATA }
}
