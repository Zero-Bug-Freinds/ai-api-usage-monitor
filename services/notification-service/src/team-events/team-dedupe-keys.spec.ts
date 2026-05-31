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

  const apiKeyUpdatedBase = {
    eventType: 'TEAM_API_KEY_UPDATED',
    teamId: '3',
    teamName: 'Acme',
    actorUserId: 'actor-1',
    recipientUserIds: ['user-a'],
    apiKeyId: 99,
    provider: 'OPENAI',
    alias: 'prod-key',
  } as TeamDomainEventPayload;

  it('scopes TEAM_API_KEY_UPDATED by occurredAt so repeated updates get distinct keys', () => {
    const first = buildInAppDedupeKey(
      'TEAM_API_KEY_UPDATED',
      { ...apiKeyUpdatedBase, occurredAt: '2026-05-31T04:00:00.000Z' } as TeamDomainEventPayload,
      'user-a',
    );
    const second = buildInAppDedupeKey(
      'TEAM_API_KEY_UPDATED',
      { ...apiKeyUpdatedBase, occurredAt: '2026-05-31T04:01:00.000Z' } as TeamDomainEventPayload,
      'user-a',
    );
    expect(first).not.toBeNull();
    expect(second).not.toBeNull();
    expect(first).not.toEqual(second);
    expect(first).toContain('2026-05-31T04:00:00.000Z');
    expect(second).toContain('2026-05-31T04:01:00.000Z');
  });

  it('dedupes TEAM_API_KEY_UPDATED when occurredAt is identical (MQ retry)', () => {
    const payload = {
      ...apiKeyUpdatedBase,
      occurredAt: '2026-05-31T04:00:00.000Z',
    } as TeamDomainEventPayload;
    const a = buildInAppDedupeKey('TEAM_API_KEY_UPDATED', payload, 'user-a');
    const b = buildInAppDedupeKey('TEAM_API_KEY_UPDATED', payload, 'user-a');
    expect(a).toBe(b);
  });

  it('returns null for TEAM_API_KEY_UPDATED without valid occurredAt', () => {
    expect(
      buildInAppDedupeKey(
        'TEAM_API_KEY_UPDATED',
        { ...apiKeyUpdatedBase, occurredAt: 'not-a-date' } as TeamDomainEventPayload,
        'user-a',
      ),
    ).toBeNull();
    expect(
      buildInAppDedupeKey(
        'TEAM_API_KEY_UPDATED',
        { ...apiKeyUpdatedBase, occurredAt: undefined } as TeamDomainEventPayload,
        'user-a',
      ),
    ).toBeNull();
  });

  it('keeps TEAM_API_KEY_REGISTERED dedupe per key without occurredAt', () => {
    const payload = {
      ...apiKeyUpdatedBase,
      eventType: 'TEAM_API_KEY_REGISTERED',
      occurredAt: '2026-05-31T04:00:00.000Z',
    } as TeamDomainEventPayload;
    const first = buildInAppDedupeKey('TEAM_API_KEY_REGISTERED', payload, 'user-a');
    const second = buildInAppDedupeKey(
      'TEAM_API_KEY_REGISTERED',
      { ...payload, occurredAt: '2026-05-31T05:00:00.000Z' } as TeamDomainEventPayload,
      'user-a',
    );
    expect(first).toBe(second);
    expect(first).not.toContain('2026-05-31');
  });
});
