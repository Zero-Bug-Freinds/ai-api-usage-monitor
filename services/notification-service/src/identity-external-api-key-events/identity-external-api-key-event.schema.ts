import { z } from 'zod';
import { IdentityExternalApiKeyEventTypes } from './identity-external-api-key-event-types';

const instantLike = z.union([z.string(), z.coerce.date()]);

/** Matches agent-service `IdentityExternalApiKeyEventListener` budget branch. */
export function isIdentityExternalApiKeyBudgetEventType(eventType: string): boolean {
  return (
    IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_BUDGET_CHANGED === eventType ||
    'ExternalApiKeyBudgetChangedEvent' === eventType
  );
}

const externalApiKeyStatusEnum = z.enum(['ACTIVE', 'DELETION_REQUESTED', 'DELETED']);

const externalApiKeyDeletedEventSchema = z
  .object({
    eventType: z.literal(IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED),
    userId: z.string(),
    apiKeyId: z.union([z.number(), z.string(), z.bigint()]).optional(),
    keyId: z.union([z.number(), z.string(), z.bigint()]).optional(),
    occurredAt: instantLike,
    retainLogs: z.boolean(),
    provider: z.string().nullable().optional(),
    alias: z.string().nullable().optional(),
  })
  .passthrough()
  .transform((o) => {
    const raw = o.apiKeyId ?? o.keyId;
    let apiKeyId = NaN;
    if (raw !== undefined && raw !== null) {
      apiKeyId =
        typeof raw === 'bigint' ? Number(raw) : typeof raw === 'string' ? Number(raw) : raw;
    }
    return {
      eventType: o.eventType,
      userId: o.userId,
      apiKeyId,
      occurredAt: o.occurredAt,
      retainLogs: o.retainLogs,
      provider: o.provider ?? null,
      alias: o.alias ?? null,
    };
  })
  .refine((o) => Number.isFinite(o.apiKeyId), {
    message: 'apiKeyId or keyId is required and must be numeric',
  });

export type ExternalApiKeyDeletedEventPayload = z.infer<typeof externalApiKeyDeletedEventSchema>;

export const externalApiKeyStatusChangedEventSchema = z
  .object({
    schemaVersion: z.number().int(),
    occurredAt: instantLike,
    keyId: z.union([z.number(), z.string(), z.bigint()]).transform((v) => {
      if (typeof v === 'bigint') return Number(v);
      if (typeof v === 'string') return Number(v);
      return v;
    }),
    alias: z.string().nullable().optional(),
    userId: z.string(),
    visibility: z.string().optional(),
    provider: z.string(),
    status: externalApiKeyStatusEnum,
    keyHash: z.string().nullable().optional(),
  })
  .passthrough();

export type ExternalApiKeyStatusChangedEventPayload = z.infer<
  typeof externalApiKeyStatusChangedEventSchema
>;

export function safeParseExternalApiKeyDeletedJson(
  raw: unknown,
): z.ZodSafeParseResult<ExternalApiKeyDeletedEventPayload> {
  return externalApiKeyDeletedEventSchema.safeParse(raw);
}

export function safeParseExternalApiKeyStatusChangedJson(
  raw: unknown,
): z.ZodSafeParseResult<ExternalApiKeyStatusChangedEventPayload> {
  return externalApiKeyStatusChangedEventSchema.safeParse(raw);
}
