import { describe, expect, it } from 'vitest';
import { buildInAppDedupeKey } from './team-dedupe-keys';
import type { TeamDomainEventPayload } from './team-domain-event.schema';

describe('buildInAppDedupeKey', () => {
  it('builds per-invitation keys for invite flows', () => {
    const payload = {
      eventType: 'TEAM_INVITATION_ACCEPTED',
      teamId: '1',
      teamName: 'Acme',
      actorUserId: 'accepter',
      occurredAt: '2026-01-01T00:00:00.000Z',
      recipientUserIds: ['inviter'],
      invitationId: 'inv-42',
    } as TeamDomainEventPayload;

    const key = buildInAppDedupeKey(
      'TEAM_INVITATION_ACCEPTED',
      payload,
      'inviter',
    );
    expect(key).toContain('inv-42');
    expect(key).toContain('inviter');
  });

  it('scopes TEAM_MEMBER_JOINED by team and receiver', () => {
    const payload = {
      eventType: 'TEAM_MEMBER_JOINED',
      teamId: '7',
      teamName: 'Acme',
      actorUserId: 'joiner',
      occurredAt: '2026-01-01T00:00:00.000Z',
      recipientUserIds: ['inviter', 'joiner'],
      receiverId: 'joiner',
      inviterId: 'inviter',
    } as TeamDomainEventPayload;

    const key = buildInAppDedupeKey('TEAM_MEMBER_JOINED', payload, 'joiner');
    expect(key).toContain('TEAM_MEMBER_JOINED');
    expect(key).toContain('7');
    expect(key).toContain('joiner');
  });
});
