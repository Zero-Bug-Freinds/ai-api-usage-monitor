import { Module } from '@nestjs/common';
import { PrismaModule } from '../prisma/prisma.module';
import { BillingEventsConsumer } from './billing-events.consumer';
import { BillingInAppNotificationHandlerService } from './billing-in-app-notification-handler.service';
import { BillingTeamEventsConsumer } from './billing-team-events.consumer';
import { BillingTeamInAppNotificationHandlerService } from './billing-team-in-app-notification-handler.service';
import { TeamServiceClient } from './team-service.client';

@Module({
  imports: [PrismaModule],
  providers: [
    BillingInAppNotificationHandlerService,
    BillingEventsConsumer,
    TeamServiceClient,
    BillingTeamInAppNotificationHandlerService,
    BillingTeamEventsConsumer,
  ],
})
export class BillingEventsModule {}

