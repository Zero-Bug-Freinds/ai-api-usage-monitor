import type { TeamDomainEventPayload } from './team-domain-event.schema';
import type { TeamEventType } from './team-event-types';

/**
 * Effective in-app recipients per product rules.
 *
 * `TEAM_MEMBER_JOINED` publishes `recipientUserIds` as [inviter, joined]. The inviter is already
 * notified by `TEAM_INVITATION_ACCEPTED`, so we only surface `TEAM_MEMBER_JOINED` to the joined
 * user (`receiverId`) to avoid duplicate in-app rows for the inviter.
 */
export function getEffectiveRecipientUserIds(
  eventType: TeamEventType,
  payload: TeamDomainEventPayload,
): string[] {
  if (eventType === 'TEAM_MEMBER_JOINED') {
    const receiver = payload.receiverId;
    if (receiver) return [receiver];
    return [];
  }
  return payload.recipientUserIds ?? [];
}
