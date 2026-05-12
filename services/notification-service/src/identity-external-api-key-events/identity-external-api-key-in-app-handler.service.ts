import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Prisma } from '@prisma/client';
import { PrismaClientKnownRequestError } from '@prisma/client/runtime/library';
import { PrismaService } from '../prisma/prisma.service';
import {
  buildExternalApiKeyDeletedInAppDedupeKey,
  buildExternalApiKeyStatusInAppDedupeKey,
} from './identity-external-api-key-dedupe-keys';
import type { ExternalApiKeyDeletedEventPayload } from './identity-external-api-key-event.schema';
import type { ExternalApiKeyStatusChangedEventPayload } from './identity-external-api-key-event.schema';
import {
  buildExternalApiKeyDeletedCopy,
  buildExternalApiKeyStatusChangedCopy,
  type IdentityExternalApiKeyNotificationLocale,
} from './identity-external-api-key-notification-templates';

const IN_APP_CHANNEL = 'in-app';
const DELIVERY_STATUS = 'delivered';

function inAppTypeForStatus(status: ExternalApiKeyStatusChangedEventPayload['status']): string {
  const slug = status.toLowerCase().replace(/_/g, '-');
  return `identity:external-api-key:status-${slug}`;
}

@Injectable()
export class IdentityExternalApiKeyInAppHandlerService {
  private readonly logger = new Logger(IdentityExternalApiKeyInAppHandlerService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly config: ConfigService,
  ) {}

  private getLocale(): IdentityExternalApiKeyNotificationLocale {
    const raw = this.config.get<string>('IDENTITY_EXTERNAL_API_KEY_EVENTS_DEFAULT_LOCALE', 'en');
    return raw === 'ko' ? 'ko' : 'en';
  }

  async handleDeleted(payload: ExternalApiKeyDeletedEventPayload): Promise<{ created: boolean }> {
    const userId = payload.userId?.trim();
    if (!userId) {
      this.logger.warn('Skipping identity external API key delete — empty userId');
      return { created: false };
    }

    const dedupeKey = buildExternalApiKeyDeletedInAppDedupeKey(payload);
    if (!dedupeKey) {
      this.logger.warn('Skipping identity external API key delete — could not build dedupeKey');
      return { created: false };
    }

    const locale = this.getLocale();
    const copy = buildExternalApiKeyDeletedCopy(payload, locale);
    const apiKeyId = payload.apiKeyId;

    try {
      await this.prisma.$transaction(async (tx) => {
        await tx.notificationDelivery.create({
          data: {
            dedupeKey,
            channel: IN_APP_CHANNEL,
            status: DELIVERY_STATUS,
            payload: {
              eventKind: 'EXTERNAL_API_KEY_DELETED',
              userId,
              apiKeyId,
              occurredAt:
                typeof payload.occurredAt === 'string'
                  ? payload.occurredAt
                  : (payload.occurredAt as Date).toISOString(),
            } as Prisma.InputJsonValue,
          },
        });

        await tx.inAppNotification.create({
          data: {
            userId,
            title: copy.title,
            body: copy.body,
            type: 'identity:external-api-key:deleted',
            meta: {
              keyId: apiKeyId,
              provider: payload.provider ?? null,
              alias: payload.alias ?? null,
            } satisfies Prisma.InputJsonObject,
          },
        });
      });
      return { created: true };
    } catch (e) {
      if (e instanceof PrismaClientKnownRequestError && e.code === 'P2002') {
        return { created: false };
      }
      throw e;
    }
  }

  async handleStatusChanged(
    payload: ExternalApiKeyStatusChangedEventPayload,
  ): Promise<{ created: boolean }> {
    const userId = payload.userId?.trim();
    if (!userId) {
      this.logger.warn('Skipping identity external API key status — empty userId');
      return { created: false };
    }

    const dedupeKey = buildExternalApiKeyStatusInAppDedupeKey(payload);
    if (!dedupeKey) {
      this.logger.warn('Skipping identity external API key status — could not build dedupeKey');
      return { created: false };
    }

    const locale = this.getLocale();
    const copy = buildExternalApiKeyStatusChangedCopy(payload, locale);
    const notificationType = inAppTypeForStatus(payload.status);

    try {
      await this.prisma.$transaction(async (tx) => {
        await tx.notificationDelivery.create({
          data: {
            dedupeKey,
            channel: IN_APP_CHANNEL,
            status: DELIVERY_STATUS,
            payload: {
              eventKind: 'ExternalApiKeyStatusChangedEvent',
              userId,
              keyId: payload.keyId,
              status: payload.status,
              schemaVersion: payload.schemaVersion,
              occurredAt:
                typeof payload.occurredAt === 'string'
                  ? payload.occurredAt
                  : (payload.occurredAt as Date).toISOString(),
            } as Prisma.InputJsonValue,
          },
        });

        await tx.inAppNotification.create({
          data: {
            userId,
            title: copy.title,
            body: copy.body,
            type: notificationType,
            meta: {
              keyId: payload.keyId,
              provider: payload.provider,
              alias: payload.alias ?? null,
              status: payload.status,
            } satisfies Prisma.InputJsonObject,
          },
        });
      });
      return { created: true };
    } catch (e) {
      if (e instanceof PrismaClientKnownRequestError && e.code === 'P2002') {
        return { created: false };
      }
      throw e;
    }
  }
}
