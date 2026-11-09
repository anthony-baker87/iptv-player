import { useState, useMemo, useRef } from "react";
import { channelKey, formatTime } from "../lib/library.mjs";
import { organizeSearch, browseRows } from "../lib/browse.mjs";
import { Virtuoso } from "react-virtuoso";

export default function ChannelList({ channels, onChoose, favorites = new Set(), onToggleFavorite, emptyMessage,
  watchStates = {}, progressHistory = {}, onToggleWatched }) {
  const [filter, setFilter] = useState("");
  const [expanded, setExpanded] = useState(new Set());
  const inputRef = useRef(null);
  const result = useMemo(() => organizeSearch(channels, filter), [channels, filter]);
  const rows = useMemo(() => browseRows(result, expanded), [result, expanded]);
  const toggleSeason = (key) => setExpanded((previous) => {
    const next = new Set(previous);
    if (next.has(key)) next.delete(key); else next.add(key);
    return next;
  });
  const renderChannel = (row) => {
    const ch = row.channel;
    const key = channelKey(ch);
    const watched = watchStates[key]?.status === "watched";
    const started = watchStates[key]?.status === "started";
    const progress = progressHistory[key];
    return <div style={{ display: "flex", alignItems: "center", borderBottom: "1px solid #222", color: "#eee", paddingLeft: row.seasonKey ? 8 : 0 }}>
      <button onClick={() => onChoose(ch)} style={{ flex: 1, minWidth: 0, textAlign: "left", padding: "10px 12px", cursor: "pointer", background: "transparent", color: "inherit", border: 0 }}>
        <div>{ch.name}</div>
        {ch.history && <div style={{ color: "#aaa", fontSize: 12, marginTop: 4 }}>
          {ch.history.episode && `Last watched: S${ch.history.episode.season} E${ch.history.episode.episode} · `}
          {ch.history.ended ? "Finished" : `${formatTime(ch.history.position)} / ${formatTime(ch.history.duration)}`}
        </div>}
      </button>
      <label title={watched ? "Watched — click to mark unwatched" : started ? `In progress${progress ? ` · ${formatTime(progress.position)}` : ""} — click to mark watched` : "Mark as watched"}
        style={{ display: "flex", alignItems: "center", cursor: "pointer", color: "#83baff" }}>
        {started && <span aria-label="In progress" style={{ fontSize: 14 }}>◐</span>}
        <input type="checkbox" checked={watched} aria-label={`${watched ? "Mark unwatched" : "Mark watched"}: ${ch.name}`}
          onChange={(event) => onToggleWatched?.(ch, event.target.checked)} style={{ accentColor: "#68d9a4", width: 16, height: 16, cursor: "pointer" }} />
      </label>
      <button aria-label={`${favorites.has(key) ? "Remove" : "Add"} ${ch.name} ${favorites.has(key) ? "from" : "to"} favorites`}
        aria-pressed={favorites.has(key)} onClick={() => onToggleFavorite(ch)}
        style={{ padding: 10, cursor: "pointer", border: 0, background: "transparent", color: favorites.has(key) ? "#ffd166" : "#aaa", fontSize: 22 }}>
        {favorites.has(key) ? "★" : "☆"}
      </button>
    </div>;
  };
  return <div style={{ width: 260, display: "flex", flexDirection: "column", background: "#141425", paddingTop: 3, flex: 1, minHeight: 0 }}>
    <div style={{ padding: 11, borderBottom: "1px solid #222", display: "flex", gap: 6 }}>
      <input ref={inputRef} placeholder="Search channels…" style={{ flex: 1, minWidth: 0, padding: 12, borderRadius: 6, border: "1px solid #333", background: "#0f0f17", color: "#eee" }}
        onKeyDown={(event) => { if (event.key === "Enter") setFilter(inputRef.current.value.trim()); }} />
      <button onClick={() => setFilter(inputRef.current.value.trim())} style={{ padding: "6px 10px", borderRadius: 6, border: "1px solid #3b82f6", background: "#1e3a8a", color: "#e6f0ff", fontWeight: 600, cursor: "pointer" }}>Go</button>
    </div>
    <div style={{ color: "#aab3c6", padding: "6px 10px", fontSize: 11 }}>
      ✓ Watched · ◐ In progress · Click a checkbox to change
      {filter && <div style={{ marginTop: 4 }}>{result.count} results · A–Z / episode order</div>}
    </div>
    <div style={{ flex: 1, minHeight: 0 }}>
      {!result.count && <div style={{ padding: 12, color: "#aaa" }}>{emptyMessage}</div>}
      <Virtuoso key={filter} style={{ height: "100%", width: "100%" }} totalCount={rows.length}
        computeItemKey={(index) => rows[index].type === "season" ? rows[index].key : `${rows[index].seasonKey || ""}:${rows[index].channel.url}:${index}`}
        itemContent={(index) => {
          const row = rows[index];
          if (row.type !== "season") return renderChannel(row);
          const watchedCount = row.channels.filter((channel) => watchStates[channelKey(channel)]?.status === "watched").length;
          return <button aria-expanded={expanded.has(row.key)} onClick={() => toggleSeason(row.key)}
            style={{ display: "block", width: "100%", textAlign: "left", padding: "12px 10px", border: 0, borderBottom: "1px solid #354262", background: "#202e49", color: "#eaf1ff", cursor: "pointer" }}>
            <strong>{expanded.has(row.key) ? "▾" : "▸"} {row.label}</strong>
            <span style={{ display: "block", color: "#aebfd8", fontSize: 12, marginTop: 4 }}>{row.channels.length} items{watchedCount ? ` · ${watchedCount} watched` : ""}</span>
          </button>;
        }} />
    </div>
  </div>;
}
