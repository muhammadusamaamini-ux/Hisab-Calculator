// Hisab Calculator for Windows — main process: the window and app updates.
const { app, BrowserWindow, ipcMain, dialog, Notification, shell, Menu } = require('electron');
const path = require('path');
const fs = require('fs');

app.setAppUserModelId('com.usama.hisab.pc');
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) app.quit();

let win = null;
const dataFile = (name) => path.join(app.getPath('userData'), name);
const readJson = (name, fallback) => { try { return JSON.parse(fs.readFileSync(dataFile(name), 'utf8')); } catch (e) { return fallback; } };
const writeJson = (name, value) => { try { fs.writeFileSync(dataFile(name), JSON.stringify(value)); } catch (e) { /* ignore */ } };
const buildOf = (v) => String(v || '').split('.').pop();

function createWindow() {
  const saved = readJson('window.json', {});
  win = new BrowserWindow({
    width: saved.width || 440,
    height: saved.height || 880,
    x: saved.x, y: saved.y,
    minWidth: 340,
    minHeight: 560,
    title: 'Hisab Calculator',
    autoHideMenuBar: true,
    backgroundColor: '#c9cdd3',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false },
  });
  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'web', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('file:')) { e.preventDefault(); shell.openExternal(url); } });
  win.on('close', () => { if (!win.isMaximized() && !win.isMinimized()) writeJson('window.json', win.getBounds()); });
  win.on('closed', () => { win = null; });
}

app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(() => { if (!gotLock) return; createWindow(); setupUpdates(); });
app.on('window-all-closed', () => app.quit());

ipcMain.handle('app:version', () => app.getVersion());

// ---------- updates (from the newest GitHub release) ----------
let updater = null;
let manual = false;
let downloading = false;
let lastInfo = null;

function notesText(info) {
  let n = info && info.releaseNotes;
  if (Array.isArray(n)) n = n.map((x) => x.note || '').join('\n');
  return String(n || '').replace(/<[^>]+>/g, '\n').split(/\r?\n/).map((s) => s.trim())
    .filter((l) => l && !/^(Co-Authored-By|Claude-Session)/i.test(l))
    .map((l) => (l.startsWith('- ') ? '• ' + l.slice(2) : l)).join('\n');
}

function box(opts) { return win ? dialog.showMessageBox(win, opts) : dialog.showMessageBox(opts); }

function offer(info) {
  if (!info) return;
  const notes = notesText(info);
  box({
    type: 'info', buttons: ['Download and install', 'Later'], defaultId: 0, cancelId: 1,
    title: 'Update available',
    message: `Build ${buildOf(info.version)} is ready (you have build ${buildOf(app.getVersion())}).`,
    detail: (notes ? "What's new:\n" + notes + '\n\n' : '') + 'Your history, memory and settings are kept.',
  }).then((r) => {
    if (r.response !== 0) return;
    downloading = true;
    if (win) win.setProgressBar(2);
    updater.downloadUpdate().catch(() => {});
  });
}

function notifyOnce(info) {
  const seen = readJson('updates.json', {});
  if (seen.notified === info.version) return;
  writeJson('updates.json', { notified: info.version });
  if (!Notification.isSupported()) return;
  const notes = notesText(info);
  const n = new Notification({
    title: 'Hisab Calculator update available',
    body: notes ? "What's new: " + notes.replace(/\n/g, ' ') : `Build ${buildOf(info.version)} is ready. Click to install it.`,
  });
  n.on('click', () => { if (win) { win.show(); win.focus(); } offer(lastInfo); });
  n.show();
}

function setupUpdates() {
  if (!app.isPackaged) return;
  try { updater = require('electron-updater').autoUpdater; } catch (e) { return; }
  updater.autoDownload = false;
  updater.autoInstallOnAppQuit = true;
  updater.on('update-available', (info) => {
    lastInfo = info;
    if (manual) { manual = false; offer(info); } else notifyOnce(info);
  });
  updater.on('update-not-available', () => {
    if (!manual) return;
    manual = false;
    box({ type: 'info', title: "You're up to date", message: `You have the newest version (build ${buildOf(app.getVersion())}).` });
  });
  updater.on('download-progress', (p) => { if (win) win.setProgressBar(Math.max(0.01, p.percent / 100)); });
  updater.on('update-downloaded', () => {
    downloading = false;
    if (win) win.setProgressBar(-1);
    box({
      type: 'info', buttons: ['Restart now', 'Later'], defaultId: 0, cancelId: 1,
      title: 'Update ready', message: 'The update is downloaded. Restart Hisab Calculator to finish installing it.',
    }).then((r) => { if (r.response === 0) updater.quitAndInstall(false, true); });
  });
  updater.on('error', () => {
    if (win) win.setProgressBar(-1);
    if (manual || downloading) {
      box({ type: 'warning', title: 'Update failed', message: "Couldn't reach GitHub or finish the download. Check your internet connection and try again." });
    }
    manual = false; downloading = false;
  });
  const quiet = () => { if (!manual && !downloading) updater.checkForUpdates().catch(() => {}); };
  setTimeout(quiet, 5000);
  setInterval(quiet, 6 * 60 * 60 * 1000);
}

ipcMain.handle('upd:check', async () => {
  if (!updater) {
    box({ type: 'info', title: 'Check for updates', message: 'Updates work in the installed app.' });
    return false;
  }
  if (downloading) return true;
  manual = true;
  try { await updater.checkForUpdates(); } catch (e) { /* the error event shows the message */ }
  return true;
});
