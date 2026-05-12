import { describe, expect, it, vi, beforeEach } from 'vitest';
import { PrismaClientKnownRequestError } from '@prisma/client/runtime/library';
import { IdentityExternalApiKeyInAppHandlerService } from './identity-external-api-key-in-app-handler.service';

function makeHandler(prisma: {
  $transaction: ReturnType<typeof vi.fn>;
}): IdentityExternalApiKeyInAppHandlerService {
  const config = {
    get: vi.fn((key: string, def?: string) => {
      if (key === 'IDENTITY_EXTERNAL_API_KEY_EVENTS_DEFAULT_LOCALE') return 'en';
      return def;
    }),
  } as unknown as import('@nestjs/config').ConfigService;

  return new IdentityExternalApiKeyInAppHandlerService(
    prisma as unknown as import('../prisma/prisma.service').PrismaService,
    config,
  );
}

describe('IdentityExternalApiKeyInAppHandlerService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('skips deleted when userId is empty', async () => {
    const prisma = { $transaction: vi.fn() };
    const handler = makeHandler(prisma);
    const r = await handler.handleDeleted({
      eventType: 'EXTERNAL_API_KEY_DELETED',
      userId: '  ',
      apiKeyId: 1,
      occurredAt: '2026-01-01T00:00:00.000Z',
      retainLogs: false,
      provider: null,
      alias: null,
    });
    expect(r.created).toBe(false);
    expect(prisma.$transaction).not.toHaveBeenCalled();
  });

  it('creates delivery + in-app on deleted', async () => {
    const creates: string[] = [];
    const prisma = {
      $transaction: vi.fn(async (fn: (tx: unknown) => Promise<void>) => {
        const tx = {
          notificationDelivery: {
            create: vi.fn(async () => {
              creates.push('delivery');
            }),
          },
          inAppNotification: {
            create: vi.fn(async () => {
              creates.push('in-app');
            }),
          },
        };
        await fn(tx);
      }),
    };
    const handler = makeHandler(prisma);
    const r = await handler.handleDeleted({
      eventType: 'EXTERNAL_API_KEY_DELETED',
      userId: 'owner@example.com',
      apiKeyId: 3,
      occurredAt: '2026-01-01T00:00:00.000Z',
      retainLogs: true,
      provider: 'openai',
      alias: 'Work',
    });
    expect(r.created).toBe(true);
    expect(prisma.$transaction).toHaveBeenCalledTimes(1);
    expect(creates).toEqual(['delivery', 'in-app']);
  });

  it('returns created false on dedupe unique violation', async () => {
    const err = new PrismaClientKnownRequestError('Unique', {
      code: 'P2002',
      clientVersion: 'test',
    });
    const prisma = {
      $transaction: vi.fn(async () => {
        throw err;
      }),
    };
    const handler = makeHandler(prisma);
    const r = await handler.handleStatusChanged({
      schemaVersion: 2,
      occurredAt: '2026-01-01T00:00:00.000Z',
      keyId: 1,
      userId: 'u@x.com',
      provider: 'p',
      status: 'ACTIVE',
    });
    expect(r.created).toBe(false);
  });
});
