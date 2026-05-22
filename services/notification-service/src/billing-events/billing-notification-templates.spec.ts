import { describe, expect, it } from 'vitest';
import {
  buildBillingBudgetThresholdCopy,
  buildBillingTeamApiKeyBudgetThresholdCopy,
} from './billing-notification-templates';
import type { BillingBudgetThresholdReachedEventPayload } from './billing-budget-threshold-event.schema';

describe('buildBillingBudgetThresholdCopy', () => {
  it('renders Korean template', () => {
    const payload = {
      schemaVersion: 1,
      occurredAt: '2026-04-27T00:00:00.000Z',
      monthStart: '2026-04-01',
      thresholdPct: 0.8,
      monthlyTotalUsd: 80,
      monthlyBudgetUsd: 100,
    } as BillingBudgetThresholdReachedEventPayload;

    const copy = buildBillingBudgetThresholdCopy(
      { payload: { ...payload }, apiKeyId: '2', apiKeyAlias: 'Gemini 키 1' },
      'ko',
    );
    expect(copy.title).toBe('예산 임계치 도달');
    expect(copy.body).toContain('80%');
    expect(copy.body).toContain('API 키(Gemini 키 1)');
    expect(copy.body).toContain('예산');
  });
});

describe('buildBillingTeamApiKeyBudgetThresholdCopy', () => {
  it('renders Korean team API key budget template', () => {
    const copy = buildBillingTeamApiKeyBudgetThresholdCopy({
      teamName: 'Alpha',
      payload: {
        schemaVersion: 1,
        occurredAt: '2026-04-27T00:00:00.000Z',
        teamId: 1,
        triggerUserId: 'u1',
        teamApiKeyId: 9,
        provider: 'GOOGLE',
        apiKeyAlias: 'prod-key',
        monthStart: '2026-04-01',
        thresholdPct: 0.8,
        monthlyTotalUsd: 80,
        monthlyBudgetUsd: 100,
      },
    });
    expect(copy.title).toBe('팀 API 키 예산 임계치 도달');
    expect(copy.body).toBe(
      '팀 Alpha의 GOOGLE API 키(prod-key) 사용량이 월 예산의 80%를 넘었습니다.',
    );
  });
});

