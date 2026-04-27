import { Injectable, Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../prisma/prisma.service';
import type { BillingBudgetThresholdReachedEventPayload } from './billing-budget-threshold-event.schema';
import {
  buildBillingBudgetInAppDedupeKey,
  type BillingSubjectType,
} from './billing-dedupe-keys';
import { buildBillingBudgetThresholdCopy } from './billing-notification-templates';

const IN_APP_CHANNEL = 'in-app';
const DELIVERY_STATUS = 'delivered';

@Injectable()
export class BillingInAppNotificationHandlerService {
  private readonly logger = new Logger(BillingInAppNotificationHandlerService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly config: ConfigService,
  ) {}

  private getLocale(): 'ko' | 'en' {
    const raw = this.config.get<string>('BILLING_EVENTS_DEFAULT_LOCALE', 'ko');
    return raw === 'en' ? 'en' : 'ko';
  }

  async handleBudgetThresholdReached(params: {
    subjectType: BillingSubjectType;
    userId?: string;
    teamId?: string;
    apiKeyId?: string;
    payload: BillingBudgetThresholdReachedEventPayload;
  }): Promise<{ created: boolean; dedupeKey: string | null }> {
    const dedupeKey = buildBillingBudgetInAppDedupeKey(params);
    if (!dedupeKey) {
      this.logger.warn(
        `Skipping billing budget event — missing fields for dedupe (subjectType=${params.subjectType})`,
      );
      return { created: false, dedupeKey: null };
    }

    const targetUserId = params.userId;
    if (!targetUserId?.trim()) {
      this.logger.warn(
        `Skipping billing budget event — missing userId header (subjectType=${params.subjectType})`,
      );
      return { created: false, dedupeKey };
    }

    const copy = buildBillingBudgetThresholdCopy(params.payload, this.getLocale());

    try {
      await this.prisma.$transaction(async (tx) => {
        await tx.notificationDelivery.create({
          data: {
            dedupeKey,
            channel: IN_APP_CHANNEL,
            status: DELIVERY_STATUS,
            payload: {
              eventType: 'BILLING_BUDGET_THRESHOLD_REACHED',
              subjectType: params.subjectType,
              userId: params.userId,
              teamId: params.teamId,
              apiKeyId: params.apiKeyId,
              monthStart: params.payload.monthStart,
              thresholdPct: params.payload.thresholdPct,
            } as Prisma.InputJsonValue,
          },
        });

        await tx.inAppNotification.create({
          data: {
            userId: targetUserId,
            title: copy.title,
            body: copy.body,
            type: 'billing:budget-threshold',
          },
        });
      });
      return { created: true, dedupeKey };
    } catch (e) {
      if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === 'P2002') {
        return { created: false, dedupeKey };
      }
      throw e;
    }
  }
}

