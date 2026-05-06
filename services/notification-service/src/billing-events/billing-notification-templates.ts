import type { BillingBudgetThresholdReachedEventPayload } from './billing-budget-threshold-event.schema';

export interface BillingNotificationCopy {
  title: string;
  body: string;
}

export function buildBillingBudgetThresholdCopy(
  params: {
    payload: BillingBudgetThresholdReachedEventPayload;
    apiKeyId?: string;
    apiKeyAlias?: string;
  },
  locale: 'ko' | 'en',
): BillingNotificationCopy {
  const thresholdPct = Math.round(params.payload.thresholdPct * 100);
  const total = formatUsd(params.payload.monthlyTotalUsd);
  const budget = formatUsd(params.payload.monthlyBudgetUsd);
  const alias = params.apiKeyAlias?.trim();
  const keyLabel = alias
    ? `API 키(${alias})`
    : params.apiKeyId?.trim()
      ? `API 키(${params.apiKeyId.trim()})`
      : 'API 키';

  if (locale === 'ko') {
    return {
      title: '예산 임계치 도달',
      body: `${keyLabel}의 이번 달 지출이 월 예산의 ${thresholdPct}%를 넘었습니다 (총 ${total} / 예산 ${budget}).`,
    };
  }

  return {
    title: 'Budget threshold reached',
    body: `This month’s spend for ${keyLabel} exceeded ${thresholdPct}% of your monthly budget (total ${total} / budget ${budget}).`,
  };
}

function formatUsd(value: number): string {
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `$${value.toFixed(2)}`;
  }
}

