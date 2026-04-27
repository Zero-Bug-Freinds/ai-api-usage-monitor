import type { Request } from 'express';

export type ScopeType = 'USER' | 'TEAM';

/**
 * Populated by `InAppAuthGuard`. User-facing fields come from API Gateway trust headers
 * after JWT validation; internal calls may set only `isInternal`.
 */
export type AuthContext = {
  /** Platform user id from `X-User-Id` (JWT `userId` claim), never email/`sub`. */
  userId?: string;
  /** Same as `userId` on gateway hops when `X-Platform-User-Id` is injected. */
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
