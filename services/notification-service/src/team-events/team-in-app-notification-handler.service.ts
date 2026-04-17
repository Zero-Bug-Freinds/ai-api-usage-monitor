import { Injectable, Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../prisma/prisma.service';
import { buildInAppDedupeKey } from './team-dedupe-keys';
import type { TeamDomainEventPayload } from './team-domain-event.schema';
import type { TeamEventType } from './team-event-types';
import {
  buildTeamNotificationCopy,
  type NotificationLocale,
} from './team-notification-templates';
import { getEffectiveRecipientUserIds } from './team-recipient-user-ids';

const IN_APP_CHANNEL = 'in-app';
const DELIVERY_STATUS = 'delivered';

@Injectable()
export class TeamInAppNotificationHandlerService {
  private readonly logger = new Logger(TeamInAppNotificationHandlerService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly config: ConfigService,
  ) {}

  private getLocale(): NotificationLocale {
    const raw = this.config.get<string>('TEAM_EVENTS_DEFAULT_LOCALE', 'en');
    return raw === 'ko' ? 'ko' : 'en';
  }

  async handleTeamDomainEvent(
    eventType: TeamEventType,
    payload: TeamDomainEventPayload,
  ): Promise<{ createdCount: number; skippedCount: number }> {
    const recipients = getEffectiveRecipientUserIds(eventType, payload);
    let createdCount = 0;
    let skippedCount = 0;

    const notificationType = `team:${eventType}`;
    const locale = this.getLocale();

    for (const userId of recipients) {
      const dedupeKey = buildInAppDedupeKey(eventType, payload, userId);
      if (!dedupeKey) {
        this.logger.warn(
          `Skipping recipient — missing fields for dedupe (eventType=${eventType}, userId=${userId})`,
        );
        skippedCount += 1;
        continue;
      }

      const copy = buildTeamNotificationCopy(eventType, payload, userId, locale);

      try {
        await this.prisma.$transaction(async (tx) => {
          await tx.notificationDelivery.create({
            data: {
              dedupeKey,
              channel: IN_APP_CHANNEL,
              status: DELIVERY_STATUS,
              payload: {
                eventType,
                teamId: payload.teamId,
                recipientUserId: userId,
              } as Prisma.InputJsonValue,
            },
          });

          await tx.inAppNotification.create({
            data: {
              userId,
              title: copy.title,
              body: copy.body,
              type: notificationType,
            },
          });
        });
        createdCount += 1;
      } catch (e) {
        if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2002') {
          skippedCount += 1;
          continue;
        }
        throw e;
      }
    }

    return { createdCount, skippedCount };
  }
}
