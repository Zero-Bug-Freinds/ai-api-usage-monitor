import { Injectable, Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import type { BillingTeamBudgetThresholdReachedEventPayload } from './billing-team-budget-threshold-event.schema';
import {
  IN_APP_DELIVERY_CHANNEL,
  IN_APP_DELIVERY_STATUS,
} from '../in-app-notifications/in-app-delivery.constants';
import { buildBillingTeamApiKeyBudgetThresholdCopy } from './billing-notification-templates';
import { buildBillingTeamBudgetInAppDedupeKey } from './billing-team-dedupe-keys';
import { TeamServiceClient } from './team-service.client';

@Injectable()
export class BillingTeamInAppNotificationHandlerService {
  private readonly logger = new Logger(BillingTeamInAppNotificationHandlerService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly teamServiceClient: TeamServiceClient,
  ) {}

  async handleTeamBudgetThresholdReached(params: {
    payload: BillingTeamBudgetThresholdReachedEventPayload;
  }): Promise<{ createdCount: number; skippedCount: number }> {
    const { payload } = params;

    const teamName =
      (await this.teamServiceClient.fetchTeamNameInternal({ teamId: payload.teamId })) ??
      String(payload.teamId);

    const memberUserIds = await this.teamServiceClient.fetchTeamMemberUserIds({
      teamId: payload.teamId,
      requesterUserId: payload.triggerUserId,
    });

    if (memberUserIds.length === 0) {
      this.logger.warn(
        `No team members resolved for team budget notification (teamId=${payload.teamId})`,
      );
      return { createdCount: 0, skippedCount: 0 };
    }

    let createdCount = 0;
    let skippedCount = 0;

    for (const targetUserId of memberUserIds) {
      const dedupeKey = buildBillingTeamBudgetInAppDedupeKey({
        teamId: payload.teamId,
        teamApiKeyId: payload.teamApiKeyId,
        targetUserId,
        monthStart: payload.monthStart,
        thresholdPct: payload.thresholdPct,
      });
      if (!dedupeKey) {
        skippedCount += 1;
        continue;
      }

      const copy = buildBillingTeamApiKeyBudgetThresholdCopy({ teamName, payload });

      try {
        await this.prisma.$transaction(async (tx) => {
          await tx.notificationDelivery.create({
            data: {
              dedupeKey,
              channel: IN_APP_DELIVERY_CHANNEL,
              status: IN_APP_DELIVERY_STATUS,
              payload: {
                eventType: 'BILLING_TEAM_API_KEY_BUDGET_THRESHOLD_REACHED',
                teamId: payload.teamId,
                teamApiKeyId: payload.teamApiKeyId,
                provider: payload.provider,
                apiKeyAlias: payload.apiKeyAlias,
                targetUserId,
                monthStart: payload.monthStart,
                thresholdPct: payload.thresholdPct,
              } as Prisma.InputJsonValue,
            },
          });

          await tx.inAppNotification.create({
            data: {
              userId: targetUserId,
              title: copy.title,
              body: copy.body,
              type: 'billing:team-api-key-budget-threshold',
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

