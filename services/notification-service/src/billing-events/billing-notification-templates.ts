import type { BillingBudgetThresholdReachedEventPayload } from './billing-budget-threshold-event.schema';

export interface BillingNotificationCopy {
  title: string;
  body: string;
}

export function buildBillingBudgetThresholdCopy(
  payload: BillingBudgetThresholdReachedEventPayload,
  locale: 'ko' | 'en',
): BillingNotificationCopy {
  const thresholdPct = Math.round(payload.thresholdPct * 100);
  const total = formatUsd(payload.monthlyTotalUsd);
  const budget = formatUsd(payload.monthlyBudgetUsd);

  if (locale === 'ko') {
    return {
      title: '예산 임계치 도달',
      body: `이번 달 지출이 월 예산의 ${thresholdPct}%를 넘었습니다 (총 ${total} / 예산 ${budget}).`,
    };
  }

  return {
    title: 'Budget threshold reached',
    body: `This month’s spend exceeded ${thresholdPct}% of your monthly budget (total ${total} / budget ${budget}).`,
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

