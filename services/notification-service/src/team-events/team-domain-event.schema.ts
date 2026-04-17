import { z } from 'zod';
import { isTeamEventType, type TeamEventType } from './team-event-types';

const instantLike = z.union([
  z.string(),
  z.coerce.date(),
]);

const eventTypeField = z
  .string()
  .refine((v): v is TeamEventType => isTeamEventType(v), { message: 'Unknown eventType' });

/** Parsed payload after validation — mirrors team-service `TeamDomainOutboundEvent` JSON. */
export type TeamDomainEventPayload = z.infer<typeof teamDomainEventSchema>;

const teamDomainEventSchema = z
  .object({
    eventType: eventTypeField,
    teamId: z.string(),
    teamName: z.string().nullable().optional(),
    actorUserId: z.string(),
    occurredAt: instantLike,
    recipientUserIds: z.array(z.string()),
    invitationId: z.string().optional(),
    receiverId: z.string().optional(),
    inviterId: z.string().optional(),
    createdAt: instantLike.optional(),
    removedUserId: z.string().optional(),
    memberUserIdsSnapshot: z.array(z.string()).optional(),
    apiKeyId: z.union([z.number(), z.bigint()]).optional(),
    provider: z.string().optional(),
    alias: z.string().optional(),
    deletionGraceDays: z.number().int().optional(),
    permanentDeletionAt: instantLike.optional(),
  })
  .passthrough();

export function parseTeamDomainEventJson(raw: unknown): TeamDomainEventPayload {
  return teamDomainEventSchema.parse(raw);
}

export function safeParseTeamDomainEventJson(
  raw: unknown,
): z.SafeParseReturnType<unknown, TeamDomainEventPayload> {
  return teamDomainEventSchema.safeParse(raw);
}
