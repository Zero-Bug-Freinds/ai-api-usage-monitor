import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

type TeamApiResponse<T> = {
  success: boolean;
  message: string;
  data: T | null;
};

@Injectable()
export class TeamServiceInternalClient {
  constructor(private readonly config: ConfigService) {}

  async acceptInvitation(params: { invitationId: string; inviteeUserId: string }) {
    return await this.postDecision(params.invitationId, {
      inviteeUserId: params.inviteeUserId,
      decision: 'ACCEPT',
    });
  }

  async rejectInvitation(params: { invitationId: string; inviteeUserId: string }) {
    return await this.postDecision(params.invitationId, {
      inviteeUserId: params.inviteeUserId,
      decision: 'REJECT',
    });
  }

  private async postDecision(
    invitationId: string,
    body: { inviteeUserId: string; decision: 'ACCEPT' | 'REJECT' },
  ): Promise<TeamApiResponse<unknown>> {
    const base = this.config.get<string>(
      'TEAM_SERVICE_INTERNAL_BASE_URL',
      'http://localhost:8093',
    );
    const url = `${base.replace(/\/$/, '')}/internal/v1/team-invitations/${encodeURIComponent(invitationId)}/decision`;

    const timeoutMs = Number(this.config.get<string>('TEAM_SERVICE_INTERNAL_TIMEOUT_MS', '5000')) || 5000;
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'content-type': 'application/json', accept: 'application/json' },
        body: JSON.stringify(body),
        signal: controller.signal,
      });
      const text = await res.text();
      const json: unknown = text ? (JSON.parse(text) as unknown) : null;
      if (!res.ok) {
        const msg =
          typeof json === 'object' && json !== null && 'message' in json
            ? String((json as { message?: unknown }).message ?? '')
            : `HTTP ${res.status}`;
        throw new Error(msg);
      }
      if (typeof json !== 'object' || json === null) {
        throw new Error('Invalid response from team-service');
      }
      return json as TeamApiResponse<unknown>;
    } finally {
      clearTimeout(t);
    }
  }
}

