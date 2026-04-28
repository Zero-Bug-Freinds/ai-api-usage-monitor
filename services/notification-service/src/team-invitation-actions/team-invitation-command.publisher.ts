import { Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Channel } from 'amqplib';
import * as amqp from 'amqplib';
import type {
  TeamInvitationDecision,
  TeamInvitationDecisionCommand,
} from './team-invitation-command.types';
import { randomUUID } from 'crypto';

type AmqpConnection = Awaited<ReturnType<typeof amqp.connect>>;

@Injectable()
export class TeamInvitationCommandPublisher implements OnModuleDestroy {
  private readonly logger = new Logger(TeamInvitationCommandPublisher.name);
  private connection: AmqpConnection | null = null;
  private channel: Channel | null = null;

  constructor(private readonly config: ConfigService) {}

  async publishDecision(params: {
    invitationId: string;
    inviteeUserId: string;
    decision: TeamInvitationDecision;
    correlationId?: string;
  }): Promise<{ published: boolean; eventId?: string }> {
    const enabled =
      this.config.get<string>('TEAM_INVITATION_COMMAND_PUBLISHER_ENABLED', 'false') ===
        'true' ||
      this.config.get<string>('TEAM_INVITATION_COMMAND_PUBLISHER_ENABLED', 'false') === '1';
    if (!enabled) return { published: false };

    const url = this.config.get<string>('RABBITMQ_URL');
    if (!url?.trim()) {
      this.logger.warn(
        'TEAM_INVITATION_COMMAND_PUBLISHER_ENABLED is true but RABBITMQ_URL is empty — skipping publish',
      );
      return { published: false };
    }

    const exchange = this.config.get<string>(
      'TEAM_COMMANDS_EXCHANGE_NAME',
      'team.commands',
    );
    const routingKey = this.config.get<string>(
      'TEAM_INVITATION_DECISION_ROUTING_KEY',
      'team.invitation.decision',
    );
    const assertTopology =
      this.config.get<string>('TEAM_COMMANDS_ASSERT_TOPOLOGY', 'false') === 'true' ||
      this.config.get<string>('TEAM_COMMANDS_ASSERT_TOPOLOGY', 'false') === '1';

    const eventId = randomUUID();
    const occurredAt = new Date().toISOString();
    const command: TeamInvitationDecisionCommand = {
      schemaVersion: 1,
      eventType: 'TEAM_INVITATION_DECISION_COMMAND',
      eventId,
      occurredAt,
      invitationId: params.invitationId,
      inviteeUserId: params.inviteeUserId,
      decision: params.decision,
      ...(params.correlationId ? { correlationId: params.correlationId } : {}),
    };

    const dedupeKey = buildDedupeKey(command);

    try {
      const ch = await this.getOrCreateChannel(url);
      if (assertTopology) {
        await ch.assertExchange(exchange, 'topic', { durable: true });
      }

      const published = ch.publish(
        exchange,
        routingKey,
        Buffer.from(JSON.stringify(command), 'utf8'),
        {
          contentType: 'application/json',
          persistent: true,
          messageId: eventId,
          headers: {
            eventType: command.eventType,
            dedupeKey,
            invitationId: command.invitationId,
            decision: command.decision,
            inviteeUserId: command.inviteeUserId,
            ...(command.correlationId ? { correlationId: command.correlationId } : {}),
          },
        },
      );

      if (!published) {
        this.logger.warn(
          `Publish returned false (exchange=${exchange} routingKey=${routingKey})`,
        );
      }

      return { published: true, eventId };
    } catch (err) {
      this.logger.error(
        `Failed to publish team invitation decision command (invitationId=${params.invitationId} decision=${params.decision}): ${err instanceof Error ? err.message : String(err)}`,
      );
      return { published: false };
    }
  }

  private async getOrCreateChannel(url: string): Promise<Channel> {
    if (this.channel) return this.channel;

    const conn = await amqp.connect(url);
    this.connection = conn;
    const ch = await conn.createChannel();
    this.channel = ch;
    return ch;
  }

  async onModuleDestroy(): Promise<void> {
    const ch = this.channel;
    const conn = this.connection;
    if (ch) {
      try {
        await ch.close();
      } catch {
        /* ignore */
      }
    }
    this.channel = null;
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

function buildDedupeKey(cmd: TeamInvitationDecisionCommand): string {
  return `team-command:invitation:${cmd.invitationId}:${cmd.decision}:${cmd.inviteeUserId}`;
}

