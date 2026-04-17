import { describe, expect, it } from 'vitest';
import { parseTeamDomainEventJson, safeParseTeamDomainEventJson } from './team-domain-event.schema';

describe('parseTeamDomainEventJson', () => {
  it('parses a minimal TEAM_CREATED payload', () => {
    const raw = {
      eventType: 'TEAM_CREATED',
      teamId: '1',
      teamName: 'Acme',
      actorUserId: 'u-owner',
      occurredAt: '2026-01-01T00:00:00.000Z',
      recipientUserIds: ['u-owner'],
    };
    const parsed = parseTeamDomainEventJson(raw);
    expect(parsed.eventType).toBe('TEAM_CREATED');
    expect(parsed.recipientUserIds).toEqual(['u-owner']);
  });

  it('rejects unknown eventType', () => {
    const raw = {
      eventType: 'UNKNOWN',
      teamId: '1',
      actorUserId: 'u',
      occurredAt: '2026-01-01T00:00:00.000Z',
      recipientUserIds: [],
    };
    const parsed = safeParseTeamDomainEventJson(raw);
    expect(parsed.success).toBe(false);
  });
});
