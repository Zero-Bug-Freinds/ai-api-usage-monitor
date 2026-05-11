const IN_APP_CHANNEL_SCOPE = 'in-app';

export function buildBillingTeamBudgetInAppDedupeKey(params: {
  teamId: number;
  teamApiKeyId: number;
  targetUserId: string;
  monthStart: string;
  thresholdPct: number;
}): string | null {
  if (!Number.isFinite(params.teamId) || params.teamId < 0) return null;
  if (!Number.isFinite(params.teamApiKeyId) || params.teamApiKeyId < 0) return null;
  if (!hasText(params.targetUserId)) return null;
  const yyyyMM = toYyyyMm(params.monthStart);
  if (!yyyyMM) return null;
  const threshold = thresholdKeyPart(params.thresholdPct);
  return `${IN_APP_CHANNEL_SCOPE}:billing:team-api-key-budget:${params.teamId}:${params.teamApiKeyId}:${params.targetUserId}:${yyyyMM}:${threshold}`;
}

function toYyyyMm(monthStart: string): string | null {
  const m = monthStart.match(/^(\d{4})-(\d{2})-\d{2}$/);
  if (!m) return null;
  return `${m[1]}${m[2]}`;
}

function thresholdKeyPart(thresholdPct: number): string {
  if (!Number.isFinite(thresholdPct)) return 'NaN';
  const pct = Math.round(thresholdPct * 100);
  if (!Number.isFinite(pct) || pct < 0) return 'NaN';
  return `pct${pct}`;
}

function hasText(value: string | undefined): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

