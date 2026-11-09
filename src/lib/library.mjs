export function channelKey(channel) {
  const id = channel.rawAttrs?.["tvg-id"];
  return id ? JSON.stringify([id, channel.name, channel.group || ""]) : channel.sourceUrl || channel.url;
}

export function seasonInfo(name = "") {
  const match = /(?:^|[^a-z0-9])(?:s(?:eason)?[ ._-]*(\d{1,3})(?:[ ._-]*e(?:p(?:isode)?)?[ ._-]*(\d{1,3}))?|(\d{1,3})x(\d{1,3}))(?![a-z0-9])/i.exec(name);
  if (!match) return null;
  const series = name.slice(0, match.index).replace(/[\s._-]+$/, "").replace(/[._]+/g, " ").replace(/\s+/g, " ").trim();
  if (!series) return null;
  return { series, season: Number(match[1] || match[3]), episode: match[2] || match[4] ? Number(match[2] || match[4]) : null };
}

export function episodeInfo(name = "") {
  const info = seasonInfo(name);
  return info && info.episode !== null ? info : null;
}

export function resumable(position, duration, ended = false) {
  return !ended && Number.isFinite(duration) && duration > 0 && position >= 5 &&
    position < duration - Math.min(30, duration * 0.02);
}

export function updateHistory(previous, channel, progress, now = Date.now()) {
  if (!Number.isFinite(progress.duration) || progress.duration <= 0 || !Number.isFinite(progress.position) || progress.position < 0 || (progress.position < 1 && !progress.reset)) return previous;
  const key = channelKey(channel);
  const record = { key, channel: { ...channel, url: channel.sourceUrl || channel.url },
    ...progress, updatedAt: now, episode: episodeInfo(channel.name) };
  delete record.channel.sourceUrl;
  delete record.reset;
  return Object.fromEntries(Object.entries({ ...previous, [key]: record })
    .sort((a, b) => b[1].updatedAt - a[1].updatedAt).slice(0, 200));
}

export function historyItems(history, channels) {
  const available = new Map(channels.map((channel) => [channelKey(channel), channel]));
  const series = new Set();
  return Object.values(history).sort((a, b) => b.updatedAt - a.updatedAt).flatMap((record) => {
    const channel = available.get(record.key);
    if (!channel) return [];
    if (record.episode) {
      const seriesKey = JSON.stringify([channel.group || "", record.episode.series.toLowerCase()]);
      if (series.has(seriesKey)) return [];
      series.add(seriesKey);
    }
    return [{ ...channel, history: record }];
  });
}

export function formatTime(seconds) {
  if (!Number.isFinite(seconds)) return "0:00";
  const total = Math.floor(Math.max(0, seconds));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor(total / 60) % 60;
  return hours ? `${hours}:${String(minutes).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`
    : `${minutes}:${String(total % 60).padStart(2, "0")}`;
}
