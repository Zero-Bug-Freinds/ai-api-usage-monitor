import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

const TEAM_INVITE_NOTIFICATION_TYPE = 'team:TEAM_INVITE_CREATED';

@Injectable()
export class InAppNotificationsService {
  constructor(private readonly prisma: PrismaService) {}

  async countUnreadByUserId(params: { userId: string; platformUserId?: string }) {
    const ids = resolveRecipientUserIds(params.userId, params.platformUserId);
    const unreadCount = await this.prisma.inAppNotification.count({
      where: { readAt: null, OR: ids.map((userId) => ({ userId })) },
    });

    return { unreadCount };
  }

  async listByUserId(params: {
    userId: string;
    platformUserId?: string;
    cursor?: string;
    limit: number;
  }) {
    const { userId, platformUserId, cursor, limit } = params;
    const ids = resolveRecipientUserIds(userId, platformUserId);

    const items = await this.prisma.inAppNotification.findMany({
      where: { OR: ids.map((id) => ({ userId: id })) },
      orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
      ...(cursor
        ? {
            cursor: { id: cursor },
            skip: 1,
          }
        : {}),
      take: limit,
      select: {
        id: true,
        createdAt: true,
        userId: true,
        title: true,
        body: true,
        readAt: true,
        type: true,
        meta: true,
      },
    });

    const nextCursor = items.length === limit ? items[items.length - 1]?.id : null;

    return { items, nextCursor };
  }

  async markRead(params: { userId: string; platformUserId?: string; id: string }) {
    const ids = resolveRecipientUserIds(params.userId, params.platformUserId);
    const existing = await this.prisma.inAppNotification.findUnique({
      where: { id: params.id },
      select: { id: true, userId: true, readAt: true, type: true, meta: true },
    });

    if (!existing) throw new NotFoundException('Notification not found');
    if (!ids.includes(existing.userId)) throw new NotFoundException('Notification not found');

    if (existing.readAt) return { updated: false };

    if (isPendingTeamInviteForReadGuard(existing.type, existing.meta)) {
      throw new BadRequestException(
        '팀 초대는 수락 또는 거절 후에 읽음 처리할 수 있습니다.',
      );
    }

    await this.prisma.inAppNotification.update({
      where: { id: params.id },
      data: { readAt: new Date() },
    });

    return { updated: true };
  }

  async markAllRead(params: { userId: string; platformUserId?: string }) {
    const ids = resolveRecipientUserIds(params.userId, params.platformUserId);
    const userOr = ids.map((userId) => ({ userId }));

    const pendingInviteRows = await this.prisma.inAppNotification.findMany({
      where: {
        readAt: null,
        OR: userOr,
        type: TEAM_INVITE_NOTIFICATION_TYPE,
      },
      select: { id: true, meta: true },
    });
    const pendingInviteIds = pendingInviteRows
      .filter((row) => !teamInviteActionedAtPresent(row.meta))
      .map((row) => row.id);

    const where: Prisma.InAppNotificationWhereInput = {
      readAt: null,
      OR: userOr,
      ...(pendingInviteIds.length > 0 ? { NOT: { id: { in: pendingInviteIds } } } : {}),
    };

    const result = await this.prisma.inAppNotification.updateMany({
      where,
      data: { readAt: new Date() },
    });

    return { updatedCount: result.count };
  }

  /**
   * After accept/reject, marks matching in-app rows read and records decision on meta
   * so mark-read guards clear for team invites.
   */
  async markTeamInviteNotificationsResolved(params: {
    userId: string;
    platformUserId?: string;
    invitationId: string;
    decision: 'ACCEPT' | 'REJECT';
  }): Promise<void> {
    const recipientIds = resolveRecipientUserIds(params.userId, params.platformUserId);
    const rows = await this.prisma.inAppNotification.findMany({
      where: {
        userId: { in: recipientIds },
        type: TEAM_INVITE_NOTIFICATION_TYPE,
        meta: { path: ['invitationId'], equals: params.invitationId },
      },
      select: { id: true, meta: true },
    });

    const now = new Date();
    const actionedAt = now.toISOString();

    await Promise.all(
      rows.map((row) => {
        const prevMeta = row.meta;
        const base =
          prevMeta !== null && typeof prevMeta === 'object' && !Array.isArray(prevMeta)
            ? { ...(prevMeta as Record<string, unknown>) }
            : {};
        const next = {
          ...base,
          actionedAt,
          decision: params.decision,
        } as Prisma.InputJsonObject;
        return this.prisma.inAppNotification.update({
          where: { id: row.id },
          data: { readAt: now, meta: next },
        });
      }),
    );
  }

  async testSend(params: {
    actorUserId?: string;
    isInternal: boolean;
    targetUserId: string;
    title: string;
    body: string;
    type?: string;
  }) {
    if (!params.isInternal && params.actorUserId !== params.targetUserId) {
      throw new ForbiddenException(
        'Without internal secret, targetUserId must match X-User-Id',
      );
    }

    const created = await this.prisma.inAppNotification.create({
      data: {
        userId: params.targetUserId,
        title: params.title,
        body: params.body,
        type: params.type ?? 'test',
      },
      select: {
        id: true,
        createdAt: true,
        userId: true,
        title: true,
        body: true,
        readAt: true,
        type: true,
        meta: true,
      },
    });

    return created;
  }
}

function resolveRecipientUserIds(userId: string, platformUserId?: string): string[] {
  const out: string[] = [];
  const a = userId?.trim();
  const b = platformUserId?.trim();
  if (a) out.push(a);
  if (b && b !== a) out.push(b);
  return out;
}

function teamInviteActionedAtPresent(meta: unknown): boolean {
  if (typeof meta !== 'object' || meta === null) return false;
  const v = (meta as Record<string, unknown>).actionedAt;
  if (v == null) return false;
  if (typeof v === 'string' && v.trim().length === 0) return false;
  return true;
}

function isPendingTeamInviteForReadGuard(type: string | null, meta: unknown): boolean {
  if (type !== TEAM_INVITE_NOTIFICATION_TYPE) return false;
  return !teamInviteActionedAtPresent(meta);
}

