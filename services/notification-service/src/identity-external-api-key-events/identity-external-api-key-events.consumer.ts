import {
  Injectable,
  Logger,
  OnApplicationBootstrap,
  OnModuleDestroy,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Channel, ConsumeMessage } from 'amqplib';
import * as amqp from 'amqplib';
import { IdentityExternalApiKeyEventTypes } from './identity-external-api-key-event-types';
import { IdentityExternalApiKeyInAppHandlerService } from './identity-external-api-key-in-app-handler.service';
import {
  isIdentityExternalApiKeyBudgetEventType,
  safeParseExternalApiKeyDeletedJson,
  safeParseExternalApiKeyStatusChangedJson,
} from './identity-external-api-key-event.schema';

type AmqpConnection = Awaited<ReturnType<typeof amqp.connect>>;

@Injectable()
export class IdentityExternalApiKeyEventsConsumer
  implements OnApplicationBootstrap, OnModuleDestroy
{
  private readonly logger = new Logger(IdentityExternalApiKeyEventsConsumer.name);
  private connection: AmqpConnection | null = null;
  private channel: Channel | null = null;
  private consumerTag: string | null = null;

  constructor(
    private readonly config: ConfigService,
    private readonly handler: IdentityExternalApiKeyInAppHandlerService,
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    const enabled = this.config.get<string>(
      'IDENTITY_EXTERNAL_API_KEY_EVENTS_CONSUMER_ENABLED',
      'false',
    );
    if (enabled !== 'true' && enabled !== '1') {
      this.logger.log(
        'Identity external API key events consumer disabled (IDENTITY_EXTERNAL_API_KEY_EVENTS_CONSUMER_ENABLED)',
      );
      return;
    }

    const url = this.config.get<string>('RABBITMQ_URL');
    if (!url?.trim()) {
      this.logger.warn(
        'IDENTITY_EXTERNAL_API_KEY_EVENTS_CONSUMER_ENABLED is true but RABBITMQ_URL is empty — consumer not started',
      );
      return;
    }

    const queue = this.config.get<string>(
      'IDENTITY_EXTERNAL_API_KEY_EVENTS_QUEUE_NAME',
      'notification.identity.external-api-key.queue',
    );
    const prefetch =
      Number(this.config.get<string>('IDENTITY_EXTERNAL_API_KEY_EVENTS_PREFETCH', '10')) || 10;
    const assertTopologyRaw = this.config.get<string>(
      'IDENTITY_EXTERNAL_API_KEY_EVENTS_ASSERT_TOPOLOGY',
      'true',
    );
    const assertTopology = assertTopologyRaw === 'true' || assertTopologyRaw === '1';
    const exchange = this.config.get<string>(
      'IDENTITY_EXTERNAL_API_KEY_EVENTS_EXCHANGE_NAME',
      'identity.events',
    );
    const bindingKeysRaw = this.config.get<string>(
      'IDENTITY_EXTERNAL_API_KEY_EVENTS_BINDING_KEYS',
      'identity.external-api-key.status-changed',
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
        `Identity external API key events consumer started (queue=${queue}${exchange ? `, exchange=${exchange}` : ''})`,
      );
    } catch (err) {
      this.logger.error(
        `Failed to start identity external API key events consumer: ${err instanceof Error ? err.message : String(err)}`,
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
      this.logger.warn('Invalid JSON in identity external API key event message — acking');
      ack();
      return;
    }

    if (typeof body !== 'object' || body === null) {
      this.logger.warn('Identity external API key event body is not an object — acking');
      ack();
      return;
    }

    const root = body as Record<string, unknown>;
    const eventTypeField = root.eventType;

    try {
      // 1) Physical delete — same order as usage-service / agent-service
      if (eventTypeField === IdentityExternalApiKeyEventTypes.EXTERNAL_API_KEY_DELETED) {
        const parsed = safeParseExternalApiKeyDeletedJson(body);
        if (!parsed.success) {
          this.logger.warn(
            `Identity delete event validation failed — acking: ${parsed.error.message}`,
          );
          ack();
          return;
        }
        const result = await this.handler.handleDeleted(parsed.data);
        this.logger.log(
          JSON.stringify({
            msg: 'identity_external_api_key_deleted_processed',
            created: result.created,
            apiKeyId: parsed.data.apiKeyId,
          }),
        );
        ack();
        return;
      }

      // 2) Budget — must precede schemaVersion branch (budget payloads include schemaVersion)
      if (typeof eventTypeField === 'string' && isIdentityExternalApiKeyBudgetEventType(eventTypeField)) {
        ack();
        return;
      }

      // 3) Status / alias
      if (Object.prototype.hasOwnProperty.call(root, 'schemaVersion')) {
        const parsed = safeParseExternalApiKeyStatusChangedJson(body);
        if (!parsed.success) {
          this.logger.warn(
            `Identity status event validation failed — acking: ${parsed.error.message}`,
          );
          ack();
          return;
        }
        const result = await this.handler.handleStatusChanged(parsed.data);
        this.logger.log(
          JSON.stringify({
            msg: 'identity_external_api_key_status_processed',
            created: result.created,
            keyId: parsed.data.keyId,
            status: parsed.data.status,
          }),
        );
        ack();
        return;
      }

      this.logger.warn(
        'Unrecognized identity external API key event payload (expected delete eventType, budget eventType, or schemaVersion) — acking',
      );
      ack();
    } catch (err) {
      this.logger.error(
        `identity_external_api_key_event_processing_failed ${err instanceof Error ? err.message : String(err)}`,
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
