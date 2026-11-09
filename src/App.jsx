import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import IPTVPlayer from "./components/IPTVPlayer";
import ChannelList from "./components/ChannelList";
import { parseM3U } from "./lib/parseM3U";
import SettingsModal from "./components/SettingsModal";
import "./App.css";
import { useSavedState } from "./lib/useSavedState";
import { channelKey, historyItems, updateHistory } from "./lib/library.mjs";
import { originalStream, rememberStream } from "./lib/navigation.mjs";
import { recordWatch, setWatchStatus, validWatchStates, watchStatesFromHistory } from "./lib/watched.mjs";

export default function App() {
  const [playlistUrl, setPlaylistUrl] = useState("");
  const [channels, setChannels] = useState([]);
  const [current, setCurrent] = useState(null);
  const [watched, setWatched] = useState({ active: null, previous: null });
  const lastChannel = current ? watched.previous : watched.active;
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(false);
  const [playerBusy, setPlayerBusy] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);

  const [favorites, setFavorites, favoriteError] = useSavedState("iptv.favorites.v1", [], (value) => Array.isArray(value) && value.every((key) => typeof key === "string"));
  const [audio, setAudio, audioError] = useSavedState("iptv.audio.v1", { volume: 1, muted: false }, (value) => value && Number.isFinite(value.volume) && value.volume >= 0 && value.volume <= 1 && typeof value.muted === "boolean");
  const [history, setHistory, historyError] = useSavedState("iptv.history.v1", {}, (value) => value && typeof value === "object" && !Array.isArray(value) && Object.values(value).every((item) => item && typeof item.key === "string" && Number.isFinite(item.position) && Number.isFinite(item.duration) && Number.isFinite(item.updatedAt)));
  const [watchStates, setWatchStates, watchError] = useSavedState("iptv.watched.v1", watchStatesFromHistory(history), validWatchStates);
  const watchOverrides = useRef(new Set());
  const toggleWatched = (channel, completed) => {
    watchOverrides.current.add(channelKey(channel));
    setWatchStates((previous) => setWatchStatus(previous, channel, completed));
  };
  const [view, setView] = useState("all");
  const toggleFavorite = (ch) => {
    const key = channelKey(ch);
    setFavorites((previous) => previous.includes(key) ? previous.filter((item) => item !== key) : [...previous, key]);
  };
  const selection = useRef(0);
  const [failedChannels, setFailedChannels] = useState(new Set());
  const [hideFailed, setHideFailed] = useState(false);
  const [playbackId, setPlaybackId] = useState(0);
  const favoriteKeys = useMemo(() => new Set(favorites), [favorites]);
  const historyForView = view === "history" ? history : null;
  const visibleChannels = useMemo(() => (historyForView ? historyItems(historyForView, channels) : channels)
    .filter((ch) => (view !== "favorites" || favoriteKeys.has(channelKey(ch))) && (!hideFailed || !failedChannels.has(ch.url))),
  [view, historyForView, channels, favoriteKeys, hideFailed, failedChannels]);
  const markChannel = useCallback((url, failed) => {
    setFailedChannels((previous) => {
      const next = new Set(previous);
      if (failed) next.add(url); else next.delete(url);
      return next;
    });
  }, []);

  const playChannel = useCallback(async (ch) => {
    const source = originalStream(ch);
    const requestId = ++selection.current;
    setErr("");
    setPlayerBusy(true);
    setCurrent(null);
    try {
      const res = await window.player.proxyStream(source.url);
      if (requestId !== selection.current) return;
      if (!res?.ok) throw new Error(res?.error || "Proxy failed");
      watchOverrides.current.delete(channelKey(source));
      setCurrent({ ...source, sourceUrl: source.url, url: res.url });
      setWatched((previous) => rememberStream(previous, source));
      setPlaybackId(requestId);
    } catch {
      if (requestId !== selection.current) return;
      markChannel(ch.url, true);
      setErr("Could not start playback. Select the channel again to retry.");
    } finally {
      if (requestId === selection.current) setPlayerBusy(false);
    }
  }, [markChannel]);

  useEffect(() => {
    const handleLastKey = (event) => {
      if (event.key.toLowerCase() !== "l" || event.repeat || event.ctrlKey || event.altKey || event.metaKey ||
          event.target.closest?.("input, textarea, select, [contenteditable='true']") ||
          !lastChannel || playerBusy || loading || settingsOpen) return;
      event.preventDefault();
      void playChannel(lastChannel);
    };
    window.addEventListener("keydown", handleLastKey);
    return () => window.removeEventListener("keydown", handleLastKey);
  }, [lastChannel, playerBusy, loading, settingsOpen, playChannel]);

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

        <button
          onClick={() => lastChannel && playChannel(lastChannel)}
          disabled={!lastChannel || playerBusy || loading}
          title={lastChannel ? `Switch to ${lastChannel.name} (L)` : "Watch another channel to enable Last"}
          style={{ margin: 8, padding: "10px 16px", borderRadius: 8, border: "1px solid #3b82f6", background: "#1e3a8a", color: "white", fontWeight: 600, opacity: lastChannel && !playerBusy && !loading ? 1 : 0.5 }}
        >Last (L)</button>

        <label style={{ color: "#eee", padding: 8 }}>
          <input type="checkbox" checked={hideFailed} onChange={(e) => setHideFailed(e.target.checked)} />
          Hide failed channels ({failedChannels.size})
        </label>
        {failedChannels.size > 0 && <button onClick={() => setFailedChannels(new Set())} style={{ margin: 8 }}>Reset failed channels</button>}
        <div style={{ display: "flex", gap: 4, padding: 8 }}>
          {[ ["all", "All"], ["favorites", "Favorites"], ["history", "History"] ].map(([key, label]) =>
            <button key={key} aria-pressed={view === key} onClick={() => setView(key)} style={{ flex: 1, padding: 8, background: view === key ? "#1e3a8a" : "#242438", color: "white", border: 0, borderRadius: 6 }}>{label}</button>)}
        </div>
        {view === "history" && <div style={{ color: "#aaa", padding: "0 8px 8px", fontSize: 12 }}>Movies and last watched episode per series</div>}
        <ChannelList
          channels={visibleChannels}
          favorites={favoriteKeys}
          watchStates={watchStates}
          progressHistory={history}
          onToggleWatched={toggleWatched}
          onToggleFavorite={toggleFavorite}
          emptyMessage={view === "favorites" ? "Star a channel to add it here." : view === "history" ? "Watched movies and episodes will appear here." : "No matching channels."}
          onChoose={(ch) => {
            playChannel(ch);
          }}
        />
      </div>
      <div style={{ flex: 1, display: "flex", flexDirection: "column" }}>
        {(favoriteError || audioError || historyError || watchError) && <div role="alert" style={{ color: "#ffb4b4", padding: 12 }}>{favoriteError || audioError || historyError || watchError}</div>}
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
              audio={audio} onAudioChange={setAudio}
              resumeRecord={history[channelKey(current)]}
              onProgress={(progress) => {
                setHistory((previous) => updateHistory(previous, current, progress));
                if (!watchOverrides.current.has(channelKey(current))) setWatchStates((previous) => recordWatch(previous, current, progress));
              }}
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
