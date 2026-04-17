import type { TeamDomainEventPayload } from './team-domain-event.schema';
import type { TeamEventType } from './team-event-types';

export type NotificationLocale = 'en' | 'ko';

export interface TeamNotificationCopy {
  title: string;
  body: string;
}

function teamLabel(payload: TeamDomainEventPayload): string {
  return payload.teamName?.trim() || payload.teamId;
}

function formatUserId(userId: string | undefined): string {
  if (!userId) return '—';
  return userId.length > 12 ? `${userId.slice(0, 8)}…` : userId;
}

/** Title/body for in-app notifications. Korean + English strings. */
export function buildTeamNotificationCopy(
  eventType: TeamEventType,
  payload: TeamDomainEventPayload,
  recipientUserId: string,
  locale: NotificationLocale,
): TeamNotificationCopy {
  const team = teamLabel(payload);
  const actor = formatUserId(payload.actorUserId);

  const en = (): TeamNotificationCopy => {
    switch (eventType) {
      case 'TEAM_CREATED':
        return {
          title: 'Team created',
          body: `You created team "${team}".`,
        };
      case 'TEAM_INVITE_CREATED':
        return {
          title: 'Team invitation',
          body: `You were invited to join "${team}".`,
        };
      case 'TEAM_MEMBER_JOINED':
        return {
          title: 'Joined team',
          body: `You joined "${team}".`,
        };
      case 'TEAM_INVITATION_ACCEPTED':
        return {
          title: 'Invitation accepted',
          body: `Your invitation to "${team}" was accepted.`,
        };
      case 'TEAM_INVITATION_REJECTED':
        return {
          title: 'Invitation declined',
          body: `Your invitation to "${team}" was declined.`,
        };
      case 'TEAM_MEMBER_REMOVED':
        return {
          title: 'Removed from team',
          body: `You were removed from "${team}".`,
        };
      case 'TEAM_DELETED':
        return {
          title: 'Team deleted',
          body: `Team "${team}" was deleted.`,
        };
      case 'TEAM_API_KEY_REGISTERED':
        return {
          title: 'API key registered',
          body: `An API key was registered for "${team}" (${formatProvider(payload)}).`,
        };
      case 'TEAM_API_KEY_UPDATED':
        return {
          title: 'API key updated',
          body: `An API key was updated for "${team}" (${formatProvider(payload)}).`,
        };
      case 'TEAM_API_KEY_DELETED':
        return {
          title: 'API key deleted',
          body: `An API key was deleted for "${team}" (${formatProvider(payload)}).`,
        };
      case 'TEAM_API_KEY_DELETION_SCHEDULED':
        return {
          title: 'API key deletion scheduled',
          body: `Deletion was scheduled for an API key on "${team}" (${formatProvider(payload)}).`,
        };
      case 'TEAM_API_KEY_DELETION_CANCELLED':
        return {
          title: 'API key deletion cancelled',
          body: `Scheduled deletion was cancelled for an API key on "${team}" (${formatProvider(payload)}).`,
        };
    }
  };

  const ko = (): TeamNotificationCopy => {
    switch (eventType) {
      case 'TEAM_CREATED':
        return {
          title: '팀 생성됨',
          body: `"${team}" 팀을 생성했습니다.`,
        };
      case 'TEAM_INVITE_CREATED':
        return {
          title: '팀 초대',
          body: `"${team}" 팀에 초대되었습니다.`,
        };
      case 'TEAM_MEMBER_JOINED':
        return {
          title: '팀 참여',
          body: `"${team}" 팀에 참여했습니다.`,
        };
      case 'TEAM_INVITATION_ACCEPTED':
        return {
          title: '초대 수락',
          body: `"${team}" 팀 초대가 수락되었습니다.`,
        };
      case 'TEAM_INVITATION_REJECTED':
        return {
          title: '초대 거절',
          body: `"${team}" 팀 초대가 거절되었습니다.`,
        };
      case 'TEAM_MEMBER_REMOVED':
        return {
          title: '팀에서 제외됨',
          body: `"${team}" 팀에서 제외되었습니다.`,
        };
      case 'TEAM_DELETED':
        return {
          title: '팀 삭제됨',
          body: `"${team}" 팀이 삭제되었습니다.`,
        };
      case 'TEAM_API_KEY_REGISTERED':
        return {
          title: 'API 키 등록',
          body: `"${team}" 팀에 API 키가 등록되었습니다 (${formatProviderKo(payload)}).`,
        };
      case 'TEAM_API_KEY_UPDATED':
        return {
          title: 'API 키 변경',
          body: `"${team}" 팀의 API 키가 변경되었습니다 (${formatProviderKo(payload)}).`,
        };
      case 'TEAM_API_KEY_DELETED':
        return {
          title: 'API 키 삭제',
          body: `"${team}" 팀의 API 키가 삭제되었습니다 (${formatProviderKo(payload)}).`,
        };
      case 'TEAM_API_KEY_DELETION_SCHEDULED':
        return {
          title: 'API 키 삭제 예약',
          body: `"${team}" 팀 API 키 삭제가 예약되었습니다 (${formatProviderKo(payload)}).`,
        };
      case 'TEAM_API_KEY_DELETION_CANCELLED':
        return {
          title: 'API 키 삭제 취소',
          body: `"${team}" 팀 API 키의 삭제 예약이 취소되었습니다 (${formatProviderKo(payload)}).`,
        };
    }
  };

  // `recipientUserId` reserved for future per-recipient phrasing (e.g. actor vs viewer).
  void recipientUserId;
  void actor;
  return locale === 'ko' ? ko() : en();
}

function formatProvider(payload: TeamDomainEventPayload): string {
  const p = payload.provider?.trim();
  const a = payload.alias?.trim();
  if (p && a) return `${p} / ${a}`;
  return p || a || 'provider';
}

function formatProviderKo(payload: TeamDomainEventPayload): string {
  const p = payload.provider?.trim();
  const a = payload.alias?.trim();
  if (p && a) return `${p} / ${a}`;
  return p || a || '제공자';
}
