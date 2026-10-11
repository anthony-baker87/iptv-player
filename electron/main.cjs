// electron/main.cjs
const { app, BrowserWindow, ipcMain, net, screen } = require("electron");
const path = require("path");
const { spawn } = require("child_process");
const express = require("express");
const ffmpegPath = require("ffmpeg-static");
const fsp = require("fs/promises");
const crypto = require("crypto");
const Store = require("electron-store").default;

const store = new Store();
const isDev = !app.isPackaged;
let expressApp = null;

let win;
let httpServer = null;
let httpPort = 39221;
let hlsRoot = null;
let proxyQueue = Promise.resolve();
const queueProxy = (operation) => {
  const result = proxyQueue.then(operation);
  proxyQueue = result.catch(() => {});
  return result;
};
let currentProxy = null; // { id, sourceUrl, sessions }

// ---------- Window ----------
function createWindow() {
  const { width, height } = screen.getPrimaryDisplay().workArea;
  win = new BrowserWindow({
    width,
    height,
    backgroundColor: "#121218",
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
    frame: true,
    fullscreen: false,
  });

  if (isDev) {
    win.loadURL("http://localhost:5173");
    win.webContents.openDevTools({ mode: "detach" });
  } else {
    const indexPath = path.join(app.getAppPath(), "dist", "index.html");
    console.log("Loading index from:", indexPath);
    win.loadFile(indexPath).catch((err) => {
      console.error("Failed to load index.html:", err);
    });
  }
}

app.commandLine.appendSwitch("ignore-certificate-errors");

app.whenReady().then(async () => {
  hlsRoot = path.join(app.getPath("userData"), "hls");
  await fsp.mkdir(hlsRoot, { recursive: true });
  await ensureHlsServer();

  createWindow();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", async () => {
  await stopProxy();
  if (httpServer) {
    httpServer.close();
    httpServer = null;
  }
  if (process.platform !== "darwin") app.quit();
});

// ---------- Local HLS server ----------
async function ensureHlsServer() {
  if (httpServer) return;

  expressApp = express();

  expressApp.use((req, res, next) => {
    res.header("Access-Control-Allow-Origin", "*");
    res.header("Access-Control-Allow-Methods", "GET, OPTIONS");
    res.header("Access-Control-Allow-Headers", "Content-Type, Range");
    next();
  });

  const noCache = (req, res, next) => {
    res.setHeader(
      "Cache-Control",
      "no-store, no-cache, must-revalidate, proxy-revalidate"
    );
    res.setHeader("Pragma", "no-cache");
    res.setHeader("Expires", "0");
    res.setHeader("Surrogate-Control", "no-store");
    next();
  };

  expressApp.use(
    "/hls",
    noCache,
    express.static(hlsRoot, {
      fallthrough: false,
      etag: false,
      lastModified: false,
      cacheControl: false,
    })
  );

  // One route for all sessions; stale URLs cannot restart old channels.
  expressApp.get("/mp4/:id", (req, res) => {
    const proxy = currentProxy;
    if (!proxy || req.params.id !== proxy.id) return res.sendStatus(410);
    const ffmpegBin = isDev ? ffmpegPath : path.join(process.resourcesPath, "ffmpeg.exe");
    const ff = spawn(ffmpegBin, [
      "-hide_banner", "-loglevel", "warning", "-user_agent", "Mozilla/5.0",
      "-reconnect", "1", "-reconnect_streamed", "1",
      "-reconnect_on_network_error", "1", "-reconnect_on_http_error", "5xx",
      "-reconnect_delay_max", "5", "-rw_timeout", "15000000",
      "-fflags", "+genpts+discardcorrupt", "-probesize", "5M",
      "-analyzeduration", "5M", "-i", proxy.sourceUrl,
      "-c:v", "copy", "-c:a", "aac", "-b:a", "128k", "-ac", "2",
      "-f", "mp4", "-movflags", "frag_keyframe+empty_moov+default_base_moof", "pipe:1",
    ], { stdio: ["ignore", "pipe", "pipe"] });
    let cleaned = false;
    const cleanup = () => {
      if (cleaned) return;
      cleaned = true;
      proxy.sessions.delete(cleanup);
      ff.stdout.unpipe(res);
      ff.stdout.destroy();
      if (ff.exitCode === null && !ff.killed) ff.kill();
      if (!res.destroyed) res.destroy();
    };
    proxy.sessions.add(cleanup);
    res.setHeader("Content-Type", "video/mp4");
    res.setHeader("Cache-Control", "no-store");
    res.on("close", cleanup);
    res.on("error", cleanup);
    ff.on("error", cleanup);
    ff.stdout.on("error", cleanup);
    // Drain stderr so FFmpeg cannot block on a full pipe.
    ff.stderr.on("data", () => {});
    ff.stderr.on("error", cleanup);
    // pipe ends the response only after stdout has drained, not at process exit.
    ff.stdout.pipe(res);
  });

  await new Promise((res, reject) => {
    httpServer = expressApp.listen(httpPort, "127.0.0.1", () => {
      console.log(`Local server http://127.0.0.1:${httpPort}`);
      res();
    });
    httpServer.once("error", reject);
  });
}

// ---------- Proxy control ----------
async function startProxy(sourceUrl) {
  await ensureHlsServer();
  await stopProxy();

  const id = crypto.randomBytes(6).toString("hex");
  const routePath = `/mp4/${id}`;

  currentProxy = { id, sourceUrl, sessions: new Set() };
  return `http://127.0.0.1:${httpPort}${routePath}`;
}

async function stopProxy() {
  const proxy = currentProxy;
  currentProxy = null;
  if (proxy) for (const cleanup of [...proxy.sessions]) cleanup();
}

// ---------- IPC ----------
ipcMain.handle("fetch-text", async (_e, url) => {
  return new Promise((resolve, reject) => {
    const request = net.request(url);
    const chunks = [];
    const timer = setTimeout(() => { request.abort(); reject(new Error("Playlist request timed out")); }, 20000);
    const fail = (error) => { clearTimeout(timer); reject(error); };
    request.on("response", (response) => {
      if (response.statusCode < 200 || response.statusCode >= 300) {
        fail(new Error(`Playlist HTTP ${response.statusCode}`));
        request.abort();
        return;
      }
      response.on("error", fail);
      response.on("data", (chunk) => chunks.push(Buffer.from(chunk)));
      response.on("end", () => { clearTimeout(timer); resolve(Buffer.concat(chunks).toString("utf8")); });
    });
    request.on("error", fail);
    request.end();
  });
});

ipcMain.handle("proxy-stream", (_e, tsUrl) => queueProxy(async () => {
  try {
    await stopProxy();
    if (/\.m3u8(?:[?#]|$)/i.test(tsUrl)) {
      return { ok: true, url: tsUrl };
    } else {
      const localUrl = await startProxy(tsUrl);
      return { ok: true, url: localUrl };
    }
  } catch (err) {
    console.error("proxy-stream error:", err);
    return { ok: false, error: String(err?.message || err) };
  }
}));

ipcMain.handle("stop-proxy", () => queueProxy(async () => {
  await stopProxy();
  return true;
}));

// ---------- Settings IPC (electron-store) ----------
ipcMain.handle("settings:get", (_e, key) => {
  return store.get(key);
});

ipcMain.handle("settings:set", (_e, key, value) => {
  store.set(key, value);
  return true;
});
