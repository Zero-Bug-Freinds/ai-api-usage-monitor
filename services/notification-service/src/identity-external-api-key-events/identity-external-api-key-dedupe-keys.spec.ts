import { describe, expect, it } from 'vitest';
import {
  buildExternalApiKeyDeletedInAppDedupeKey,
  buildExternalApiKeyStatusInAppDedupeKey,
} from './identity-external-api-key-dedupe-keys';

describe('identity external API key dedupe keys', () => {
  it('builds stable deleted dedupe key', () => {
    const key = buildExternalApiKeyDeletedInAppDedupeKey({
      eventType: 'EXTERNAL_API_KEY_DELETED',
      userId: 'u@x.com',
      apiKeyId: 10,
      occurredAt: '2026-01-02T00:00:00.000Z',
      retainLogs: false,
      provider: null,
      alias: null,
    });
    expect(key).toBe(
      'in-app:identity:ext-key:deleted:10:u@x.com:2026-01-02T00:00:00.000Z',
    );
  });

  it('returns null when userId missing for deleted', () => {
    const key = buildExternalApiKeyDeletedInAppDedupeKey({
      eventType: 'EXTERNAL_API_KEY_DELETED',
      userId: '   ',
      apiKeyId: 1,
      occurredAt: '2026-01-02T00:00:00.000Z',
      retainLogs: false,
      provider: null,
      alias: null,
    });
    expect(key).toBeNull();
  });

  it('builds status dedupe key with status segment', () => {
    const key = buildExternalApiKeyStatusInAppDedupeKey({
      schemaVersion: 2,
      occurredAt: '2026-03-01T00:00:00.000Z',
      keyId: 5,
      userId: 'user@test.dev',
      provider: 'openai',
      status: 'DELETION_REQUESTED',
    });
    expect(key).toContain('in-app:identity:ext-key:status:DELETION_REQUESTED:5:user@test.dev');
  });
});
