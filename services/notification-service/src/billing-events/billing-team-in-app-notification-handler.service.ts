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

      const title = '팀 API 키 예산 임계치 도달';
      const pct = Math.round(payload.thresholdPct * 100);
      const body = `팀 ${teamName}의 ${payload.provider} API 키(${payload.apiKeyAlias}) 사용량이 월 예산의 ${pct}%를 넘었습니다.`;

      try {
        await this.prisma.$transaction(async (tx) => {
          await tx.notificationDelivery.create({
            data: {
              dedupeKey,
              channel: IN_APP_CHANNEL,
              status: DELIVERY_STATUS,
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
              title,
              body,
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

