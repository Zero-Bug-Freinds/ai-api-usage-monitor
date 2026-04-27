import { describe, expect, it } from 'vitest';
import { buildBillingBudgetInAppDedupeKey } from './billing-dedupe-keys';
import type { BillingBudgetThresholdReachedEventPayload } from './billing-budget-threshold-event.schema';

describe('buildBillingBudgetInAppDedupeKey', () => {
  it('uses yyyyMM and thresholdPct for stability', () => {
    const payload = {
      schemaVersion: 1,
      occurredAt: '2026-04-27T00:00:00.000Z',
      monthStart: '2026-04-01',
      thresholdPct: 0.8,
      monthlyTotalUsd: 80,
      monthlyBudgetUsd: 100,
    } as BillingBudgetThresholdReachedEventPayload;

    const key = buildBillingBudgetInAppDedupeKey({
      subjectType: 'USER',
      userId: 'u',
      payload,
    });

    expect(key).toContain('billing:budget:USER:u');
    expect(key).toContain(':202604:');
    expect(key).toContain(':0.8');
  });
});

