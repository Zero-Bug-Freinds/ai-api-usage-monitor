import type { ExternalApiKeyDeletedEventPayload } from './identity-external-api-key-event.schema';
import type { ExternalApiKeyStatusChangedEventPayload } from './identity-external-api-key-event.schema';

const IN_APP_SCOPE = 'in-app:identity:ext-key';

function occurredAtToIso(value: string | Date): string {
  if (typeof value === 'string') return value;
  return value.toISOString();
}

export function buildExternalApiKeyDeletedInAppDedupeKey(
  payload: ExternalApiKeyDeletedEventPayload,
): string | null {
  const userId = payload.userId?.trim();
  if (!userId || payload.apiKeyId === undefined || payload.apiKeyId === null) {
    return null;
  }
  const at = occurredAtToIso(payload.occurredAt as string | Date);
  return `${IN_APP_SCOPE}:deleted:${String(payload.apiKeyId)}:${userId}:${at}`;
}

export function buildExternalApiKeyStatusInAppDedupeKey(
  payload: ExternalApiKeyStatusChangedEventPayload,
): string | null {
  const userId = payload.userId?.trim();
  if (!userId || payload.keyId === undefined || payload.keyId === null || Number.isNaN(payload.keyId)) {
    return null;
  }
  const at = occurredAtToIso(payload.occurredAt as string | Date);
  return `${IN_APP_SCOPE}:status:${payload.status}:${String(payload.keyId)}:${userId}:${at}`;
}
