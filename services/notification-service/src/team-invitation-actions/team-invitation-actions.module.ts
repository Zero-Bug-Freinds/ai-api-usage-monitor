import { Module } from '@nestjs/common';
import { TeamInvitationActionsController } from './team-invitation-actions.controller';
import { TeamInvitationCommandPublisher } from './team-invitation-command.publisher';
import { TeamServiceInternalClient } from './team-service-internal.client';

@Module({
  controllers: [TeamInvitationActionsController],
  providers: [TeamServiceInternalClient, TeamInvitationCommandPublisher],
})
export class TeamInvitationActionsModule {}

