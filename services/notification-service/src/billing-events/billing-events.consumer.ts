import {
  Injectable,
  Logger,
  OnApplicationBootstrap,
  OnModuleDestroy,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Channel, ConsumeMessage } from 'amqplib';
import * as amqp from 'amqplib';
import { safeParseBillingBudgetThresholdReachedEventJson } from './billing-budget-threshold-event.schema';
import { BillingInAppNotificationHandlerService } from './billing-in-app-notification-handler.service';
import type { BillingSubjectType } from './billing-dedupe-keys';

type AmqpConnection = Awaited<ReturnType<typeof amqp.connect>>;

@Injectable()
export class BillingEventsConsumer
  implements OnApplicationBootstrap, OnModuleDestroy
{
  private readonly logger = new Logger(BillingEventsConsumer.name);
  private connection: AmqpConnection | null = null;
  private channel: Channel | null = null;
  private consumerTag: string | null = null;

  constructor(
    private readonly config: ConfigService,
    private readonly handler: BillingInAppNotificationHandlerService,
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    const enabled = this.config.get<string>('BILLING_EVENTS_CONSUMER_ENABLED', 'false');
    if (enabled !== 'true' && enabled !== '1') {
      this.logger.log('Billing events consumer disabled (BILLING_EVENTS_CONSUMER_ENABLED)');
      return;
    }

    const url = this.config.get<string>('RABBITMQ_URL');
    if (!url?.trim()) {
      this.logger.warn(
        'BILLING_EVENTS_CONSUMER_ENABLED is true but RABBITMQ_URL is empty — consumer not started',
      );
      return;
    }

    const queue = this.config.get<string>(
      'BILLING_EVENTS_QUEUE_NAME',
      'notification.billing.events',
    );
    const prefetch = Number(this.config.get<string>('BILLING_EVENTS_PREFETCH', '10')) || 10;
    const assertTopology =
      this.config.get<string>('BILLING_EVENTS_ASSERT_TOPOLOGY', 'false') === 'true' ||
      this.config.get<string>('BILLING_EVENTS_ASSERT_TOPOLOGY', 'false') === '1';
    const exchange = this.config.get<string>('BILLING_EVENTS_EXCHANGE_NAME');
    const bindingKeysRaw = this.config.get<string>(
      'BILLING_EVENTS_BINDING_KEYS',
      'billing.budget.threshold.reached',
    );
    const bindingKeys = bindingKeysRaw
      .split(',')
      .map((k) => k.trim())
      .filter(Boolean);

    try {
      const conn = await amqp.connect(url);
      this.connection = conn;
      const ch = await conn.createChannel();
      this.channel = ch;
      await ch.prefetch(prefetch);

      if (assertTopology && exchange?.trim()) {
        await ch.assertExchange(exchange.trim(), 'topic', { durable: true });
      }

      await ch.assertQueue(queue, { durable: true });

      if (assertTopology && exchange?.trim()) {
        for (const key of bindingKeys) {
          await ch.bindQueue(queue, exchange.trim(), key);
        }
      }

      const { consumerTag } = await ch.consume(
        queue,
        (msg: ConsumeMessage | null) => void this.onMessage(msg),
        { noAck: false },
      );
      this.consumerTag = consumerTag;

      this.logger.log(
        `Billing events consumer started (queue=${queue}${exchange ? `, exchange=${exchange}` : ''})`,
      );
    } catch (err) {
      this.logger.error(
        `Failed to start billing events consumer: ${err instanceof Error ? err.message : String(err)}`,
      );
      throw err;
    }
  }

  private async onMessage(msg: ConsumeMessage | null): Promise<void> {
    const ch = this.channel;
    if (!msg || !ch) return;

    const ack = (): void => {
      ch.ack(msg);
    };

    const nackRequeue = (): void => {
      ch.nack(msg, false, true);
    };

    let body: unknown;
    try {
      const text = msg.content.toString('utf8');
      body = JSON.parse(text) as unknown;
    } catch {
      this.logger.warn('Invalid JSON in billing event message — acking');
      ack();
      return;
    }

    const parsed = safeParseBillingBudgetThresholdReachedEventJson(body);
    if (!parsed.success) {
      this.logger.warn(
        `Billing event payload validation failed — acking: ${parsed.error.message}`,
      );
      ack();
      return;
    }

    const payload = parsed.data;
    const headers = msg.properties.headers ?? {};
    const subjectTypeRaw = headers['subjectType'];
    const subjectType = normalizeSubjectType(subjectTypeRaw);
    if (!subjectType) {
      this.logger.warn(`Missing/invalid subjectType header — acking (value=${String(subjectTypeRaw)})`);
      ack();
      return;
    }

    const userId = typeof headers['userId'] === 'string' ? headers['userId'] : undefined;
    const teamId = typeof headers['teamId'] === 'string' ? headers['teamId'] : undefined;
    const apiKeyId = typeof headers['apiKeyId'] === 'string' ? headers['apiKeyId'] : undefined;

    try {
      const result = await this.handler.handleBudgetThresholdReached({
        subjectType,
        userId,
        teamId,
        apiKeyId,
        payload,
      });

      this.logger.log(
        JSON.stringify({
          msg: 'billing_budget_threshold_processed',
          subjectType,
          userId,
          created: result.created,
          dedupeKey: result.dedupeKey,
        }),
      );
      ack();
    } catch (err) {
      this.logger.error(
        `billing_budget_threshold_processing_failed subjectType=${subjectType} userId=${String(userId)} ${err instanceof Error ? err.message : String(err)}`,
      );
      nackRequeue();
    }
  }

  async onModuleDestroy(): Promise<void> {
    const ch = this.channel;
    const conn = this.connection;
    if (ch && this.consumerTag) {
      try {
        await ch.cancel(this.consumerTag);
      } catch {
        /* ignore */
      }
    }
    if (ch) {
      try {
        await ch.close();
      } catch {
        /* ignore */
      }
    }
    this.channel = null;
    this.consumerTag = null;
    if (conn) {
      try {
        await conn.close();
      } catch {
        /* ignore */
      }
    }
    this.connection = null;
  }
}

function normalizeSubjectType(value: unknown): BillingSubjectType | null {
  if (value === 'USER' || value === 'TEAM' || value === 'API_KEY') {
    return value;
  }
  return null;
}

