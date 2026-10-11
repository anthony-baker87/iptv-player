const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const { PassThrough, Writable } = require("node:stream");
const { EventEmitter } = require("node:events");
const handlers = {};
const routes = {};
const children = [];
const express = () => ({ use() {}, get(route, handler) { routes[route] = handler; },
  listen(_port, _host, callback) { queueMicrotask(callback); return new EventEmitter(); } });
express.static = () => {};
const app = { isPackaged: false, commandLine: { appendSwitch() {} }, whenReady: () => ({ then() {} }), on() {} };
const mocks = {
  electron: { app, ipcMain: { handle(name, handler) { handlers[name] = handler; } } },
  express, "ffmpeg-static": "ffmpeg",
  "electron-store": { default: class {} },
  child_process: { spawn() {
    const ff = new EventEmitter();
    ff.stdout = new PassThrough(); ff.stderr = new PassThrough();
    ff.exitCode = null; ff.killed = false;
    ff.kill = () => { ff.killed = true; };
    children.push(ff); return ff;
  } },
};
vm.runInNewContext(fs.readFileSync("electron/main.cjs", "utf8"), {
  require: (name) => mocks[name] || require(name), __dirname: process.cwd(),
  process, console, Buffer, setTimeout, clearTimeout,
});
const tick = () => new Promise((resolve) => setImmediate(resolve));
const response = () => {
  const chunks = [];
  const res = new Writable({ write(chunk, _encoding, callback) { chunks.push(chunk.toString()); callback(); } });
  res.setHeader = () => {}; res.sendStatus = (status) => { res.status = status; };
  res.chunks = chunks; return res;
};
(async () => {
  const first = await handlers["proxy-stream"](null, "https://example.test/live.ts");
  const id = first.url.split("/").pop();
  const res = response(); routes["/mp4/:id"]({ params: { id } }, res);
  const ff = children.at(-1);
  ff.stdout.write("first"); ff.emit("exit", 0);
  assert.equal(res.writableEnded, false, "exit must not end response before stdout drains");
  ff.stdout.end("last"); await tick();
  assert.equal(res.chunks.join(""), "firstlast");
  const next = await handlers["proxy-stream"](null, "https://example.test/next.ts");
  const old = response(); routes["/mp4/:id"]({ params: { id } }, old);
  assert.equal(old.status, 410);
  const live = response(); routes["/mp4/:id"]({ params: { id: next.url.split("/").pop() } }, live);
  live.destroy(); await tick(); assert.equal(children.at(-1).killed, true);
  const errored = response(); routes["/mp4/:id"]({ params: { id: next.url.split("/").pop() } }, errored);
  children.at(-1).emit("error", new Error("FFmpeg launch failed"));
  await tick(); assert.equal(errored.destroyed, true);
  const stopped = response(); routes["/mp4/:id"]({ params: { id: next.url.split("/").pop() } }, stopped);
  await handlers["stop-proxy"]();
  assert.equal(children.at(-1).killed, true);
  assert.equal(stopped.destroyed, true);
  const results = await Promise.all([
    handlers["proxy-stream"](null, "https://example.test/a.ts"),
    handlers["proxy-stream"](null, "https://example.test/b.ts"),
    handlers["proxy-stream"](null, "https://example.test/live.m3u8?token=test"),
  ]);
  assert.equal(results[2].url, "https://example.test/live.m3u8?token=test");
  const stale = response(); routes["/mp4/:id"]({ params: { id: results[1].url.split("/").pop() } }, stale);
  assert.equal(stale.status, 410);
  assert.equal(Object.keys(routes).length, 1, "channel switching must not accumulate routes");
  console.log("Proxy lifecycle checks passed: drain, disconnect, stale URLs, concurrent switching, HLS cleanup.");
})().catch((error) => { console.error(error); process.exitCode = 1; });
