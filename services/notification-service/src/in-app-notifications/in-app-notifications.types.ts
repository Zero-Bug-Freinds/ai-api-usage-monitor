import type { Request } from 'express';

export type ScopeType = 'USER' | 'TEAM';

/**
 * Populated by `InAppAuthGuard`. User-facing fields come from API Gateway trust headers
 * after JWT validation; internal calls may set only `isInternal`.
 */
export type AuthContext = {
  /** User identifier from `X-User-Id` (JWT `sub` email injected by API Gateway). */
  userId?: string;
  /** Platform internal user id from `X-Platform-User-Id` (JWT `userId` claim). */
  platformUserId?: string;
  teamId?: string;
  scopeType?: ScopeType;
  isInternal: boolean;
};

export type AuthedRequest = Request & {
  /** Mirrors `auth.userId` when the in-app auth guard runs. */
  userId?: string;
  auth?: AuthContext;
};
