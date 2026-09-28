# SimpleTorrent

[繁體中文](#繁體中文) · [English](#english) · [Releases](https://github.com/kencyue/SimpleTorrent/releases)

SimpleTorrent is a lightweight, privacy-conscious BitTorrent client for Android. It focuses on the essentials: opening magnet links and `.torrent` files, managing multiple transfers, selecting files, and controlling bandwidth — without ads or built-in analytics.

> **Legal notice:** BitTorrent is a general-purpose peer-to-peer file-transfer protocol. SimpleTorrent does not provide, index, host, or promote copyrighted content. Users are responsible for ensuring that they have the right to download and share any content they transfer.

---

## 繁體中文

### 📱 專案簡介

**SimpleTorrent** 是一款簡潔、輕量、重視隱私的 Android BitTorrent 用戶端。專案以實用與透明為核心，不內建廣告、不加入使用者追蹤或分析 SDK，讓使用者自行加入合法取得的 Magnet Link 或 `.torrent` 檔案進行傳輸。

### ✨ 主要功能

- 支援 `magnet:` 磁力連結
- 支援直接開啟 `.torrent` 檔案
- 支援多個 Torrent 任務同時傳輸
- 任務暫停、繼續與移除
- 移除任務時可選擇是否一併刪除已下載檔案
- Torrent 內個別檔案選擇
- 全域下載與上傳速度限制
- Android Foreground Service 背景傳輸
- 持續通知顯示傳輸狀態
- 可由 Android Intent 接收 Magnet Link 與 `.torrent` 檔案
- 使用 Android App-specific Scoped Storage
- 無廣告
- 無內建 Analytics / Tracking SDK

### 🔐 隱私與資料

SimpleTorrent 的設計不需要帳號，也不需要將 Torrent 使用紀錄傳送至專案開發者的伺服器。Torrent/P2P 傳輸本身會依 BitTorrent 協定與其他 peers、trackers 或 DHT 節點進行網路通訊，因此使用者的 IP 位址可能會對參與同一 Torrent swarm 的其他節點可見。

完整政策請參閱 [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md)。

### 🧱 技術架構

- **Language:** Kotlin
- **Platform:** Android / AndroidX
- **UI:** Single Activity + RecyclerView
- **BitTorrent engine:** [FrostWire jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent), based on libtorrent
- **Target SDK:** Android 16 / API 36
- **Background transfer:** Android Foreground Service (`dataSync`)
- **Storage:** App-specific scoped external storage
- **CI/CD:** GitHub Actions
- **Distribution artifact:** Android App Bundle (`.aab`)

`TorrentEngine` 封裝 jlibtorrent `SessionManager`，作為 Torrent 操作的主要入口；`TorrentService` 則負責背景傳輸生命週期與持續通知。

### 📦 下載

正式版本與建置產物請由 GitHub Releases 取得：

**https://github.com/kencyue/SimpleTorrent/releases**

Google Play 版本完成審核後，可再由 Play 商店取得正式發布版本。

### 🛠️ 開發與建置

需求：

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0
- 可存取 Google / Maven 與 FrostWire dependencies 的網路環境

Clone 專案後執行：

```bash
./gradlew clean bundleRelease
```

Release Android App Bundle 會產生於：

```text
app/build/outputs/bundle/release/
```

也可以直接使用 Android Studio 開啟專案、完成 Gradle Sync 後建置。

### 🤖 GitHub Actions / Release

每次推送或 Pull Request 都會透過 GitHub Actions 驗證 release bundle 建置。建立 `v*` tag 時，workflow 會建立 GitHub Release 並附加產生的 `.aab`。

例如：

```bash
git tag v2.1.0
git push origin v2.1.0
```

### ⚠️ 使用責任

SimpleTorrent 僅提供通用 BitTorrent 傳輸功能。本專案不提供 Torrent 搜尋、不提供內容索引，也不附帶任何受著作權保護的影音、軟體或其他內容。

請只下載或分享你擁有、獲得授權、屬於公有領域，或法律允許傳輸的內容。使用者應自行遵守所在地區適用的著作權及相關法律。

---

## English

### 📱 About

**SimpleTorrent** is a lightweight and privacy-conscious BitTorrent client for Android. It is intentionally focused on core torrent functionality and does not include advertising, analytics, or built-in content discovery.

Users add their own legally obtained magnet links or `.torrent` files and remain responsible for the content they transfer.

### ✨ Features

- Open `magnet:` links
- Open `.torrent` files
- Run multiple torrent transfers
- Pause, resume, and remove torrent tasks
- Optionally remove downloaded files with a task
- Select individual files within a torrent
- Configure global download and upload speed limits
- Continue active transfers through an Android Foreground Service
- Display transfer state through a persistent notification
- Handle magnet links and torrent files through Android intents
- Use Android app-specific scoped storage
- No advertisements
- No built-in analytics or tracking SDK

### 🔐 Privacy

SimpleTorrent does not require an account and is not designed to send torrent history to a developer-operated backend. BitTorrent is a peer-to-peer protocol, however, so normal torrent operation communicates with peers, trackers, and/or DHT nodes. Your IP address may therefore be visible to other participants in the same torrent swarm.

See [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) for the full privacy policy.

### 🧱 Technical Overview

- **Language:** Kotlin
- **Platform:** Android / AndroidX
- **UI:** Single Activity + RecyclerView
- **BitTorrent engine:** [FrostWire jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent), backed by libtorrent
- **Target SDK:** Android 16 / API 36
- **Background transfer:** Android Foreground Service (`dataSync`)
- **Storage:** App-specific scoped external storage
- **CI/CD:** GitHub Actions
- **Release format:** Android App Bundle (`.aab`)

`TorrentEngine` provides the application's main abstraction around jlibtorrent's `SessionManager`. `TorrentService` manages the background transfer lifecycle and persistent Android notification.

### 📦 Downloads

Official project releases are published on GitHub Releases:

**https://github.com/kencyue/SimpleTorrent/releases**

A Google Play distribution can be provided separately after Play review and publication.

### 🛠️ Building from Source

Requirements:

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Network access to the required Google/Maven and FrostWire dependencies

Clone the repository and run:

```bash
./gradlew clean bundleRelease
```

The release Android App Bundle is generated under:

```text
app/build/outputs/bundle/release/
```

The project can also be opened directly in Android Studio and built after Gradle synchronization completes.

### 🤖 CI/CD and Releases

GitHub Actions validates the release bundle on pushes and pull requests. Pushing a `v*` tag triggers the release workflow, which creates a GitHub Release and attaches the generated `.aab` artifact.

Example:

```bash
git tag v2.1.0
git push origin v2.1.0
```

### ⚠️ Responsible Use

SimpleTorrent is a general-purpose BitTorrent transfer client. The project does not provide torrent search, content indexing, or bundled copyrighted media or software.

Only download or share content that you own, are authorized to distribute, is in the public domain, or that you are otherwise legally permitted to transfer. Users are responsible for complying with applicable copyright and other laws in their jurisdiction.

---

## License

No open-source license is currently declared by this repository. Unless a license is added, copyright remains with the project owner and no additional reuse rights should be assumed.

## Project Links

- **Repository:** https://github.com/kencyue/SimpleTorrent
- **Releases:** https://github.com/kencyue/SimpleTorrent/releases
- **Issues:** https://github.com/kencyue/SimpleTorrent/issues
