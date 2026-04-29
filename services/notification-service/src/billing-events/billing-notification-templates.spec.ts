import { describe, expect, it } from 'vitest';
import { buildBillingBudgetThresholdCopy } from './billing-notification-templates';
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

    const copy = buildBillingBudgetThresholdCopy(payload, 'ko');
    expect(copy.title).toBe('예산 임계치 도달');
    expect(copy.body).toContain('80%');
    expect(copy.body).toContain('예산');
  });
});

