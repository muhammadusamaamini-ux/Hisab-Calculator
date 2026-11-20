// Gives the calculator page small helpers: the app version, "Check for updates" and saving exported bills.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('hisabPC', {
  version: () => ipcRenderer.invoke('app:version'),
  checkUpdates: () => ipcRenderer.invoke('upd:check'),
  saveFile: (name, b64) => ipcRenderer.invoke('file:save', { name, b64 }),
});
