import { useState, useEffect, useRef, useCallback } from "react";
import IPTVPlayer from "./components/IPTVPlayer";
import ChannelList from "./components/ChannelList";
import { parseM3U } from "./lib/parseM3U";
import SettingsModal from "./components/SettingsModal";
import "./App.css";

export default function App() {
  const [playlistUrl, setPlaylistUrl] = useState("");
  const [channels, setChannels] = useState([]);
  const [current, setCurrent] = useState(null);
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(false);
  const [playerBusy, setPlayerBusy] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);

  const selection = useRef(0);
  const [failedChannels, setFailedChannels] = useState(new Set());
  const [hideFailed, setHideFailed] = useState(false);
  const [playbackId, setPlaybackId] = useState(0);
  const markChannel = useCallback((url, failed) => {
    setFailedChannels((previous) => {
      const next = new Set(previous);
      if (failed) next.add(url); else next.delete(url);
      return next;
    });
  }, []);

  const playChannel = useCallback(async (ch) => {
    const requestId = ++selection.current;
    setErr("");
    setPlayerBusy(true);
    setCurrent(null);
    try {
      const res = await window.player.proxyStream(ch.url);
      if (requestId !== selection.current) return;
      if (!res?.ok) throw new Error(res?.error || "Proxy failed");
      setCurrent({ ...ch, sourceUrl: ch.url, url: res.url });
      setPlaybackId(requestId);
    } catch {
      if (requestId !== selection.current) return;
      markChannel(ch.url, true);
      setErr("Could not start playback. Select the channel again to retry.");
    } finally {
      if (requestId === selection.current) setPlayerBusy(false);
    }
  }, [markChannel]);

  const loadPlaylist = useCallback(async (url) => {
    setErr("");
    setLoading(true);
    try {
      const text = await window.api.fetchText(url);
      const isHls = /#EXT-X-(?:TARGETDURATION|STREAM-INF|MEDIA-SEQUENCE|MAP)/.test(text);
      const chs = text.trim().startsWith("#EXTM3U") && !isHls
        ? parseM3U(text) : [{ name: "Direct Stream", url }];
      setChannels(chs);
      setFailedChannels(new Set());
      if (chs.length) await playChannel(chs[0]);
      else { selection.current++; setCurrent(null); await window.player.stopProxy(); }
      await window.settings.set("playlistUrl", url);
      await window.settings.set("channels", chs);
      setPlaylistUrl(url);
    } catch (e) {
      console.error(e);
      setErr("Failed to load playlist.");
    } finally {
      setLoading(false);
    }
  }, [playChannel]);

  // Load saved playlist on startup
  useEffect(() => {
    let active = true;
    const selectionRef = selection;
    (async () => {
      try {
        const savedUrl = await window.settings.get("playlistUrl");
        const savedChannels = await window.settings.get("channels");

        if (!active) return;
        if (
          savedChannels &&
          Array.isArray(savedChannels) &&
          savedChannels.length
        ) {
          // use cached playlist directly
          setPlaylistUrl(savedUrl || "");
          setChannels(savedChannels);
          await playChannel(savedChannels[0]);
        } else if (savedUrl) {
          // fallback: fetch from saved URL
          setPlaylistUrl(savedUrl);
          await loadPlaylist(savedUrl, true);
        }
      } catch (e) {
        console.error("Failed to load saved settings:", e);
      }
    })();
    return () => { active = false; selectionRef.current++; void window.player.stopProxy(); };
  }, [loadPlaylist, playChannel]);


  return (
    <div
      style={{
        display: "flex",
        height: "100vh",
        width: "100vw",
        overflow: "hidden",
        background: "#0e0e16",
      }}
    >
      <div style={{ width: 260, display: "flex", flexDirection: "column" }}>
        <button
          onClick={() => setSettingsOpen(true)}
          style={{
            margin: 8,
            padding: "10px 16px",
            borderRadius: 8,
            border: "1px solid #3b82f6",
            background: "#1e3a8a",
            color: "#fff",
            fontWeight: 600,
          }}
        >
          Settings
        </button>
        <button
          onClick={() => loadPlaylist(playlistUrl, true)}
          disabled={!playlistUrl || loading}
          style={{
            margin: 8,
            padding: "10px 16px",
            borderRadius: 8,
            border: "1px solid #3b82f6",
            background: "#1e3a8a",
            color: "#fff",
            fontWeight: 600,
          }}
        >
          {loading ? "Refreshing…" : "Refresh List"}
        </button>

        <label style={{ color: "#eee", padding: 8 }}>
          <input type="checkbox" checked={hideFailed} onChange={(e) => setHideFailed(e.target.checked)} />
          Hide failed channels ({failedChannels.size})
        </label>
        {failedChannels.size > 0 && <button onClick={() => setFailedChannels(new Set())} style={{ margin: 8 }}>Reset failed channels</button>}
        <ChannelList
          channels={hideFailed ? channels.filter((ch) => !failedChannels.has(ch.url)) : channels}
          onChoose={(ch) => {
            playChannel(ch);
          }}
        />
      </div>
      <div style={{ flex: 1, display: "flex", flexDirection: "column" }}>
        {err && <div style={{ color: "#ffb4b4", padding: 12 }}>{err}</div>}

        <div style={{ flex: 1, padding: 12 }}>
          {loading && (
            <div
              style={{
                color: "#9aa0a6",
                display: "grid",
                placeItems: "center",
                height: "100%",
              }}
            >
              Loading playlist…
            </div>
          )}
          {playerBusy && (
            <div
              style={{
                color: "#9aa0a6",
                display: "grid",
                placeItems: "center",
                height: "100%",
              }}
            >
              Preparing in-app playback…
            </div>
          )}
          {!loading && !playerBusy && current && (
            <IPTVPlayer key={playbackId} streamUrl={current.url}
              onFailed={() => markChannel(current.sourceUrl, true)}
              onPlaying={() => markChannel(current.sourceUrl, false)} />
          )}
          {!loading && !playerBusy && !current && (
            <div
              style={{
                color: "#9aa0a6",
                display: "grid",
                placeItems: "center",
                height: "100%",
              }}
            >
              Open Settings to load a playlist.
            </div>
          )}
        </div>
      </div>
      <SettingsModal
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        onLoad={(url) => loadPlaylist(url, true)}
        initialUrl={playlistUrl}
      />
    </div>
  );
}
