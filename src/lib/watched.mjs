import { channelKey, resumable } from "./library.mjs";

export function watchStatesFromHistory(history) {
  return Object.fromEntries(Object.values(history).filter((entry) => entry.ended || entry.position >= 5)
    .map((entry) => [entry.key, { status: entry.ended ? "watched" : "started", updatedAt: entry.updatedAt }]));
}

export function validWatchStates(value) {
  return value && typeof value === "object" && !Array.isArray(value) && Object.values(value).every((entry) => entry &&
    ["watched", "started", "unwatched"].includes(entry.status) && Number.isFinite(entry.updatedAt));
}

export function recordWatch(previous, channel, progress, now = Date.now()) {
  if (!Number.isFinite(progress.duration) || progress.duration <= 0 || !Number.isFinite(progress.position) || progress.position < 5 && !progress.ended) return previous;
  const key = channelKey(channel);
  if (previous[key]?.status === "watched") return previous;
  const complete = progress.ended || progress.position > progress.duration / 2 && !resumable(progress.position, progress.duration);
  const status = complete ? "watched" : "started";
  return previous[key]?.status === status ? previous : { ...previous, [key]: { status, updatedAt: now } };
}

export function setWatchStatus(previous, channel, watched, now = Date.now()) {
  return { ...previous, [channelKey(channel)]: { status: watched ? "watched" : "unwatched", updatedAt: now } };
}
