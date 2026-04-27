import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Request } from 'express';

import type { AuthContext, ScopeType } from '../in-app-notifications.types';

const HDR_USER_ID = 'x-user-id';
const HDR_PLATFORM_USER_ID = 'x-platform-user-id';
const HDR_TEAM_ID = 'x-team-id';
const HDR_SCOPE_TYPE = 'x-scope-type';
const HDR_INTERNAL_SECRET = 'x-notification-internal-secret';

/**
 * Authenticates in-app HTTP requests in a **trusted private hop** behind API Gateway
 * (or equivalent). Callers on the public internet must not be able to set trust headers
 * directly; only the Gateway should inject `X-User-Id` and related headers after JWT
 * validation.
 *
 * Phase 1 precedence:
 * 1. `X-User-Id` (required for user identity) plus optional gateway companion headers.
 * 2. Else `X-Notification-Internal-Secret` when it matches configured secret (privileged).
 * 3. Else 401.
 *
 * Phase 2 (Gateway rollout): validate `X-Gateway-Auth` against `GATEWAY_SHARED_SECRET`
 * in the same change that removes gateway permit-all for `/api/notification/**`.
 */
@Injectable()
export class InAppAuthGuard implements CanActivate {
  constructor(private readonly config: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<Request & { userId?: string; auth?: AuthContext }>();

    const userId = getTrimmedHeader(req, HDR_USER_ID);
    const platformUserId = getTrimmedHeader(req, HDR_PLATFORM_USER_ID);
    const teamId = getTrimmedHeader(req, HDR_TEAM_ID);
    const scopeType = parseScopeType(getTrimmedHeader(req, HDR_SCOPE_TYPE));

    const internalSecret = getTrimmedHeader(req, HDR_INTERNAL_SECRET);
    const expectedInternalSecret =
      this.config.get<string>('NOTIFICATION_INTERNAL_SECRET')?.trim() ?? '';

    const isInternal =
      expectedInternalSecret.length > 0 &&
      internalSecret !== undefined &&
      internalSecret === expectedInternalSecret;

    if (userId !== undefined) {
      req.userId = userId;
      req.auth = {
        userId,
        platformUserId: platformUserId ?? undefined,
        teamId: teamId ?? undefined,
        scopeType,
        isInternal,
      };
      return true;
    }

    if (isInternal) {
      req.auth = { isInternal: true };
      return true;
    }

    throw new UnauthorizedException('Missing X-User-Id');
  }
}

function getTrimmedHeader(req: Request, name: string): string | undefined {
  const raw = req.headers[name];
  const v = Array.isArray(raw) ? raw[0] : raw;
  if (typeof v !== 'string') return undefined;
  const t = v.trim();
  return t.length > 0 ? t : undefined;
}

function parseScopeType(raw: string | undefined): ScopeType | undefined {
  if (raw === undefined) return undefined;
  const u = raw.trim().toUpperCase();
  if (u === 'USER' || u === 'TEAM') return u;
  return undefined;
}
