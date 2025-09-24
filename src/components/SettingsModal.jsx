import { useState, useEffect } from "react";

export default function SettingsModal({ open, onClose, onLoad, initialUrl }) {
  const [url, setUrl] = useState(initialUrl || "");

  useEffect(() => {
    setUrl(initialUrl || "");
  }, [initialUrl]);

  if (!open) return null;

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.6)",
        display: "grid",
        placeItems: "center",
        zIndex: 1000,
      }}
    >
      <div
        style={{
          background: "#141425",
          padding: 20,
          borderRadius: 12,
          width: 400,
          display: "flex",
          flexDirection: "column",
          gap: 12,
        }}
      >
        <h2 style={{ color: "#eee", marginBottom: 8 }}>Playlist URL</h2>

        <input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="Paste M3U / M3U8 URL"
          style={{
            width: "100%",
            padding: "10px 12px",
            borderRadius: 8,
            border: "1px solid #333",
            background: "#0f0f17",
            color: "#eee",
          }}
        />

        <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
          <button
            onClick={() => {
              onLoad(url.trim());
              onClose();
            }}
            style={{
              flex: 1,
              padding: "10px 16px",
              borderRadius: 8,
              border: "1px solid #3b82f6",
              background: "#1e3a8a",
              color: "#fff",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            Load Playlist
          </button>

          <button
            onClick={onClose}
            style={{
              flex: 1,
              padding: "10px 16px",
              borderRadius: 8,
              border: "1px solid #666",
              background: "#333",
              color: "#eee",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
