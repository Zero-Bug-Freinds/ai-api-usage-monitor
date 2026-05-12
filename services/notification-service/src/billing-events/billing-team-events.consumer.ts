import {
  Injectable,
  Logger,
  OnApplicationBootstrap,
  OnModuleDestroy,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Channel, ConsumeMessage } from 'amqplib';
import * as amqp from 'amqplib';
import { safeParseBillingTeamBudgetThresholdReachedEventJson } from './billing-team-budget-threshold-event.schema';
import { BillingTeamInAppNotificationHandlerService } from './billing-team-in-app-notification-handler.service';

type AmqpConnection = Awaited<ReturnType<typeof amqp.connect>>;

@Injectable()
export class BillingTeamEventsConsumer
  implements OnApplicationBootstrap, OnModuleDestroy
{
  private readonly logger = new Logger(BillingTeamEventsConsumer.name);
  private connection: AmqpConnection | null = null;
  private channel: Channel | null = null;
  private consumerTag: string | null = null;

  constructor(
    private readonly config: ConfigService,
    private readonly handler: BillingTeamInAppNotificationHandlerService,
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    const enabled = this.config.get<string>('BILLING_TEAM_EVENTS_CONSUMER_ENABLED', 'true');
    if (enabled !== 'true' && enabled !== '1') {
      this.logger.log(
        'Billing team events consumer disabled (BILLING_TEAM_EVENTS_CONSUMER_ENABLED)',
      );
      return;
    }

    const url = this.config.get<string>('RABBITMQ_URL');
    if (!url?.trim()) {
      this.logger.warn(
        'BILLING_TEAM_EVENTS_CONSUMER_ENABLED is true but RABBITMQ_URL is empty — consumer not started',
      );
      return;
    }

    const queue = this.config.get<string>(
      'BILLING_TEAM_EVENTS_QUEUE_NAME',
      'notification.billing.team.events',
    );
    const prefetch =
      Number(this.config.get<string>('BILLING_TEAM_EVENTS_PREFETCH', '10')) || 10;
    const assertTopologyRaw = this.config.get<string>(
      'BILLING_TEAM_EVENTS_ASSERT_TOPOLOGY',
      'true',
    );
    const assertTopology = assertTopologyRaw === 'true' || assertTopologyRaw === '1';
    const exchange = this.config.get<string>('BILLING_TEAM_EVENTS_EXCHANGE_NAME', 'billing.events');
    const bindingKeysRaw = this.config.get<string>(
      'BILLING_TEAM_EVENTS_BINDING_KEYS',
      'billing.team.budget.threshold.reached',
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
        `Billing team events consumer started (queue=${queue}${exchange ? `, exchange=${exchange}` : ''})`,
      );
    } catch (err) {
      this.logger.error(
        `Failed to start billing team events consumer: ${err instanceof Error ? err.message : String(err)}`,
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
      this.logger.warn('Invalid JSON in billing team event message — acking');
      ack();
      return;
    }

    const parsed = safeParseBillingTeamBudgetThresholdReachedEventJson(body);
    if (!parsed.success) {
      this.logger.warn(
        `Billing team event payload validation failed — acking: ${parsed.error.message}`,
      );
      ack();
      return;
    }

    try {
      const result = await this.handler.handleTeamBudgetThresholdReached({
        payload: parsed.data,
      });
      this.logger.log(
        JSON.stringify({
          msg: 'billing_team_budget_threshold_processed',
          teamId: parsed.data.teamId,
          createdCount: result.createdCount,
          skippedCount: result.skippedCount,
        }),
      );
      ack();
    } catch (err) {
      this.logger.error(
        `billing_team_budget_threshold_processing_failed teamId=${String(parsed.data.teamId)} ${err instanceof Error ? err.message : String(err)}`,
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

