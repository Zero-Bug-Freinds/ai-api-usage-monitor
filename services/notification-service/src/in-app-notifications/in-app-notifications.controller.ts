import {
  Body,
  Controller,
  Get,
  Param,
  Patch,
  Post,
  Query,
  Req,
  UnauthorizedException,
  UseGuards,
} from '@nestjs/common';
import { ApiHeader, ApiOperation, ApiTags } from '@nestjs/swagger';
import { InAppAuthGuard } from './auth/in-app-auth.guard';
import { ListInAppNotificationsQuery } from './dto/list-in-app-notifications.query';
import { TestSendInAppNotificationDto } from './dto/test-send-in-app-notification.dto';
import { InAppNotificationsService } from './in-app-notifications.service';
import type { AuthedRequest } from './in-app-notifications.types';

function requireUserId(req: AuthedRequest): string {
  const id = req.auth?.userId;
  if (!id) {
    throw new UnauthorizedException('Missing X-User-Id');
  }
  return id;
}

function optionalPlatformUserId(req: AuthedRequest): string | undefined {
  const id = req.auth?.platformUserId;
  return id && id.trim().length > 0 ? id : undefined;
}

@ApiTags('in-app-notifications')
@Controller('api/in-app-notifications')
@UseGuards(InAppAuthGuard)
@ApiHeader({
  name: 'X-User-Id',
  required: false,
  description:
    'API Gateway injects the JWT `sub` (email). Do not send the platform internal `userId` claim here.',
})
@ApiHeader({
  name: 'X-Platform-User-Id',
  required: false,
  description: 'API Gateway injects the platform internal user id (JWT `userId` claim).',
})
@ApiHeader({
  name: 'X-Team-Id',
  required: false,
  description: 'Optional tenant header from API Gateway (`team_id` claim).',
})
@ApiHeader({
  name: 'X-Scope-Type',
  required: false,
  description: 'Optional `USER` or `TEAM` from API Gateway (`scope_type` claim).',
})
@ApiHeader({
  name: 'X-Correlation-Id',
  required: false,
  description: 'Optional correlation id propagated by the Gateway or BFF.',
})
@ApiHeader({
  name: 'X-Notification-Internal-Secret',
  required: false,
  description:
    'Internal secret header. When valid, allows privileged test-send to other users.',
})
export class InAppNotificationsController {
  constructor(private readonly service: InAppNotificationsService) {}

  @Get()
  @ApiOperation({ summary: 'List in-app notifications (cursor pagination)' })
  async list(@Req() req: AuthedRequest, @Query() query: ListInAppNotificationsQuery) {
    const userId = requireUserId(req);
    const platformUserId = optionalPlatformUserId(req);
    const limit = query.limit ?? 30;
    return await this.service.listByUserId({
      userId,
      platformUserId,
      cursor: query.cursor,
      limit,
    });
  }

  @Get('unread-count')
  @ApiOperation({ summary: 'Get unread in-app notification count' })
  async unreadCount(@Req() req: AuthedRequest) {
    const userId = requireUserId(req);
    const platformUserId = optionalPlatformUserId(req);
    return await this.service.countUnreadByUserId({ userId, platformUserId });
  }

  @Patch(':id/read')
  @ApiOperation({ summary: 'Mark a notification as read' })
  async markRead(@Req() req: AuthedRequest, @Param('id') id: string) {
    const userId = requireUserId(req);
    const platformUserId = optionalPlatformUserId(req);
    return await this.service.markRead({ userId, platformUserId, id });
  }

  @Post('read-all')
  @ApiOperation({ summary: 'Mark all notifications as read' })
  async markAllRead(@Req() req: AuthedRequest) {
    const userId = requireUserId(req);
    const platformUserId = optionalPlatformUserId(req);
    return await this.service.markAllRead({ userId, platformUserId });
  }

  @Post('test-send')
  @ApiOperation({
    summary:
      'Create a test in-app notification (self-only unless internal secret is provided)',
  })
  async testSend(@Req() req: AuthedRequest, @Body() dto: TestSendInAppNotificationDto) {
    const auth = req.auth;
    if (!auth) {
      throw new UnauthorizedException('Missing authentication context');
    }
    return await this.service.testSend({
      actorUserId: auth.userId,
      isInternal: auth.isInternal,
      targetUserId: dto.targetUserId,
      title: dto.title,
      body: dto.body,
      type: dto.type,
    });
  }
}
