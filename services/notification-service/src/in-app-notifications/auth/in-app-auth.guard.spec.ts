import { describe, expect, it } from 'vitest';
import { UnauthorizedException } from '@nestjs/common';
import type { ExecutionContext } from '@nestjs/common';
import { InAppAuthGuard } from './in-app-auth.guard';
import type { AuthContext } from '../in-app-notifications.types';

function makeContext(req: Record<string, unknown>): ExecutionContext {
  return {
    switchToHttp: () => ({
      getRequest: () => req,
    }),
  } as ExecutionContext;
}

describe('InAppAuthGuard', () => {
  const secret = 'internal-secret';

  it('accepts X-User-Id only and fills auth (isInternal false without secret)', () => {
    const config = { get: (k: string) => (k === 'NOTIFICATION_INTERNAL_SECRET' ? secret : undefined) };
    const guard = new InAppAuthGuard(config as never);
    const req: { headers: Record<string, string>; userId?: string; auth?: AuthContext } = {
      headers: { 'x-user-id': '  user-1  ' },
    };
    expect(guard.canActivate(makeContext(req))).toBe(true);
    expect(req.userId).toBe('user-1');
    expect(req.auth).toEqual({
      userId: 'user-1',
      platformUserId: undefined,
      teamId: undefined,
      scopeType: undefined,
      isInternal: false,
    });
  });

  it('accepts internal secret only (no user id)', () => {
    const config = { get: (k: string) => (k === 'NOTIFICATION_INTERNAL_SECRET' ? secret : undefined) };
    const guard = new InAppAuthGuard(config as never);
    const req: { headers: Record<string, string>; userId?: string; auth?: AuthContext } = {
      headers: { 'x-notification-internal-secret': secret },
    };
    expect(guard.canActivate(makeContext(req))).toBe(true);
    expect(req.userId).toBeUndefined();
    expect(req.auth).toEqual({ isInternal: true });
  });

  it('throws when neither X-User-Id nor valid internal secret', () => {
    const config = { get: (k: string) => (k === 'NOTIFICATION_INTERNAL_SECRET' ? secret : undefined) };
    const guard = new InAppAuthGuard(config as never);
    const req = { headers: {} };
    expect(() => guard.canActivate(makeContext(req))).toThrow(UnauthorizedException);
  });

  it('throws when internal secret is wrong and no X-User-Id', () => {
    const config = { get: (k: string) => (k === 'NOTIFICATION_INTERNAL_SECRET' ? secret : undefined) };
    const guard = new InAppAuthGuard(config as never);
    const req = { headers: { 'x-notification-internal-secret': 'wrong' } };
    expect(() => guard.canActivate(makeContext(req))).toThrow(UnauthorizedException);
  });

  it('accepts X-User-Id with X-Team-Id and X-Scope-Type', () => {
    const config = { get: (k: string) => (k === 'NOTIFICATION_INTERNAL_SECRET' ? '' : undefined) };
    const guard = new InAppAuthGuard(config as never);
    const req: { headers: Record<string, string>; auth?: AuthContext } = {
      headers: {
        'x-user-id': 'u1',
        'x-team-id': 't9',
        'x-scope-type': 'team',
      },
    };
    expect(guard.canActivate(makeContext(req))).toBe(true);
    expect(req.auth).toMatchObject({
      userId: 'u1',
      teamId: 't9',
      scopeType: 'TEAM',
      isInternal: false,
    });
  });

  it('marks isInternal when X-User-Id and valid internal secret are both present', () => {
    const config = { get: (k: string) => (k === 'NOTIFICATION_INTERNAL_SECRET' ? secret : undefined) };
    const guard = new InAppAuthGuard(config as never);
    const req: { headers: Record<string, string>; auth?: AuthContext } = {
      headers: {
        'x-user-id': 'actor',
        'x-notification-internal-secret': secret,
      },
    };
    expect(guard.canActivate(makeContext(req))).toBe(true);
    expect(req.auth?.isInternal).toBe(true);
    expect(req.auth?.userId).toBe('actor');
  });
});
