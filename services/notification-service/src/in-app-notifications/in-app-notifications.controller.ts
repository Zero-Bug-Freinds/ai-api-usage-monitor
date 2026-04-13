import {
  Body,
  Controller,
  Get,
  UnauthorizedException,
  Param,
  Patch,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiHeader, ApiOperation, ApiTags } from '@nestjs/swagger';
import { InAppAuthGuard } from './auth/in-app-auth.guard';
import { ListInAppNotificationsQuery } from './dto/list-in-app-notifications.query';
import { TestSendInAppNotificationDto } from './dto/test-send-in-app-notification.dto';
import { InAppNotificationsService } from './in-app-notifications.service';
import type { AuthedRequest } from './in-app-notifications.types';

@ApiTags('in-app-notifications')
@Controller('api/in-app-notifications')
@UseGuards(InAppAuthGuard)
@ApiHeader({
  name: 'X-User-Id',
  required: false,
  description:
    'API Gateway trusted header (recommended). Required for list/read endpoints.',
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
    if (!req.userId) {
      throw new UnauthorizedException('Missing X-User-Id');
    }

    const limit = query.limit ?? 30;
    return await this.service.listByUserId({
      userId: req.userId,
      cursor: query.cursor,
      limit,
    });
  }

  @Patch(':id/read')
  @ApiOperation({ summary: 'Mark a notification as read' })
  async markRead(@Req() req: AuthedRequest, @Param('id') id: string) {
    if (!req.userId) {
      throw new UnauthorizedException('Missing X-User-Id');
    }

    return await this.service.markRead({ userId: req.userId, id });
  }

  @Post('read-all')
  @ApiOperation({ summary: 'Mark all notifications as read' })
  async markAllRead(@Req() req: AuthedRequest) {
    if (!req.userId) {
      throw new UnauthorizedException('Missing X-User-Id');
    }

    return await this.service.markAllRead({ userId: req.userId });
  }

  @Post('test-send')
  @ApiOperation({
    summary:
      'Create a test in-app notification (self-only unless internal secret is provided)',
  })
  async testSend(@Req() req: AuthedRequest, @Body() dto: TestSendInAppNotificationDto) {
    return await this.service.testSend({
      actorUserId: req.userId,
      isInternal: Boolean(req.auth?.isInternal),
      targetUserId: dto.targetUserId,
      title: dto.title,
      body: dto.body,
      type: dto.type,
    });
  }
}

