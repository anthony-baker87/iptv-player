const { contextBridge, ipcRenderer } = require("electron");

// Existing API
contextBridge.exposeInMainWorld("api", {
  fetchText: (url) => ipcRenderer.invoke("fetch-text", url),
});

contextBridge.exposeInMainWorld("player", {
  proxyStream: (url) => ipcRenderer.invoke("proxy-stream", url),
  stopProxy: () => ipcRenderer.invoke("stop-proxy"),
});

// settings persistence API
contextBridge.exposeInMainWorld("settings", {
  get: (key) => ipcRenderer.invoke("settings:get", key),
  set: (key, value) => ipcRenderer.invoke("settings:set", key, value),
});
