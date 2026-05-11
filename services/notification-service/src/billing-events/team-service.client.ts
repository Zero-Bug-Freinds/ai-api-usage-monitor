import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

type TeamApiResponse<T> = {
  success: boolean;
  message: string;
  data: T | null;
};

type InternalTeamDetailResponse = {
  id: string;
  name: string;
  createdBy: string;
  createdAt: string;
};

@Injectable()
export class TeamServiceClient {
  constructor(private readonly config: ConfigService) {}

  async fetchTeamNameInternal(params: { teamId: number }): Promise<string | null> {
    const base = this.config.get<string>(
      'TEAM_SERVICE_BASE_URL',
      'http://localhost:8093',
    );
    const url = `${base.replace(/\/$/, '')}/internal/teams/${encodeURIComponent(String(params.teamId))}`;

    const timeoutMs =
      Number(this.config.get<string>('TEAM_SERVICE_TIMEOUT_MS', '5000')) || 5000;
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(url, {
        method: 'GET',
        headers: { accept: 'application/json' },
        signal: controller.signal,
      });
      const text = await res.text();
      const json: unknown = text ? (JSON.parse(text) as unknown) : null;
      if (!res.ok) {
        return null;
      }
      if (typeof json !== 'object' || json === null) {
        return null;
      }
      const body = json as TeamApiResponse<InternalTeamDetailResponse>;
      const name = body?.data?.name;
      return typeof name === 'string' && name.trim().length > 0 ? name.trim() : null;
    } catch {
      return null;
    } finally {
      clearTimeout(t);
    }
  }

  async fetchTeamMemberUserIds(params: {
    teamId: number;
    requesterUserId: string;
  }): Promise<string[]> {
    const base = this.config.get<string>(
      'TEAM_SERVICE_BASE_URL',
      'http://localhost:8093',
    );
    const url = `${base.replace(/\/$/, '')}/api/v1/teams/${encodeURIComponent(String(params.teamId))}/members`;

    const timeoutMs =
      Number(this.config.get<string>('TEAM_SERVICE_TIMEOUT_MS', '5000')) || 5000;
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(url, {
        method: 'GET',
        headers: {
          accept: 'application/json',
          'X-User-Id': params.requesterUserId,
          'X-Team-Id': String(params.teamId),
        },
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

      const body = json as TeamApiResponse<unknown>;
      if (!Array.isArray(body.data)) {
        return [];
      }
      return body.data
        .map((v) => (typeof v === 'string' ? v : null))
        .filter((v): v is string => typeof v === 'string' && v.trim().length > 0);
    } finally {
      clearTimeout(t);
    }
  }
}

