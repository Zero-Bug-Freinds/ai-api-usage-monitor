import { hasText, thresholdKeyPart, toYyyyMm } from './billing-dedupe-key-parts';

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
