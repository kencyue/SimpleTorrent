/* ---------- 小工具 ---------- */
function formatSize(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let v = bytes, i = 0;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return (i === 0 ? v : v.toFixed(1)) + ' ' + units[i];
}
function formatSpeed(bps) {
  if (bps < 1024) return bps + ' B/s';
  const kb = bps / 1024;
  if (kb < 1024) return kb.toFixed(1) + ' KB/s';
  return (kb / 1024).toFixed(1) + ' MB/s';
}
const STATE_LABEL = {
  METADATA: '取得 metadata 中', CHECKING: '檢查檔案中', PAUSED: '已暫停',
  FINISHED: '已完成', SEEDING: '分享中', DOWNLOADING: '下載中', ERROR: '發生錯誤'
};
const STATE_BADGE_CLASS = {
  METADATA: 'badge-metadata', CHECKING: 'badge-checking', PAUSED: 'badge-paused',
  FINISHED: 'badge-finished', SEEDING: 'badge-seeding', DOWNLOADING: 'badge-downloading', ERROR: 'badge-error'
};
const STATE_RING_CLASS = {
  METADATA: 'state-metadata', CHECKING: 'state-checking', PAUSED: 'state-paused',
  FINISHED: 'state-finished', SEEDING: 'state-seeding', DOWNLOADING: '', ERROR: 'state-error'
};
/* 圓環中心圖示(暫停/完成/分享/錯誤才顯示圖示,下載中顯示百分比數字) */
const STATE_ICON = {
  PAUSED: '<path d="M8 5v14l11-7z"/>',
  FINISHED: '<path d="M5 13l4 4L19 7"/>',
  SEEDING: '<path d="M12 21V9m0 0 5 5m-5-5-5 5M4 3h16"/>',
  ERROR: '<path d="M12 8v5M12 16h.01M10.3 3.9 2.6 17a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>',
  CHECKING: '<circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/>',
  METADATA: '<circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/>'
};

let toastTimer = null;
function showToast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 2200);
}
window.showToast = showToast;

/* ---------- Android bridge 呼叫包裝 ---------- */
function bridgeCall(name, ...args) {
  try {
    if (window.Android && typeof window.Android[name] === 'function') {
      return window.Android[name](...args);
    }
  } catch (e) { console.error('bridge error', name, e); }
  return null;
}

/* ---------- 主題(亮/暗) ---------- */
const root = document.documentElement;
function applyTheme(theme) {
  root.setAttribute('data-theme', theme);
  try { localStorage.setItem('theme', theme); } catch (e) {}
  const darkSwitch = document.getElementById('switchDarkMode');
  if (darkSwitch) darkSwitch.checked = theme === 'dark';
  bridgeCall('setStatusBarTheme', theme === 'dark');
}
function currentTheme() { return root.getAttribute('data-theme') === 'light' ? 'light' : 'dark'; }
(function initTheme() {
  let saved = null;
  try { saved = localStorage.getItem('theme'); } catch (e) {}
  applyTheme(saved === 'light' ? 'light' : 'dark');
})();
document.getElementById('btnTheme').addEventListener('click', () => {
  applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
});
document.getElementById('switchDarkMode').addEventListener('change', (e) => {
  applyTheme(e.target.checked ? 'dark' : 'light');
});

/* ---------- Sheet / 浮窗控制 ---------- */
const overlay = document.getElementById('overlay');
let activeSheet = null;

function openSheet(id) {
  closeSheet();
  const el = document.getElementById(id);
  el.classList.add('show');
  overlay.classList.add('show');
  activeSheet = el;
  bridgeCall('setModalOpen', true);
}
function closeSheet() {
  document.querySelectorAll('.sheet.show, .float-dialog.show').forEach(s => s.classList.remove('show'));
  overlay.classList.remove('show');
  activeSheet = null;
  bridgeCall('setModalOpen', false);
}
overlay.addEventListener('click', closeSheet);

// 給原生 Android 返回鍵呼叫:若有面板開啟就關閉它
window.closeAnySheetForBack = function () {
  if (activeSheet) closeSheet();
};

/* ---------- 任務清單渲染(局部更新,避免整批重建造成閃爍) ---------- */
let currentTorrents = [];
const cardEls = new Map(); // infoHash -> 卡片 DOM 節點

function render(torrents) {
  currentTorrents = torrents;
  const list = document.getElementById('list');
  const empty = document.getElementById('emptyState');

  if (!torrents.length) {
    list.innerHTML = '';
    cardEls.clear();
    empty.classList.add('show');
  } else {
    empty.classList.remove('show');
    const seen = new Set();

    torrents.forEach((t, idx) => {
      seen.add(t.infoHash);
      let el = cardEls.get(t.infoHash);
      if (!el) {
        el = createCardEl(t);
        cardEls.set(t.infoHash, el);
      } else {
        updateCardEl(el, t);
      }
      // 確保 DOM 順序與資料順序一致(沒變動時不做任何操作)
      const ref = list.children[idx];
      if (ref !== el) list.insertBefore(el, ref || null);
    });

    // 移除已經不存在的任務卡片
    cardEls.forEach((el, hash) => {
      if (!seen.has(hash)) {
        el.remove();
        cardEls.delete(hash);
      }
    });
  }

  let totalDown = 0, totalUp = 0;
  torrents.forEach(t => { totalDown += t.downloadRateBps; totalUp += t.uploadRateBps; });
  document.getElementById('statDown').textContent = formatSpeed(totalDown);
  document.getElementById('statUp').textContent = formatSpeed(totalUp);
  document.getElementById('statCount').textContent = torrents.length + ' 個任務';
}

function metaLineFor(t) {
  const pct = Math.round(t.progress * 100);
  const sizeText = formatSize(t.totalSizeBytes);
  if (t.state === 'DOWNLOADING') return `↓${formatSpeed(t.downloadRateBps)} · ${t.seeds} 個種子 · ${sizeText}`;
  if (t.state === 'SEEDING') return `↑${formatSpeed(t.uploadRateBps)} · ${t.peers} 個下載者`;
  if (t.state === 'FINISHED') return sizeText;
  if (t.state === 'PAUSED') return `${pct}% · ${sizeText}`;
  return sizeText;
}

function ringHtml(t) {
  const pct = Math.round(t.progress * 100);
  const ringClass = STATE_RING_CLASS[t.state] || '';
  const icon = STATE_ICON[t.state];
  const inner = icon
    ? `<svg viewBox="0 0 24 24">${icon}</svg>`
    : `<span>${pct}%</span>`;
  return `<div class="ring ${ringClass}" style="--pct:${pct}"><div class="ring-inner">${inner}</div></div>`;
}

function cardInnerHtml(t) {
  const isPaused = t.state === 'PAUSED';
  const playIcon = isPaused
    ? '<path d="M8 5v14l11-7z"/>'
    : '<path d="M6 5h4v14H6zM14 5h4v14h-4z"/>';

  return `
    <div class="card-top">
      ${ringHtml(t)}
      <div class="card-info">
        <div class="card-name">${escapeHtml(t.name)}</div>
        <div class="badge ${STATE_BADGE_CLASS[t.state] || ''}">${STATE_LABEL[t.state] || t.state}</div>
        <div class="card-meta"><span>${metaLineFor(t)}</span></div>
      </div>
    </div>
    <div class="card-actions">
      <button data-action="files" title="選擇下載檔案"><svg viewBox="0 0 24 24"><path d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01"/></svg></button>
      <button data-action="open-folder" title="開啟下載資料夾"${isTorrentComplete(t) ? '' : ' hidden'}><svg viewBox="0 0 24 24"><path d="M4 5h6l2 2h8v11H4z"/></svg></button>
      <button data-action="pause" title="暫停/繼續"><svg viewBox="0 0 24 24">${playIcon}</svg></button>
      <button data-action="remove" class="btn-remove" title="移除"><svg viewBox="0 0 24 24"><path d="M6 7h12M9 7V5h6v2m-8 0 1 13h8l1-13"/></svg></button>
    </div>`;
}

/** 建立全新卡片節點(僅在任務第一次出現時呼叫,才會播放進場動畫) */
function createCardEl(t) {
  const el = document.createElement('div');
  el.className = 'card';
  el.setAttribute('data-hash', t.infoHash);
  el.innerHTML = cardInnerHtml(t);
  el.querySelectorAll('[data-action]').forEach(btn => btn.addEventListener('click', onCardAction));
  document.getElementById('list').appendChild(el);
  return el;
}

/** 局部更新既有卡片的內容,不重建 DOM,不會重播進場動畫 */
function updateCardEl(el, t) {
  const pct = Math.round(t.progress * 100);

  const ring = el.querySelector('.ring');
  ring.className = 'ring ' + (STATE_RING_CLASS[t.state] || '');
  ring.style.setProperty('--pct', pct);
  const icon = STATE_ICON[t.state];
  ring.querySelector('.ring-inner').innerHTML = icon
    ? `<svg viewBox="0 0 24 24">${icon}</svg>`
    : `<span>${pct}%</span>`;

  const nameEl = el.querySelector('.card-name');
  if (nameEl.textContent !== t.name) nameEl.textContent = t.name;

  const badge = el.querySelector('.badge');
  badge.className = 'badge ' + (STATE_BADGE_CLASS[t.state] || '');
  badge.textContent = STATE_LABEL[t.state] || t.state;

  el.querySelector('.card-meta span').textContent = metaLineFor(t);

  const pauseIconEl = el.querySelector('[data-action="pause"] svg');
  pauseIconEl.innerHTML = t.state === 'PAUSED'
    ? '<path d="M8 5v14l11-7z"/>'
    : '<path d="M6 5h4v14H6zM14 5h4v14h-4z"/>';

  const folderButton = el.querySelector('[data-action="open-folder"]');
  if (folderButton) folderButton.hidden = !isTorrentComplete(t);
}

function escapeHtml(s) {
  return (s || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function findTorrent(hash) { return currentTorrents.find(t => t.infoHash === hash); }

function isTorrentComplete(t) {
  return t.state === 'FINISHED' || t.state === 'SEEDING' || t.progress >= 0.999;
}


function onCardAction(e) {
  const card = e.currentTarget.closest('.card');
  const hash = card.getAttribute('data-hash');
  const action = e.currentTarget.getAttribute('data-action');
  const item = findTorrent(hash);
  if (!item) return;

  if (action === 'pause') {
    bridgeCall('pauseResume', hash, item.state === 'PAUSED');
  } else if (action === 'remove') {
    openRemoveSheet(item);
  } else if (action === 'files') {
    openFilesSheet(item);
  } else if (action === 'open-folder') {
    bridgeCall('openDownloadFolder', hash);
  }
}

/* ---------- 更新回呼(由 Android 呼叫) ---------- */
window.onTorrentsUpdate = function (torrents) {
  render(torrents);
};

/* ---------- 新增下載 ---------- */
const inputMagnet = document.getElementById('inputMagnet');

document.getElementById('btnAdd').addEventListener('click', () => {
  inputMagnet.value = '';
  openSheet('sheetAdd');
});
document.getElementById('btnAddCancel').addEventListener('click', closeSheet);

document.getElementById('btnPaste').addEventListener('click', () => {
  const text = bridgeCall('pasteFromClipboard');
  if (text) inputMagnet.value = text;
  else showToast('剪貼簿是空的');
});

document.getElementById('btnPickFile').addEventListener('click', () => {
  bridgeCall('pickTorrentFile');
  closeSheet();
});

document.getElementById('btnAddConfirm').addEventListener('click', () => {
  const text = inputMagnet.value.trim();
  const magnetPattern = /^magnet:\?.*xt=urn:btih:[a-zA-Z0-9]{32,40}.*/i;
  if (magnetPattern.test(text)) {
    bridgeCall('addMagnet', text);
    closeSheet();
    showToast('已加入下載佇列');
  } else if (text.startsWith('magnet:')) {
    showToast('這個磁力連結格式不完整,缺少有效的雜湊值 (xt=urn:btih:...)');
  } else {
    showToast('請貼上有效的磁力連結 (magnet:...)');
  }
});

/* ---------- 設定(浮窗) ---------- */
document.getElementById('btnSettings').addEventListener('click', () => {
  const s = JSON.parse(bridgeCall('getSettings') || '{}');
  document.getElementById('inputDownLimit').value = s.downloadLimitKBps > 0 ? s.downloadLimitKBps : '';
  document.getElementById('inputUpLimit').value = s.uploadLimitKBps > 0 ? s.uploadLimitKBps : '';
  document.getElementById('switchAutoPause').checked = !!s.autoPauseOnFinish;
  document.getElementById('switchDarkMode').checked = currentTheme() === 'dark';
  openSheet('dlgSettings');
});
document.getElementById('btnSettingsCancel').addEventListener('click', closeSheet);
document.getElementById('btnSettingsClose').addEventListener('click', closeSheet);
document.getElementById('btnSettingsSave').addEventListener('click', () => {
  const down = parseInt(document.getElementById('inputDownLimit').value, 10) || 0;
  const up = parseInt(document.getElementById('inputUpLimit').value, 10) || 0;
  const autoPause = document.getElementById('switchAutoPause').checked;
  bridgeCall('saveSettings', down, up, autoPause);
  closeSheet();
  showToast('已儲存,設定立即生效');
});

document.getElementById('linkEmail').addEventListener('click', (e) => {
  bridgeCall('openUrl', 'mailto:' + e.currentTarget.getAttribute('data-email'));
});
document.getElementById('linkWebsite').addEventListener('click', (e) => {
  bridgeCall('openUrl', e.currentTarget.getAttribute('data-url'));
});
document.getElementById('linkPrivacy').addEventListener('click', (e) => {
  bridgeCall('openUrl', e.currentTarget.getAttribute('data-url'));
});

/* ---------- 檔案選擇(浮窗) ---------- */
let filesSheetHash = null;
function openFilesSheet(item) {
  const files = JSON.parse(bridgeCall('getFileList', item.infoHash) || '[]');
  if (!files.length) {
    showToast('還在取得檔案清單,請稍後再試');
    return;
  }
  filesSheetHash = item.infoHash;
  const listEl = document.getElementById('fileList');
  listEl.innerHTML = files.map(f => `
    <label class="file-row">
      <input type="checkbox" data-index="${f.index}" checked>
      <span class="file-name">${escapeHtml(f.name)}</span>
      <span class="file-size">${formatSize(f.size)}</span>
    </label>`).join('');
  openSheet('dlgFiles');
}
document.getElementById('btnFilesCancel').addEventListener('click', closeSheet);
document.getElementById('btnFilesClose').addEventListener('click', closeSheet);
document.getElementById('btnFilesApply').addEventListener('click', () => {
  const checked = Array.from(document.querySelectorAll('#fileList input[type=checkbox]:checked'))
    .map(el => parseInt(el.getAttribute('data-index'), 10));
  bridgeCall('setFileSelection', filesSheetHash, JSON.stringify(checked));
  closeSheet();
  showToast('已更新檔案選擇');
});

/* ---------- 移除確認(浮窗) ---------- */
let removeHash = null;
function openRemoveSheet(item) {
  removeHash = item.infoHash;
  document.getElementById('removeTitle').textContent = `移除「${item.name}」`;
  openSheet('dlgRemove');
}
document.getElementById('btnRemoveCancel').addEventListener('click', closeSheet);
document.getElementById('btnRemoveClose').addEventListener('click', closeSheet);
document.getElementById('btnRemoveOnly').addEventListener('click', () => {
  bridgeCall('removeTorrent', removeHash, false);
  closeSheet();
});
document.getElementById('btnRemoveDelete').addEventListener('click', () => {
  bridgeCall('removeTorrent', removeHash, true);
  closeSheet();
});

/* ---------- 啟動時先拉一次清單 ---------- */
(function init() {
  const version = bridgeCall('getAppVersion');
  if (version) document.getElementById('appVersion').textContent = 'Simple Torrent V' + version;
  const raw = bridgeCall('getTorrents');
  if (raw) render(JSON.parse(raw));
})();
