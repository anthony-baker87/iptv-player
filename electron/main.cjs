// electron/main.cjs
const { app, BrowserWindow, ipcMain, net, screen } = require("electron");
const path = require("path");
const { spawn } = require("child_process");
const express = require("express");
const ffmpegPath = require("ffmpeg-static");
const fs = require("fs");
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
let currentProxy = null; // { id, folder, ffmpeg }

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

  await new Promise((res) => {
    httpServer = expressApp.listen(httpPort, "127.0.0.1", () => {
      console.log(`Local server http://127.0.0.1:${httpPort}`);
      res();
    });
  });
}

// ---------- Proxy control ----------
async function startProxy(sourceUrl) {
  await ensureHlsServer();
  await stopProxy();

  const id = crypto.randomBytes(6).toString("hex");
  const routePath = `/mp4/${id}`;

  expressApp.get(routePath, (req, res) => {
    const ffmpegBin = isDev
      ? ffmpegPath // use node_modules path in dev
      : path.join(process.resourcesPath, "ffmpeg.exe"); // packaged path

    const args = [
      "-hide_banner",
      "-loglevel",
      "warning",
      "-user_agent",
      "Mozilla/5.0",
      "-reconnect",
      "1",
      "-reconnect_streamed",
      "1",
      "-reconnect_on_http_error",
      "4xx,5xx",
      "-reconnect_delay_max",
      "5",
      "-fflags",
      "+genpts+discardcorrupt",
      "-probesize",
      "50M",
      "-analyzeduration",
      "50M",
      "-i",
      sourceUrl,
      "-c:v",
      "copy",
      "-c:a",
      "aac",
      "-b:a",
      "128k",
      "-ac",
      "2",
      "-f",
      "mp4",
      "-movflags",
      "frag_keyframe+empty_moov+default_base_moof",
      "pipe:1",
    ];

    const ff = spawn(ffmpegBin, args);

    res.setHeader("Content-Type", "video/mp4");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Connection", "keep-alive");

    ff.stdout.pipe(res);

    ff.on("exit", () => res.end());
    req.on("close", () => {
      if (!ff.killed) ff.kill("SIGTERM");
    });
  });

  return `http://127.0.0.1:${httpPort}${routePath}`;
}

async function stopProxy() {
  if (!currentProxy) return;
  try {
    if (currentProxy.ffmpeg && !currentProxy.ffmpeg.killed) {
      currentProxy.ffmpeg.kill("SIGTERM");
    }
  } catch (_) {}
  currentProxy = null;
}

// ---------- IPC ----------
ipcMain.handle("fetch-text", async (_e, url) => {
  return new Promise((resolve, reject) => {
    const request = net.request(url);
    const chunks = [];
    request.on("response", (response) => {
      response.on("data", (chunk) => chunks.push(Buffer.from(chunk)));
      response.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    });
    request.on("error", reject);
    request.end();
  });
});

ipcMain.handle("proxy-stream", async (_e, tsUrl) => {
  try {
    if (tsUrl.endsWith(".m3u8")) {
      return { ok: true, url: tsUrl };
    } else {
      const localUrl = await startProxy(tsUrl);
      return { ok: true, url: localUrl };
    }
  } catch (err) {
    console.error("proxy-stream error:", err);
    return { ok: false, error: String(err?.message || err) };
  }
});

ipcMain.handle("stop-proxy", async () => {
  await stopProxy();
  return true;
});

// ---------- Settings IPC (electron-store) ----------
ipcMain.handle("settings:get", (_e, key) => {
  return store.get(key);
});

ipcMain.handle("settings:set", (_e, key, value) => {
  store.set(key, value);
  return true;
});
