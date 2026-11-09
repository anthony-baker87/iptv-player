import { seasonInfo } from "./library.mjs";

const alphabetical = new Intl.Collator(undefined, { numeric: true, sensitivity: "base" });
const normalize = (text) => text.toLowerCase().replace(/[._-]+/g, " ").replace(/\s+/g, " ").trim();

export function organizeSearch(channels, query) {
  if (!query.trim()) return { count: channels.length, items: channels.map((channel) => ({ type: "channel", channel })) };
  const terms = normalize(query).split(" ").filter(Boolean);
  const filtered = channels.filter((channel) => terms.every((term) => normalize(channel.name).includes(term)));
  const items = [];
  const groups = new Map();
  const seriesNames = new Set();
  for (const channel of filtered) {
    const info = seasonInfo(channel.name);
    if (!info) { items.push({ type: "channel", channel, sortName: channel.name }); continue; }
    const seriesKey = normalize(info.series);
    const key = JSON.stringify([seriesKey, info.season]);
    seriesNames.add(seriesKey);
    if (!groups.has(key)) groups.set(key, { type: "season", key, series: info.series, season: info.season, channels: [], sortName: info.series });
    groups.get(key).channels.push({ channel, episode: info.episode });
  }
  for (const group of groups.values()) {
    group.channels.sort((a, b) => (a.episode ?? Infinity) - (b.episode ?? Infinity) || alphabetical.compare(a.channel.name, b.channel.name));
    group.channels = group.channels.map((item) => item.channel);
    group.label = `${seriesNames.size > 1 ? `${group.series} · ` : ""}Season ${group.season}`;
    items.push(group);
  }
  items.sort((a, b) => alphabetical.compare(a.sortName, b.sortName) || (a.season ?? -1) - (b.season ?? -1));
  return { count: filtered.length, items };
}

export function browseRows(result, expanded) {
  return result.items.flatMap((item) => item.type === "season" && expanded.has(item.key)
    ? [item, ...item.channels.map((channel) => ({ type: "channel", channel, seasonKey: item.key }))] : [item]);
}
