// Integration checks against the built renderer in an isolated hidden Electron window.
const { app, BrowserWindow } = require("electron");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");
const express = require("express");
const fixture = fs.mkdtempSync(path.join(os.tmpdir(), "iptv-renderer-test-"));
app.setPath("userData", path.join(fixture, "profile"));
app.commandLine.appendSwitch("autoplay-policy", "no-user-gesture-required");
let server;
let win;
async function evaluate(expression) {
  try { return await win.webContents.executeJavaScript(`(()=>eval(${JSON.stringify(expression)}))()`); }
  catch (error) { throw new Error(`${error.message}\nExpression: ${expression}`); }
}
async function waitFor(expression) {
  for (let i = 0; i < 150; i++) {
    if (await evaluate(expression)) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Timed out: ${expression}`);
}
app.whenReady().then(async () => {
  execFileSync(require("ffmpeg-static"), ["-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "color=c=navy:s=320x180:r=10", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-c:a", "aac", "-t", "30", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-movflags", "+faststart", path.join(fixture, "clip.mp4")]);
  execFileSync(require("ffmpeg-static"), ["-hide_banner", "-loglevel", "error", "-i", path.join(fixture, "clip.mp4"), "-c", "copy", "-f", "hls", "-hls_playlist_type", "vod", path.join(fixture, "clip.m3u8")]);
  const web = express();
  web.get("/relay/:id.mp4", (_req, res) => res.sendFile(path.join(fixture, "clip.mp4")));
  web.get("/mp4/live", (_req, res) => res.sendFile(path.join(fixture, "clip.mp4")));
  web.use("/media", express.static(fixture));
  web.use(express.static(path.join(__dirname, "../dist")));
  server = await new Promise((resolve) => { const listening = web.listen(0, "127.0.0.1", () => resolve(listening)); });
  const origin = `http://127.0.0.1:${server.address().port}`;
  const channels = [1, 2].map((number) => ({ name: `Test Show S01E0${number}`, group: "Series", url: `${origin}/media/clip.mp4?episode=${number}` }));
  channels.push({ name: "Test Show S01E03", group: "Series", url: `${origin}/media/clip.m3u8` });
  channels.push({ name: "Test Live", url: `${origin}/mp4/live` });
  channels.push({ name: "Test Show S02E10", group: "Series", url: `${origin}/media/clip.mp4?season=2&episode=10` });
  channels.push({ name: "Test Show S02E02", group: "Series", url: `${origin}/media/clip.mp4?season=2&episode=2` });
  channels.push({ name: "Zed News", url: `${origin}/media/clip.mp4?feed=zed` });
  channels.push({ name: "Alpha News", url: `${origin}/media/clip.mp4?feed=alpha` });
  const preload = path.join(fixture, "preload.cjs");
  fs.writeFileSync(preload, `const {contextBridge}=require("electron");const channels=${JSON.stringify(channels)};
contextBridge.exposeInMainWorld("settings",{get:async(key)=>key==="channels"?channels:"",set:async()=>true});
contextBridge.exposeInMainWorld("api",{fetchText:async()=>""});
let relayId=0;const sources=[];
contextBridge.exposeInMainWorld("player",{proxyStream:async(url)=>{sources.push(url);const parsed=new URL(url);if(parsed.pathname.startsWith("/relay/"))return {ok:false,error:"Must use the original provider URL"};return {ok:true,url:parsed.searchParams.has("episode")?parsed.origin+"/relay/"+(++relayId)+".mp4?episode="+parsed.searchParams.get("episode"):url};},stopProxy:async()=>true,sources:()=>sources});
`);
  win = new BrowserWindow({ show: false, width: 1100, height: 700, webPreferences: { preload, contextIsolation: true, sandbox: true, backgroundThrottling: false, offscreen: true } });
  await win.loadURL(origin);
  await waitFor('document.querySelector("video")?.readyState >= 2');
  await waitFor('!document.querySelector("video").paused');
  assert.equal(await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="Last (L)").disabled'), true);
  // Delayed/missing playing events must not leave Last pointing at the wrong link.
  await evaluate('document.addEventListener("playing",window.__blockPlaying=(event)=>event.stopImmediatePropagation(),true)');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="Test Show S01E02").click()');
  await waitFor('document.querySelector("video")?.currentSrc.includes("episode=2") && !document.querySelector("video").paused && [...document.querySelectorAll("button")].find(b=>b.textContent==="Last (L)").title.includes("S01E01")');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="Last (L)").click()');
  await waitFor('document.querySelector("video")?.currentSrc.includes("episode=1") && !document.querySelector("video").paused && [...document.querySelectorAll("button")].find(b=>b.textContent==="Last (L)").title.includes("S01E02")');
  await evaluate('window.dispatchEvent(new KeyboardEvent("keydown",{key:"l",bubbles:true}))');
  await waitFor('document.querySelector("video")?.currentSrc.includes("episode=2") && !document.querySelector("video").paused && [...document.querySelectorAll("button")].find(b=>b.textContent==="Last (L)").title.includes("S01E01")');
  await evaluate('document.querySelector("input").dispatchEvent(new KeyboardEvent("keydown",{key:"l",bubbles:true}))');
  assert.equal(await evaluate('document.querySelector("video").currentSrc.includes("episode=2")'), true);
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="Last (L)").click()');
  await waitFor('document.querySelector("video")?.currentSrc.includes("episode=1") && !document.querySelector("video").paused');
  await evaluate('document.removeEventListener("playing",window.__blockPlaying,true)');
  await waitFor('document.querySelector("video").readyState >= 2 && Number.isFinite(document.querySelector("video").duration)');
  assert.equal(await evaluate('window.player.sources().some(url=>new URL(url).pathname.startsWith("/relay/"))'),false);
  await evaluate('const v=document.querySelector("video");v.volume=0.35;v.muted=true;v.currentTime=8;v.pause();');
  await waitFor('JSON.parse(localStorage.getItem("iptv.audio.v1"))?.volume === 0.35 && Object.values(JSON.parse(localStorage.getItem("iptv.history.v1")) || {})[0]?.position >= 8');
  await evaluate('document.querySelector("button[aria-label^=Add]").click()');
  await waitFor('JSON.parse(localStorage.getItem("iptv.favorites.v1"))?.length === 1');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="Favorites").click()');
  await waitFor('document.querySelectorAll("button[aria-label]").length === 1');
  await win.loadURL(origin);
  await waitFor('document.querySelector("[role=dialog]") !== null');
  const savedAudio = await evaluate('({volume:document.querySelector("video").volume,muted:document.querySelector("video").muted,paused:document.querySelector("video").paused})');
  assert.equal(savedAudio.volume, 0.35); assert.equal(savedAudio.muted, true); assert.equal(savedAudio.paused, true);
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="Resume").click()');
  await waitFor('document.querySelector("video").currentTime >= 8 && !document.querySelector("video").paused');
  await evaluate('document.querySelector("video").pause();[...document.querySelectorAll("button")].find(b=>b.textContent==="All").click()');
  await waitFor('document.querySelectorAll("button[aria-label]").length >= 4');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="Test Show S01E02").click()');
  await waitFor('document.querySelector("video")?.readyState >= 2 && !document.querySelector("[role=dialog]")');
  await evaluate('document.querySelector("video").currentTime=12;document.querySelector("video").pause()');
  await waitFor('Object.values(JSON.parse(localStorage.getItem("iptv.history.v1")) || {}).length === 2');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="History").click()');
  await waitFor('document.body.textContent.includes("Last watched: S1 E2")');
  assert.equal(await evaluate('document.querySelectorAll("button[aria-label]").length'), 1);
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="All").click()');
  await waitFor('document.querySelectorAll("button[aria-label]").length >= 4');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="Test Show S01E03").click()');
  await waitFor('document.querySelector("video")?.readyState >= 2');
  await evaluate('document.querySelector("video").currentTime=9;document.querySelector("video").pause()');
  await waitFor('Object.values(JSON.parse(localStorage.getItem("iptv.history.v1")) || {}).length === 3');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="Test Live").click()');
  await waitFor('document.querySelector("video")?.readyState >= 2');
  await evaluate('document.querySelector("video").currentTime=8;document.querySelector("video").pause()');
  assert.equal(await evaluate('Object.values(JSON.parse(localStorage.getItem("iptv.history.v1")) || {}).length'), 3);
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="Test Show S01E03").click()');
  await waitFor('document.querySelector("[role=dialog]") !== null');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="Resume").click()');
  await waitFor('document.querySelector("video").currentTime >= 9 && !document.querySelector("video").paused');
  await evaluate('document.querySelector("video").pause();[...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="Test Show S01E03").click()');
  await waitFor('document.querySelector("[role=dialog]") !== null');
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="Start over").click()');
  await waitFor('Object.values(JSON.parse(localStorage.getItem("iptv.history.v1"))).find(r=>r.channel.name==="Test Show S01E03")?.position === 0');
  await evaluate('document.querySelector("video").currentTime=document.querySelector("video").duration - 0.1');
  await waitFor('document.querySelector("video").ended');
  await waitFor('Object.values(JSON.parse(localStorage.getItem("iptv.history.v1"))).find(r=>r.channel.name==="Test Show S01E03")?.ended === true');
  assert.equal(await evaluate('document.querySelector("details")'), null, "Translation controls must be removed");
  assert.equal(await evaluate('typeof window.translation'), "undefined", "Translation bridge must be removed");
  await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="Test Show S01E02").click()');
  await waitFor('document.querySelector("video")?.readyState >= 2');
  const searchFor = async (query) => {
    await evaluate(`const input=document.querySelector('input[placeholder^="Search channels"]');input.value=${JSON.stringify(query)};input.dispatchEvent(new KeyboardEvent("keydown",{key:"Enter",bubbles:true}));`);
  };
  await searchFor("Test Show");
  await waitFor('document.querySelectorAll("button[aria-expanded]").length === 2');
  assert.equal(await evaluate('document.querySelectorAll("button[aria-label^=Add]").length'), 0, "Season folders should start collapsed");
  await evaluate('[...document.querySelectorAll("button[aria-expanded]")].find(b=>b.textContent.includes("Season 2")).click()');
  await waitFor('[...document.querySelectorAll("button")].filter(b=>b.textContent.startsWith("Test Show S02")).length === 2');
  assert.deepEqual(await evaluate('[...document.querySelectorAll("button")].filter(b=>b.textContent.startsWith("Test Show S02")).map(b=>b.textContent)'), ["Test Show S02E02", "Test Show S02E10"]);
  await evaluate(`document.querySelector('input[aria-label="Mark watched: Test Show S02E02"]').click()`);
  await waitFor(`document.querySelector('input[aria-label="Mark unwatched: Test Show S02E02"]')?.checked === true`);
  await win.loadURL(origin);
  await waitFor('document.querySelector("[role=dialog]") !== null');
  await searchFor("Test Show");
  await waitFor('document.querySelectorAll("button[aria-expanded]").length === 2');
  await evaluate('[...document.querySelectorAll("button[aria-expanded]")].find(b=>b.textContent.includes("Season 2")).click()');
  await waitFor(`document.querySelector('input[aria-label="Mark unwatched: Test Show S02E02"]')?.checked === true`);
  await evaluate(`document.querySelector('input[aria-label="Mark unwatched: Test Show S02E02"]').click()`);
  await waitFor(`document.querySelector('input[aria-label="Mark watched: Test Show S02E02"]')?.checked === false`);
  await evaluate('[...document.querySelectorAll("button[aria-expanded]")].find(b=>b.textContent.includes("Season 1")).click()');
  await waitFor(`document.querySelector('input[aria-label="Mark unwatched: Test Show S01E03"]')?.checked === true`);
  assert.equal(await evaluate('[...document.querySelectorAll("button")].find(b=>b.textContent==="Test Show S01E01").parentElement.querySelector("span[aria-label]") !== null'), true);
  await searchFor("News");
  await waitFor('[...document.querySelectorAll("button")].filter(b=>b.textContent.endsWith("News")).length === 2');
  assert.equal(await evaluate('document.querySelectorAll("button[aria-expanded]").length'), 0);
  assert.deepEqual(await evaluate('[...document.querySelectorAll("button")].filter(b=>b.textContent.endsWith("News")).map(b=>b.textContent)'), ["Alpha News", "Zed News"]);
  await searchFor("Test Show");
  await waitFor('document.querySelectorAll("button[aria-expanded]").length === 2');
  await evaluate('const second=[...document.querySelectorAll("button[aria-expanded]")].find(b=>b.textContent.includes("Season 2"));if(second.getAttribute("aria-expanded")==="true")second.click();[...document.querySelectorAll("button")].find(b=>b.textContent==="Start over").click()');
  await waitFor('document.querySelector("video")?.readyState >= 2');
  await evaluate('document.querySelector("video").pause()');
  if (process.env.IPTV_TEST_SCREENSHOT) {
    await new Promise((resolve) => setTimeout(resolve, 300));
    fs.writeFileSync(process.env.IPTV_TEST_SCREENSHOT, (await win.webContents.capturePage()).toPNG());
  }
  console.log("Renderer checks passed: Last with delayed playing events and fresh proxy URLs, volume/mute persistence, favorites, native/HLS resume, live exclusion, episode history, subtitle controls removed, season folders, alphabetical search, watched marker persistence.");
}).catch((error) => { console.error(error); process.exitCode = 1; }).finally(() => {
  win?.destroy(); server?.close(); app.exit(process.exitCode || 0);
});
