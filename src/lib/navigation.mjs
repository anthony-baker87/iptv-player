export function originalStream(channel) {
  const source = { ...channel, url: channel.sourceUrl || channel.url };
  delete source.sourceUrl;
  delete source.history;
  delete source.proxied;
  return source;
}

// Player lifecycle events and disposable proxy addresses must not change Last.
export function rememberStream(previous, channel) {
  const active = originalStream(channel);
  if (previous.active?.url === active.url) return previous;
  return { active, previous: previous.active };
}
