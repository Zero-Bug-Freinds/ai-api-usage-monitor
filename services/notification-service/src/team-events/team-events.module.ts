import { Module } from '@nestjs/common';
import { TeamEventsConsumer } from './team-events.consumer';
import { TeamInAppNotificationHandlerService } from './team-in-app-notification-handler.service';
import { PrismaModule } from '../prisma/prisma.module';

@Module({
  imports: [PrismaModule],
  providers: [TeamInAppNotificationHandlerService, TeamEventsConsumer],
})
export class TeamEventsModule {}
