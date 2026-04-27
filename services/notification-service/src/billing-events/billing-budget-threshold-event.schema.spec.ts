import { describe, expect, it } from 'vitest';
import { safeParseBillingBudgetThresholdReachedEventJson } from './billing-budget-threshold-event.schema';

describe('safeParseBillingBudgetThresholdReachedEventJson', () => {
  it('accepts numeric fields', () => {
    const parsed = safeParseBillingBudgetThresholdReachedEventJson({
      schemaVersion: 1,
      occurredAt: '2026-04-27T00:00:00.000Z',
      monthStart: '2026-04-01',
      thresholdPct: 0.8,
      monthlyTotalUsd: 80.12,
      monthlyBudgetUsd: 100,
      extraField: 'ok',
    });
    expect(parsed.success).toBe(true);
  });
});

