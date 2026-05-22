export function toYyyyMm(monthStart: string): string | null {
  const m = monthStart.match(/^(\d{4})-(\d{2})-\d{2}$/);
  if (!m) return null;
  return `${m[1]}${m[2]}`;
}

export function thresholdKeyPart(thresholdPct: number): string {
  if (!Number.isFinite(thresholdPct)) return 'NaN';
  const pct = Math.round(thresholdPct * 100);
  if (!Number.isFinite(pct) || pct < 0) return 'NaN';
  return `pct${pct}`;
}

export function hasText(value: string | undefined): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
