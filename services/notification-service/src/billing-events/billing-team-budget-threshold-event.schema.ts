import { z } from 'zod';

export type BillingTeamBudgetThresholdReachedEventPayload = z.infer<
  typeof billingTeamBudgetThresholdReachedEventSchema
>;

const billingTeamBudgetThresholdReachedEventSchema = z.object({
  schemaVersion: z.number().int(),
  occurredAt: z.union([z.string(), z.coerce.date()]),
  teamId: z.number().int().nonnegative(),
  triggerUserId: z.string(),
  teamApiKeyId: z.number().int().nonnegative(),
  provider: z.string(),
  apiKeyAlias: z.string(),
  monthStart: z.string(), // YYYY-MM-01
  thresholdPct: z.number(),
  monthlyTotalUsd: z.number(),
  monthlyBudgetUsd: z.number(),
});

export function safeParseBillingTeamBudgetThresholdReachedEventJson(raw: unknown) {
  return billingTeamBudgetThresholdReachedEventSchema.safeParse(raw);
}

