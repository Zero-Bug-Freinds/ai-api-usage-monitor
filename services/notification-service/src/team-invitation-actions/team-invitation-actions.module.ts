import { Module } from '@nestjs/common';
import { InAppNotificationsModule } from '../in-app-notifications/in-app-notifications.module';
import { TeamInvitationActionsController } from './team-invitation-actions.controller';
import { TeamInvitationCommandPublisher } from './team-invitation-command.publisher';
import { TeamServiceInternalClient } from './team-service-internal.client';

@Module({
  imports: [InAppNotificationsModule],
  controllers: [TeamInvitationActionsController],
  providers: [TeamServiceInternalClient, TeamInvitationCommandPublisher],
})
export class TeamInvitationActionsModule {}

