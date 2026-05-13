import type { ExternalApiKeyDeletedEventPayload } from './identity-external-api-key-event.schema';
import type { ExternalApiKeyStatusChangedEventPayload } from './identity-external-api-key-event.schema';

export type IdentityExternalApiKeyNotificationLocale = 'en' | 'ko';

export interface IdentityExternalApiKeyNotificationCopy {
  title: string;
  body: string;
}

function formatKeyLabel(alias: string | null | undefined, keyId: number): string {
  const a = alias?.trim();
  if (a) return `"${a}"`;
  return `#${String(keyId)}`;
}

function formatProvider(provider: string | null | undefined): string {
  const p = provider?.trim();
  return p || '—';
}

/** Physical delete (`ExternalApiKeyDeletedEvent`). */
export function buildExternalApiKeyDeletedCopy(
  payload: ExternalApiKeyDeletedEventPayload,
  locale: IdentityExternalApiKeyNotificationLocale,
): IdentityExternalApiKeyNotificationCopy {
  const id = payload.apiKeyId ?? 0;
  const label = formatKeyLabel(payload.alias ?? undefined, id);
  const prov = formatProvider(payload.provider ?? undefined);

  if (locale === 'ko') {
    return {
      title: '외부 API 키 삭제됨',
      body: `개인 외부 API 키 ${label}(${prov})가 영구 삭제되었습니다.`,
    };
  }
  return {
    title: 'External API key removed',
    body: `Your external API key ${label} (${prov}) was permanently deleted.`,
  };
}

/** Lifecycle status from `ExternalApiKeyStatusChangedEvent`. */
export function buildExternalApiKeyStatusChangedCopy(
  payload: ExternalApiKeyStatusChangedEventPayload,
  locale: IdentityExternalApiKeyNotificationLocale,
): IdentityExternalApiKeyNotificationCopy {
  const label = formatKeyLabel(payload.alias ?? undefined, payload.keyId);
  const prov = formatProvider(payload.provider);

  if (locale === 'ko') {
    switch (payload.status) {
      case 'ACTIVE':
        return {
          title: '외부 API 키 등록·복구',
          body: `개인 외부 API 키 ${label}(${prov})가 활성 상태입니다.`,
        };
      case 'DELETION_REQUESTED':
        return {
          title: '외부 API 키 삭제 예약',
          body: `개인 외부 API 키 ${label}(${prov})의 삭제가 예약되었습니다.`,
        };
      case 'DELETED':
        return {
          title: '외부 API 키 비활성(삭제 처리)',
          body: `개인 외부 API 키 ${label}(${prov})가 삭제 처리되었습니다. (물리 삭제는 별도 알림이 올 수 있습니다.)`,
        };
    }
  }

  switch (payload.status) {
    case 'ACTIVE':
      return {
        title: 'External API key active',
        body: `Your external API key ${label} (${prov}) is active.`,
      };
    case 'DELETION_REQUESTED':
      return {
        title: 'External API key deletion scheduled',
        body: `Deletion was scheduled for your external API key ${label} (${prov}).`,
      };
    case 'DELETED':
      return {
        title: 'External API key marked deleted',
        body: `Your external API key ${label} (${prov}) was marked deleted. A separate notice may follow when it is purged.`,
      };
  }
}
