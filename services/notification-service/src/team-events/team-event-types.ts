/** Mirrors `TeamEventTypes` in team-service (Java). */
export const TEAM_EVENT_TYPES = [
  'TEAM_CREATED',
  'TEAM_INVITE_CREATED',
  'TEAM_MEMBER_JOINED',
  'TEAM_INVITATION_ACCEPTED',
  'TEAM_INVITATION_REJECTED',
  'TEAM_MEMBER_REMOVED',
  'TEAM_DELETED',
  'TEAM_API_KEY_REGISTERED',
  'TEAM_API_KEY_UPDATED',
  'TEAM_API_KEY_DELETED',
  'TEAM_API_KEY_DELETION_SCHEDULED',
  'TEAM_API_KEY_DELETION_CANCELLED',
] as const;

export type TeamEventType = (typeof TEAM_EVENT_TYPES)[number];

export function isTeamEventType(value: string): value is TeamEventType {
  return (TEAM_EVENT_TYPES as readonly string[]).includes(value);
}
