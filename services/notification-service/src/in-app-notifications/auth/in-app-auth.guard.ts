import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Request } from 'express';

const HDR_USER_ID = 'x-user-id';
const HDR_INTERNAL_SECRET = 'x-notification-internal-secret';

@Injectable()
export class InAppAuthGuard implements CanActivate {
  constructor(private readonly config: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<Request & any>();

    const userIdRaw = req.headers[HDR_USER_ID];
    const userId = Array.isArray(userIdRaw) ? userIdRaw[0] : userIdRaw;

    const internalSecretRaw = req.headers[HDR_INTERNAL_SECRET];
    const internalSecret = Array.isArray(internalSecretRaw)
      ? internalSecretRaw[0]
      : internalSecretRaw;

    const expectedInternalSecret =
      this.config.get<string>('NOTIFICATION_INTERNAL_SECRET')?.trim() ?? '';

    const isInternal =
      expectedInternalSecret.length > 0 &&
      typeof internalSecret === 'string' &&
      internalSecret === expectedInternalSecret;

    if (typeof userId === 'string' && userId.trim().length > 0) {
      req.userId = userId.trim();
      req.auth = { isInternal, actorUserId: userId.trim() };
      return true;
    }

    if (isInternal) {
      req.auth = { isInternal: true };
      return true;
    }

    throw new UnauthorizedException('Missing X-User-Id');
  }
}

