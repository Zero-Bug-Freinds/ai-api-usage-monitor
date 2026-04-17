import { describe, expect, it } from 'vitest';
import { buildTeamNotificationCopy } from './team-notification-templates';
import type { TeamDomainEventPayload } from './team-domain-event.schema';

describe('buildTeamNotificationCopy', () => {
  it('renders TEAM_INVITE_CREATED in English', () => {
    const payload = {
      eventType: 'TEAM_INVITE_CREATED',
      teamId: '1',
      teamName: 'Acme',
      actorUserId: 'inviter',
      occurredAt: '2026-01-01T00:00:00.000Z',
      recipientUserIds: ['invitee'],
      invitationId: 'inv-1',
      receiverId: 'invitee',
      inviterId: 'inviter',
    } as TeamDomainEventPayload;

    const copy = buildTeamNotificationCopy(
      'TEAM_INVITE_CREATED',
      payload,
      'invitee',
      'en',
    );
    expect(copy.title).toBe('Team invitation');
    expect(copy.body).toContain('Acme');
  });

  it('renders TEAM_MEMBER_JOINED in Korean', () => {
    const payload = {
      eventType: 'TEAM_MEMBER_JOINED',
      teamId: '1',
      teamName: 'Acme',
      actorUserId: 'joiner',
      occurredAt: '2026-01-01T00:00:00.000Z',
      recipientUserIds: ['inviter', 'joiner'],
      receiverId: 'joiner',
      inviterId: 'inviter',
    } as TeamDomainEventPayload;

    const copy = buildTeamNotificationCopy(
      'TEAM_MEMBER_JOINED',
      payload,
      'joiner',
      'ko',
    );
    expect(copy.title).toBe('팀 참여');
    expect(copy.body).toContain('Acme');
  });
});
