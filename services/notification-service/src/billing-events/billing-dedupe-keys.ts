import type { BillingBudgetThresholdReachedEventPayload } from './billing-budget-threshold-event.schema';
import { hasText, thresholdKeyPart, toYyyyMm } from './billing-dedupe-key-parts';

const IN_APP_CHANNEL_SCOPE = 'in-app';

export type BillingSubjectType = 'USER' | 'TEAM' | 'API_KEY';

export function buildBillingBudgetInAppDedupeKey(params: {
  subjectType: BillingSubjectType;
  userId?: string;
  teamId?: string;
  apiKeyId?: string;
  payload: BillingBudgetThresholdReachedEventPayload;
}): string | null {
  const subjectId = resolveSubjectId(params);
  if (!subjectId) return null;

  const yyyyMM = toYyyyMm(params.payload.monthStart);
  if (!yyyyMM) return null;

  const threshold = thresholdKeyPart(params.payload.thresholdPct);

  return `${IN_APP_CHANNEL_SCOPE}:billing:budget:${params.subjectType}:${subjectId}:${yyyyMM}:${threshold}`;
}

function resolveSubjectId(params: {
  subjectType: BillingSubjectType;
  userId?: string;
  teamId?: string;
  apiKeyId?: string;
}): string | null {
  switch (params.subjectType) {
    case 'API_KEY':
      return hasText(params.apiKeyId) ? params.apiKeyId : null;
    case 'TEAM':
      return hasText(params.teamId) ? params.teamId : null;
    case 'USER':
      return hasText(params.userId) ? params.userId : null;
  }
}
