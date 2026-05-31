import type { BillingBudgetThresholdReachedEventPayload } from './billing-budget-threshold-event.schema';
import type { BillingTeamBudgetThresholdReachedEventPayload } from './billing-team-budget-threshold-event.schema';

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
  const total = formatUsd(params.payload.monthlyTotalUsd, locale);
  const budget = formatUsd(params.payload.monthlyBudgetUsd, locale);
  const alias = params.apiKeyAlias?.trim();
  const keyLabel =
    locale === 'ko'
      ? alias
        ? `API 키(${alias})`
        : params.apiKeyId?.trim()
          ? `API 키(${params.apiKeyId.trim()})`
          : 'API 키'
      : alias
        ? `API key (${alias})`
        : params.apiKeyId?.trim()
          ? `API key (${params.apiKeyId.trim()})`
          : 'API key';

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

export function buildBillingTeamApiKeyBudgetThresholdCopy(params: {
  teamName: string;
  payload: BillingTeamBudgetThresholdReachedEventPayload;
}): BillingNotificationCopy {
  const pct = Math.round(params.payload.thresholdPct * 100);
  return {
    title: '팀 API 키 예산 임계치 도달',
    body: `팀 ${params.teamName}의 ${params.payload.provider} API 키(${params.payload.apiKeyAlias}) 사용량이 월 예산의 ${pct}%를 넘었습니다.`,
  };
}

function formatUsd(value: number, locale: 'ko' | 'en'): string {
  const intlLocale = locale === 'ko' ? 'ko-KR' : 'en-US';
  try {
    return new Intl.NumberFormat(intlLocale, {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `$${value.toFixed(2)}`;
  }
}

