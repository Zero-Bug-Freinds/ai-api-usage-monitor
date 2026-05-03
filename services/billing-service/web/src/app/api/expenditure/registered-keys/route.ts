import { NextResponse } from "next/server";

const ACCESS_TOKEN_COOKIE = "access_token";

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
    ["connection", "content-encoding", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade"].map((s) =>
      s.toLowerCase()
    )
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

export async function GET(request: Request) {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE);
  if (!token) {
    return jsonError(401, "로그인이 필요합니다");
  }

  const gatewayBase = envGatewayBaseUrl();
  if (!gatewayBase) {
    return jsonError(500, "서버 설정이 필요합니다 (API_GATEWAY_URL)");
  }

  const targetUrl = `${gatewayBase}/api/identity/auth/external-keys`;

  const outbound = new Headers();
  outbound.set("Authorization", `Bearer ${token}`);
  outbound.set("Accept", "application/json");

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

  let upstream: Response;
  try {
    upstream = await fetch(targetUrl, {
      method: "GET",
      headers: outbound,
      redirect: "manual",
    });
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
