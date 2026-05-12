import { Module } from '@nestjs/common';
import { TeamEventsConsumer } from './team-events.consumer';
import { TeamInAppNotificationHandlerService } from './team-in-app-notification-handler.service';
import { InAppNotificationsModule } from '../in-app-notifications/in-app-notifications.module';
import { PrismaModule } from '../prisma/prisma.module';

@Module({
  imports: [PrismaModule, InAppNotificationsModule],
  providers: [TeamInAppNotificationHandlerService, TeamEventsConsumer],
})
export class TeamEventsModule {}
