import { useState, useEffect } from "react";
import IPTVPlayer from "./components/IPTVPlayer";
import ChannelList from "./components/ChannelList";
import { parseM3U } from "./lib/parseM3U";
import SettingsModal from "./components/SettingsModal";
import "./app.css";

export default function App() {
  const [playlistUrl, setPlaylistUrl] = useState("");
  const [channels, setChannels] = useState([]);
  const [current, setCurrent] = useState(null);
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(false);
  const [playerBusy, setPlayerBusy] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);

  // Load saved playlist on startup
  useEffect(() => {
    (async () => {
      try {
        const savedUrl = await window.settings.get("playlistUrl");
        const savedChannels = await window.settings.get("channels");

        if (
          savedChannels &&
          Array.isArray(savedChannels) &&
          savedChannels.length
        ) {
          // use cached playlist directly
          setPlaylistUrl(savedUrl || "");
          setChannels(savedChannels);
          setCurrent(savedChannels[0]);
        } else if (savedUrl) {
          // fallback: fetch from saved URL
          setPlaylistUrl(savedUrl);
          await loadPlaylist(savedUrl, true);
        }
      } catch (e) {
        console.error("Failed to load saved settings:", e);
      }
    })();
  }, []);

  // refresh = true forces new fetch + overwrite saved channels
  async function loadPlaylist(url, refresh = false) {
    setErr("");
    setLoading(true);

    try {
      if (refresh || !channels.length) {
        // fetch and parse
        const text = await window.api.fetchText(url);

        let chs;
        if (text.trim().startsWith("#EXTM3U")) {
          chs = parseM3U(text);
        } else {
          chs = [{ name: "Direct Stream", url }];
        }

        setChannels(chs);
        if (chs.length) setCurrent(chs[0]);

        // save url + parsed channels
        await window.settings.set("playlistUrl", url);
        await window.settings.set("channels", chs);
      } else {
        // already have cached channels
        console.log("Using cached channel list");
      }

      setPlaylistUrl(url);
    } catch (e) {
      console.error(e);
      setErr("Failed to load playlist.");
    } finally {
      setLoading(false);
    }
  }

  async function playChannel(ch) {
    setErr("");
    setPlayerBusy(true);
    try {
      if (ch.url.includes(".m3u8")) {
        setCurrent(ch);
        return;
      }
      const res = await window.player.proxyStream(ch.url);
      if (!res?.ok) throw new Error(res?.error || "Proxy failed");
      setCurrent({ ...ch, url: res.url, proxied: true });
    } catch (e) {
      console.error(e);
      setErr("Could not start in-app playback. Try another channel.");
    } finally {
      setPlayerBusy(false);
    }
  }

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

        <ChannelList
          channels={channels}
          onChoose={(ch) => {
            console.log("▶️ Selected channel:", ch);
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
            <IPTVPlayer streamUrl={current.url} />
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
