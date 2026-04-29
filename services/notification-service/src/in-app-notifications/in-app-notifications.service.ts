import { ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

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
      select: { id: true, userId: true, readAt: true },
    });

    if (!existing) throw new NotFoundException('Notification not found');
    if (!ids.includes(existing.userId)) throw new NotFoundException('Notification not found');

    if (existing.readAt) return { updated: false };

    await this.prisma.inAppNotification.update({
      where: { id: params.id },
      data: { readAt: new Date() },
    });

    return { updated: true };
  }

  async markAllRead(params: { userId: string; platformUserId?: string }) {
    const ids = resolveRecipientUserIds(params.userId, params.platformUserId);
    const result = await this.prisma.inAppNotification.updateMany({
      where: { readAt: null, OR: ids.map((userId) => ({ userId })) },
      data: { readAt: new Date() },
    });

    return { updatedCount: result.count };
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

