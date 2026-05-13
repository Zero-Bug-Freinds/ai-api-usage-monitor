import { describe, expect, it } from 'vitest';
import {
  isIdentityExternalApiKeyBudgetEventType,
  safeParseExternalApiKeyDeletedJson,
  safeParseExternalApiKeyStatusChangedJson,
} from './identity-external-api-key-event.schema';

describe('identity external API key event schema', () => {
  it('parses deleted event using apiKeyId', () => {
    const raw = {
      eventType: 'EXTERNAL_API_KEY_DELETED',
      userId: 'user@example.com',
      apiKeyId: 42,
      occurredAt: '2026-05-01T12:00:00.000Z',
      retainLogs: true,
      provider: 'openai',
      alias: 'My key',
    };
    const r = safeParseExternalApiKeyDeletedJson(raw);
    expect(r.success).toBe(true);
    if (r.success) {
      expect(r.data.apiKeyId).toBe(42);
      expect(r.data.userId).toBe('user@example.com');
    }
  });

  it('parses deleted event using keyId (usage/agent compatibility)', () => {
    const raw = {
      eventType: 'EXTERNAL_API_KEY_DELETED',
      userId: 'user@example.com',
      keyId: 99,
      occurredAt: '2026-05-01T12:00:00.000Z',
      retainLogs: false,
    };
    const r = safeParseExternalApiKeyDeletedJson(raw);
    expect(r.success).toBe(true);
    if (r.success) {
      expect(r.data.apiKeyId).toBe(99);
    }
  });

  it('rejects deleted event without apiKeyId or keyId', () => {
    const raw = {
      eventType: 'EXTERNAL_API_KEY_DELETED',
      userId: 'u',
      occurredAt: '2026-05-01T12:00:00.000Z',
      retainLogs: false,
    };
    const r = safeParseExternalApiKeyDeletedJson(raw);
    expect(r.success).toBe(false);
  });

  it('parses status changed event', () => {
    const raw = {
      schemaVersion: 2,
      occurredAt: '2026-05-01T12:00:00.000Z',
      keyId: 7,
      alias: 'k',
      userId: 'a@b.com',
      visibility: 'PRIVATE',
      provider: 'anthropic',
      status: 'ACTIVE',
      keyHash: 'h',
    };
    const r = safeParseExternalApiKeyStatusChangedJson(raw);
    expect(r.success).toBe(true);
    if (r.success) {
      expect(r.data.keyId).toBe(7);
      expect(r.data.status).toBe('ACTIVE');
    }
  });

  it('rejects invalid status string', () => {
    const raw = {
      schemaVersion: 2,
      occurredAt: '2026-05-01T12:00:00.000Z',
      keyId: 1,
      userId: 'a@b.com',
      provider: 'p',
      status: 'UNKNOWN',
    };
    const r = safeParseExternalApiKeyStatusChangedJson(raw);
    expect(r.success).toBe(false);
  });

  it('detects budget event types', () => {
    expect(isIdentityExternalApiKeyBudgetEventType('EXTERNAL_API_KEY_BUDGET_CHANGED')).toBe(true);
    expect(isIdentityExternalApiKeyBudgetEventType('ExternalApiKeyBudgetChangedEvent')).toBe(true);
    expect(isIdentityExternalApiKeyBudgetEventType('EXTERNAL_API_KEY_DELETED')).toBe(false);
  });
});
