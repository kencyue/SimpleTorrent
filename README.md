# Simple Torrent (Android)

一個沒有廣告、沒有內建追蹤的簡易 BitTorrent 下載 app。

## 功能
- 貼磁力連結 (magnet:) 或開啟 .torrent 檔案來新增下載
- 多工下載(同時處理多個 torrent)
- 每個任務可暫停 / 繼續 / 移除(可選是否連檔案一起刪除)
- 每個任務可挑選要下載哪些檔案(勾選清單)
- 全域上傳/下載速度限制(設定頁,單位 KB/s)
- 前景服務 + 持續通知,避免系統把下載中的 app 砍掉
- 系統會把 magnet 連結、.torrent 檔直接指派給這個 app 開啟

## 技術架構
- Kotlin + AndroidX,單一 Activity + RecyclerView 清單
- BitTorrent 協定本體用 [FrostWire jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent)(對 libtorrent C++ 核心的 JNI 封裝,FrostWire 官方持續維護),沒有重新發明協定實作
- `TorrentEngine` 是唯一入口,包住 `SessionManager`
- `TorrentService` 是前景服務,負責在背景保持連線並更新通知

## 如何建置
1. 用 Android Studio (Jellyfish 以上) 開啟這個資料夾當作專案
2. 讓它自動同步 Gradle(第一次需要網路下載 jlibtorrent 的 native 函式庫,並會連到 FrostWire 官方 Maven repo (dl.frostwire.com),體積較大,含 ARM/ARM64/x86/x86_64 四種 ABI)
3. 接上手機或開模擬器,按 Run

> 這個沙盒環境沒有 Android SDK / 網路也不能連 Google Maven,所以沒辦法在這裡直接幫你編出 apk,原始碼都已經寫好,麻煩你在自己的 Android Studio 建置一次。

## 已知限制 / 之後可以自己加強的地方
- 目前用系統內建圖示當 App icon(`@android:drawable/sym_def_app_icon`),你可以換成自己的 mipmap 圖示
- 下載目錄預設在 app 專屬的外部儲存空間(`Android/data/com.simpletorrent.app/files/downloads`),沒有讓使用者自訂路徑或存到「下載」資料夾,如果要分享到相簿/檔案總管,需要另外處理 Scoped Storage 或 MediaStore
- 沒有做深色主題細節調整、沒有做多語系
- 沒有做開機自動繼續任務(目前重開機後任務清單會消失,因為沒有存 resume data / DB)
- 沒有 DHT/加密協定的細部設定 UI(libtorrent 內部預設值已經夠用)

## 重要提醒
- BitTorrent 只是檔案分享協定本身合法,但下載/分享有版權的內容仍可能違法,請只用來下載你有權取得的檔案(例如合法發行的 Linux ISO、公版素材等)
