import { useState, useMemo, useRef } from "react";
import { Virtuoso } from "react-virtuoso";

export default function ChannelList({ channels, onChoose }) {
  const [filter, setFilter] = useState("");
  const inputRef = useRef(null);

  const filtered = useMemo(() => {
    if (!filter) return channels;
    const q = filter.toLowerCase();
    return channels.filter((ch) => ch.name.toLowerCase().includes(q));
  }, [channels, filter]);

  return (
    <div
      style={{
        width: 260,
        display: "flex",
        flexDirection: "column",
        background: "#141425",
        paddingTop: "3px",
        flex: 1,
        minHeight: 0,
      }}
    >
      <div
        style={{
          padding: 11,
          borderBottom: "1px solid #222",
          display: "flex",
          gap: 6,
        }}
      >
        <input
          ref={inputRef}
          placeholder="Search channels…"
          style={{
            flex: 1,
            padding: "12px 12px",
            borderRadius: 6,
            border: "1px solid #333",
            background: "#0f0f17",
            color: "#eee",
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter") setFilter(inputRef.current.value);
          }}
        />
        <button
          onClick={() => setFilter(inputRef.current.value)}
          style={{
            padding: "6px 10px",
            borderRadius: 6,
            border: "1px solid #3b82f6",
            background: "#1e3a8a",
            color: "#e6f0ff",
            fontWeight: 600,
            cursor: "pointer",
          }}
        >
          Go
        </button>
      </div>
      <div style={{ flex: 1 }}>
        <Virtuoso
          style={{ height: "100%", width: "100%" }}
          totalCount={filtered.length}
          itemContent={(index) => {
            const ch = filtered[index];
            return (
              <div
                key={index}
                onClick={() => onChoose(ch)}
                style={{
                  padding: "8px 12px",
                  cursor: "pointer",
                  borderBottom: "1px solid #222",
                  color: "#eee",
                }}
              >
                {ch.name}
              </div>
            );
          }}
        />
      </div>
    </div>
  );
}
