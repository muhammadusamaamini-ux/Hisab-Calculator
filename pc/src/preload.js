// Gives the calculator page two small helpers: the app version and "Check for updates".
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('hisabPC', {
  version: () => ipcRenderer.invoke('app:version'),
  checkUpdates: () => ipcRenderer.invoke('upd:check'),
});
