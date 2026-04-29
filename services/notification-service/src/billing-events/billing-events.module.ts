import { Module } from '@nestjs/common';
import { PrismaModule } from '../prisma/prisma.module';
import { BillingEventsConsumer } from './billing-events.consumer';
import { BillingInAppNotificationHandlerService } from './billing-in-app-notification-handler.service';

@Module({
  imports: [PrismaModule],
  providers: [BillingInAppNotificationHandlerService, BillingEventsConsumer],
})
export class BillingEventsModule {}

