import { NextResponse } from "next/server";

const ACCESS_TOKEN_COOKIE = "access_token";
const LONG_MAX = BigInt("9223372036854775807");
const LONG_MIN_EXCLUSIVE = BigInt(0);

function envTeamBffBaseUrl(): string | null {
  const raw = process.env.BILLING_TEAM_BFF_BASE_URL;
  if (!raw) return null;
  const trimmed = raw.trim().replace(/\/+$/, "");
  return trimmed.length > 0 ? trimmed : null;
}

function originForServerSideFetch(request: Request): string {
  const forwardedProto = request.headers.get("x-forwarded-proto");
  const forwardedHost = request.headers.get("x-forwarded-host");
  const host = (forwardedHost ?? request.headers.get("host") ?? "").split(",")[0]?.trim();
  if (host && host.length > 0) {
    const proto = (forwardedProto ?? "http").split(",")[0]?.trim() || "http";
    return `${proto}://${host}`;
  }
  return new URL(request.url).origin;
}

function isGatewayDevMode(): boolean {
  const v = process.env.GATEWAY_DEV_MODE?.toLowerCase();
  return v === "true" || v === "1";
}

function noStoreHeaders(): HeadersInit {
  return { "Cache-Control": "no-store" };
}

function envGatewayBaseUrl(): string | null {
  const url = process.env.API_GATEWAY_URL;
  if (!url) return null;
  return url.replace(/\/+$/, "");
}

function envIdentityBaseUrl(): string | null {
  const url = process.env.IDENTITY_SERVICE_URL;
  if (!url) return null;
  return url.replace(/\/+$/, "");
}

function getCookieValue(cookieHeader: string | null, name: string): string | null {
  if (!cookieHeader) return null;
  const prefix = `${name}=`;
  for (const part of cookieHeader.split(";")) {
    const trimmed = part.trim();
    if (trimmed.startsWith(prefix)) {
      const value = trimmed.slice(prefix.length);
      return value.length > 0 ? value : null;
    }
  }
  return null;
}

async function fetchSessionEmailForDev(identityBaseUrl: string, token: string): Promise<string | null> {
  let res: Response;
  try {
    res = await fetch(`${identityBaseUrl}/api/auth/session`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
    });
  } catch {
    return null;
  }
  let body: unknown;
  try {
    body = await res.json();
  } catch {
    return null;
  }
  if (!res.ok) return null;
  if (typeof body !== "object" || body === null) return null;
  const data = (body as { data?: unknown }).data;
  if (typeof data !== "object" || data === null) return null;
  const email = (data as { email?: unknown }).email;
  return typeof email === "string" && email.length > 0 ? email : null;
}

function filterUpstreamResponseHeaders(upstream: Response): Headers {
  const out = new Headers();
  const skip = new Set(
    [
      "connection",
      "content-encoding",
      "keep-alive",
      "proxy-authenticate",
      "proxy-authorization",
      "te",
      "trailers",
      "transfer-encoding",
      "upgrade",
    ].map((s) => s.toLowerCase())
  );
  upstream.headers.forEach((value, key) => {
    if (skip.has(key.toLowerCase())) return;
    out.append(key, value);
  });
  return out;
}

function jsonError(status: number, message: string) {
  return NextResponse.json({ message }, { status, headers: noStoreHeaders() });
}

function parseLongString(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  try {
    const n = BigInt(trimmed);
    if (n <= LONG_MIN_EXCLUSIVE || n > LONG_MAX) return null;
  } catch {
    return null;
  }
  return trimmed;
}

function normalizeUserIds(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  const out: string[] = [];
  const seen = new Set<string>();
  for (const v of value) {
    if (typeof v !== "string") continue;
    const s = v.trim();
    if (!s) continue;
    if (seen.has(s)) continue;
    seen.add(s);
    out.push(s);
  }
  return out;
}

async function fetchTeamMemberUserIds(request: Request, teamId: string): Promise<string[] | null> {
  const origin = envTeamBffBaseUrl() ?? originForServerSideFetch(request);
  const url = new URL(`/api/team/v1/teams/${encodeURIComponent(teamId)}/members`, origin);
  const cookie = request.headers.get("cookie");
  let res: Response;
  try {
    res = await fetch(url.toString(), {
      method: "GET",
      headers: {
        Accept: "application/json",
        ...(cookie ? { cookie } : {}),
      },
      cache: "no-store",
    });
  } catch {
    return null;
  }
  let json: unknown;
  try {
    json = await res.json();
  } catch {
    return null;
  }
  if (!res.ok) return null;
  if (typeof json !== "object" || json === null) return null;
  const rec = json as Record<string, unknown>;
  if (rec.success !== true || !Array.isArray(rec.data)) return null;
  return rec.data.filter((x): x is string => typeof x === "string" && x.trim().length > 0).map((x) => x.trim());
}

export async function POST(request: Request) {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE);
  if (!token) {
    return jsonError(401, "로그인이 필요합니다");
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return jsonError(400, "요청 바디가 JSON 형식이어야 합니다");
  }
  if (typeof body !== "object" || body === null) {
    return jsonError(400, "요청 바디가 올바르지 않습니다");
  }

  const teamId = parseLongString((body as { teamId?: unknown }).teamId);
  if (!teamId) {
    return jsonError(400, "teamId는 숫자 문자열이어야 합니다");
  }

  const monthStartDate = (body as { monthStartDate?: unknown }).monthStartDate;
  if (typeof monthStartDate !== "string" || monthStartDate.trim().length === 0) {
    return jsonError(400, "monthStartDate가 필요합니다");
  }

  const userIds = normalizeUserIds((body as { userIds?: unknown }).userIds);
  if (userIds.length === 0) {
    return jsonError(400, "userIds가 필요합니다");
  }

  const members = await fetchTeamMemberUserIds(request, teamId);
  if (!members) {
    return jsonError(502, "팀 멤버 조회에 실패했습니다");
  }
  const memberSet = new Set(members);

  const nonMembers = userIds.filter((id) => !memberSet.has(id));
  if (nonMembers.length > 0) {
    return jsonError(403, "팀 멤버가 아닌 userId가 포함되어 있습니다");
  }

  const gatewayBase = envGatewayBaseUrl();
  if (!gatewayBase) {
    return jsonError(500, "서버 설정이 필요합니다 (API_GATEWAY_URL)");
  }

  const outbound = new Headers();
  outbound.set("Authorization", `Bearer ${token}`);
  outbound.set("Accept", "application/json");
  outbound.set("Content-Type", "application/json");

  const correlation = request.headers.get("x-correlation-id");
  if (correlation && correlation.length > 0) {
    outbound.set("X-Correlation-Id", correlation);
  }

  if (isGatewayDevMode()) {
    const identityBase = envIdentityBaseUrl();
    if (!identityBase) {
      return jsonError(500, "서버 설정이 필요합니다 (GATEWAY_DEV_MODE 사용 시 IDENTITY_SERVICE_URL)");
    }
    const userId = await fetchSessionEmailForDev(identityBase, token);
    if (!userId) {
      return jsonError(401, "게이트웨이 개발 모드에서 사용자 식별에 실패했습니다");
    }
    outbound.set("X-User-Id", userId);
  }

  const targetUrl = `${gatewayBase}/api/v1/expenditure/team/month-rollup`;
  const init: RequestInit = {
    method: "POST",
    headers: outbound,
    redirect: "manual",
    body: JSON.stringify({ userIds, monthStartDate: monthStartDate.trim() }),
  };

  let upstream: Response;
  try {
    upstream = await fetch(targetUrl, init);
  } catch {
    return jsonError(502, "API 게이트웨이에 연결할 수 없습니다");
  }

  const resHeaders = filterUpstreamResponseHeaders(upstream);
  resHeaders.set("Cache-Control", "no-store");

  return new NextResponse(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: resHeaders,
  });
}

