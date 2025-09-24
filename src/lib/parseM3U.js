export function parseM3U(text) {
  const lines = text.split(/\r?\n/);
  const channels = [];
  let pending = null;

  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;

    if (line.startsWith("#EXTINF")) {
      const attrs = {};
      for (const m of line.matchAll(/([a-zA-Z0-9-]+)="(.*?)"/g)) {
        attrs[m[1]] = m[2];
      }
      const name = (line.split(",").pop() || "").trim();
      pending = {
        name: name || attrs["tvg-name"] || "Unknown",
        logo: attrs["tvg-logo"] || null,
        group: attrs["group-title"] || "Other",
        rawAttrs: attrs,
      };
    } else if (!line.startsWith("#") && pending) {
      pending.url = line;
      channels.push(pending);
      pending = null;
    }
  }
  return channels;
}
