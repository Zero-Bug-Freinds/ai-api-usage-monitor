import type { TeamDomainEventPayload } from './team-domain-event.schema';
import type { TeamEventType } from './team-event-types';

const IN_APP_CHANNEL_SCOPE = 'in-app';

/**
 * Stable, retry-safe dedupe keys per recipient. Must stay aligned with team-service payloads.
 */
export function buildInAppDedupeKey(
  eventType: TeamEventType,
  payload: TeamDomainEventPayload,
  recipientUserId: string,
): string | null {
  const teamId = payload.teamId;
  switch (eventType) {
    case 'TEAM_CREATED':
      return `${IN_APP_CHANNEL_SCOPE}:team:${eventType}:${teamId}:${recipientUserId}`;
    case 'TEAM_INVITE_CREATED': {
      const id = payload.invitationId;
      if (!id) return null;
      return `${IN_APP_CHANNEL_SCOPE}:team:${eventType}:${id}:${recipientUserId}`;
    }
    case 'TEAM_MEMBER_JOINED': {
      const receiverId = payload.receiverId;
      if (!receiverId) return null;
      return `${IN_APP_CHANNEL_SCOPE}:team:${eventType}:${teamId}:${receiverId}:${recipientUserId}`;
    }
    case 'TEAM_INVITATION_ACCEPTED':
    case 'TEAM_INVITATION_REJECTED': {
      const id = payload.invitationId;
      if (!id) return null;
      return `${IN_APP_CHANNEL_SCOPE}:team:${eventType}:${id}:${recipientUserId}`;
    }
    case 'TEAM_MEMBER_REMOVED': {
      const removed = payload.removedUserId;
      if (!removed) return null;
      return `${IN_APP_CHANNEL_SCOPE}:team:${eventType}:${teamId}:${removed}:${recipientUserId}`;
    }
    case 'TEAM_DELETED':
      return `${IN_APP_CHANNEL_SCOPE}:team:${eventType}:${teamId}:${recipientUserId}`;
    case 'TEAM_API_KEY_REGISTERED':
    case 'TEAM_API_KEY_UPDATED':
    case 'TEAM_API_KEY_DELETED':
    case 'TEAM_API_KEY_DELETION_SCHEDULED':
    case 'TEAM_API_KEY_DELETION_CANCELLED': {
      const keyId = payload.apiKeyId;
      if (keyId === undefined || keyId === null) return null;
      return `${IN_APP_CHANNEL_SCOPE}:team:${eventType}:${teamId}:${String(keyId)}:${recipientUserId}`;
    }
    default:
      return null;
  }
}
