import { Injectable, Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import type { BillingTeamBudgetThresholdReachedEventPayload } from './billing-team-budget-threshold-event.schema';
import { buildBillingTeamBudgetInAppDedupeKey } from './billing-team-dedupe-keys';
import { TeamServiceClient } from './team-service.client';

const IN_APP_CHANNEL = 'in-app';
const DELIVERY_STATUS = 'delivered';

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
        targetUserId,
        monthStart: payload.monthStart,
        thresholdPct: payload.thresholdPct,
      });
      if (!dedupeKey) {
        skippedCount += 1;
        continue;
      }

      const title = '팀 예산 임계치 도달';
      const pct = Math.round(payload.thresholdPct * 100);
      const body = `이번 달 팀 예산의 ${pct}%에 도달했습니다.`;

      try {
        await this.prisma.$transaction(async (tx) => {
          await tx.notificationDelivery.create({
            data: {
              dedupeKey,
              channel: IN_APP_CHANNEL,
              status: DELIVERY_STATUS,
              payload: {
                eventType: 'BILLING_TEAM_BUDGET_THRESHOLD_REACHED',
                teamId: payload.teamId,
                targetUserId,
                monthStart: payload.monthStart,
                thresholdPct: payload.thresholdPct,
              } as Prisma.InputJsonValue,
            },
          });

          await tx.inAppNotification.create({
            data: {
              userId: targetUserId,
              title,
              body,
              type: 'billing:team-budget-threshold',
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

