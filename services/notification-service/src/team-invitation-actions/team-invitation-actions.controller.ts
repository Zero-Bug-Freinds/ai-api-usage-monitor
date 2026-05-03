import {
  Controller,
  Param,
  Post,
  Req,
  UnauthorizedException,
  UseGuards,
} from '@nestjs/common';
import { ApiHeader, ApiOperation, ApiTags } from '@nestjs/swagger';
import { InAppAuthGuard } from '../in-app-notifications/auth/in-app-auth.guard';
import { InAppNotificationsService } from '../in-app-notifications/in-app-notifications.service';
import type { AuthedRequest } from '../in-app-notifications/in-app-notifications.types';
import { TeamInvitationCommandPublisher } from './team-invitation-command.publisher';
import { TeamServiceInternalClient } from './team-service-internal.client';

function requireUserId(req: AuthedRequest): string {
  const id = req.auth?.userId;
  if (!id) {
    throw new UnauthorizedException('Missing X-User-Id');
  }
  return id;
}

@ApiTags('team-invitations')
@Controller('api/team-invitations')
@UseGuards(InAppAuthGuard)
@ApiHeader({
  name: 'X-User-Id',
  required: false,
  description:
    'API Gateway injects the JWT `sub` (email). Invitation decisions use this id.',
})
@ApiHeader({
  name: 'X-Correlation-Id',
  required: false,
  description: 'Optional correlation id propagated by the Gateway or BFF.',
})
export class TeamInvitationActionsController {
  constructor(
    private readonly teamInternal: TeamServiceInternalClient,
    private readonly publisher: TeamInvitationCommandPublisher,
    private readonly inAppNotifications: InAppNotificationsService,
  ) {}

  @Post(':invitationId/accept')
  @ApiOperation({ summary: 'Accept a team invitation (internal action via notification)' })
  async accept(@Req() req: AuthedRequest, @Param('invitationId') invitationId: string) {
    const inviteeUserId = requireUserId(req);
    const correlationId = getTrimmedHeader(req, 'x-correlation-id');

    const result = await this.teamInternal.acceptInvitation({ invitationId, inviteeUserId });

    await this.inAppNotifications.markTeamInviteNotificationsResolved({
      userId: inviteeUserId,
      platformUserId: req.auth?.platformUserId,
      invitationId,
      decision: 'ACCEPT',
    });

    // Best-effort command publish for async processing / audit; membership is already applied via internal API.
    await this.publisher.publishDecision({
      invitationId,
      inviteeUserId,
      decision: 'ACCEPT',
      correlationId: correlationId ?? undefined,
    });

    return result;
  }

  @Post(':invitationId/reject')
  @ApiOperation({ summary: 'Reject a team invitation (internal action via notification)' })
  async reject(@Req() req: AuthedRequest, @Param('invitationId') invitationId: string) {
    const inviteeUserId = requireUserId(req);
    const correlationId = getTrimmedHeader(req, 'x-correlation-id');

    const result = await this.teamInternal.rejectInvitation({ invitationId, inviteeUserId });

    await this.inAppNotifications.markTeamInviteNotificationsResolved({
      userId: inviteeUserId,
      platformUserId: req.auth?.platformUserId,
      invitationId,
      decision: 'REJECT',
    });

    // Best-effort command publish for async processing / audit; rejection is already applied via internal API.
    await this.publisher.publishDecision({
      invitationId,
      inviteeUserId,
      decision: 'REJECT',
      correlationId: correlationId ?? undefined,
    });

    return result;
  }
}

function getTrimmedHeader(req: AuthedRequest, name: string): string | undefined {
  const raw = req.headers[name];
  const v = Array.isArray(raw) ? raw[0] : raw;
  if (typeof v !== 'string') return undefined;
  const t = v.trim();
  return t.length > 0 ? t : undefined;
}

