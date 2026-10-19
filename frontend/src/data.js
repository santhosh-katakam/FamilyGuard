export function usageRows(events = []) {
  const snapshots = new Map();
  for (const e of events) {
    const key = `${e.packageName}:${e.data.day}`;
    const prior = snapshots.get(key);
    if (!prior || new Date(e.occurredAt) > new Date(prior.occurredAt))
      snapshots.set(key, e);
  }
  return [...snapshots.values()];
}
export function localDay(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function minutes(ms) {
  return `${Math.floor(ms / 3600000)}h ${Math.floor((ms % 3600000) / 60000)}m`;
}
export function installed(events = []) {
  const latest = new Map();
  for (const e of events) {
    const old = latest.get(e.packageName);
    if (!old || new Date(e.occurredAt) > new Date(old.occurredAt))
      latest.set(e.packageName, e);
  }
  return [...latest.values()].filter((e) => e.kind !== "REMOVED");
}
export function stale(timestamp, now = Date.now()) {
  return !timestamp || now - new Date(timestamp).getTime() > 120000;
}
