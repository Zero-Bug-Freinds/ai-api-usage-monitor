import { z } from 'zod';

const instantLike = z.union([z.string(), z.coerce.date()]);

const nonNegativeFiniteNumber = z.coerce
  .number()
  .refine((v) => Number.isFinite(v), { message: 'Must be a finite number' })
  .refine((v) => v >= 0, { message: 'Must be >= 0' });

export type BillingBudgetThresholdReachedEventPayload = z.infer<
  typeof billingBudgetThresholdReachedEventSchema
>;

const billingBudgetThresholdReachedEventSchema = z
  .object({
    schemaVersion: z.coerce.number().int(),
    occurredAt: instantLike,
    monthStart: z.string(),
    thresholdPct: nonNegativeFiniteNumber,
    monthlyTotalUsd: nonNegativeFiniteNumber,
    monthlyBudgetUsd: nonNegativeFiniteNumber,
    apiKeyAlias: z.string().optional(),
  })
  .passthrough();

export function safeParseBillingBudgetThresholdReachedEventJson(
  raw: unknown,
): z.ZodSafeParseResult<BillingBudgetThresholdReachedEventPayload> {
  return billingBudgetThresholdReachedEventSchema.safeParse(raw);
}

