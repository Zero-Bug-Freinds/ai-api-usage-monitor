import { describe, expect, it } from 'vitest';
import { getEffectiveRecipientUserIds } from './team-recipient-user-ids';
import type { TeamDomainEventPayload } from './team-domain-event.schema';

describe('getEffectiveRecipientUserIds', () => {
  it('for TEAM_MEMBER_JOINED only notifies receiverId (inviter is not duplicated vs invitation accepted)', () => {
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

    expect(getEffectiveRecipientUserIds('TEAM_MEMBER_JOINED', payload)).toEqual([
      'joiner',
    ]);
  });

  it('passes through recipientUserIds for TEAM_INVITATION_ACCEPTED', () => {
    const payload = {
      eventType: 'TEAM_INVITATION_ACCEPTED',
      teamId: '1',
      teamName: 'Acme',
      actorUserId: 'accepter',
      occurredAt: '2026-01-01T00:00:00.000Z',
      recipientUserIds: ['inviter'],
      invitationId: 'inv-1',
    } as TeamDomainEventPayload;

    expect(getEffectiveRecipientUserIds('TEAM_INVITATION_ACCEPTED', payload)).toEqual([
      'inviter',
    ]);
  });
});
