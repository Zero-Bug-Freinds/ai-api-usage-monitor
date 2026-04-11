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
    ["connection", "content-encoding", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade"].map((s) => s.toLowerCase())
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

type RouteContext = { params: Promise<{ path?: string[] }> };

async function proxyExpenditure(request: Request, context: RouteContext): Promise<Response> {
  const token = getCookieValue(request.headers.get("cookie"), ACCESS_TOKEN_COOKIE);
  if (!token) {
    return jsonError(401, "로그인이 필요합니다");
  }

  const gatewayBase = envGatewayBaseUrl();
  if (!gatewayBase) {
    return jsonError(500, "서버 설정이 필요합니다 (API_GATEWAY_URL)");
  }

  const { path: segments } = await context.params;
  const pathParts = segments ?? [];
  if (pathParts.length === 0) {
    return jsonError(404, "지출 API 경로가 필요합니다");
  }

  const subPath = pathParts.map((s) => encodeURIComponent(s)).join("/");
  const url = new URL(request.url);
  const targetUrl = `${gatewayBase}/api/v1/expenditure/${subPath}${url.search}`;

  const method = request.method.toUpperCase();
  const outbound = new Headers();
  outbound.set("Authorization", `Bearer ${token}`);

  const accept = request.headers.get("accept");
  outbound.set("Accept", accept && accept.length > 0 ? accept : "application/json");

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

  const hasBody = method !== "GET" && method !== "HEAD";
  const init: RequestInit & { duplex?: "half" } = {
    method,
    headers: outbound,
    redirect: "manual",
  };

  if (hasBody) {
    const contentType = request.headers.get("content-type");
    if (contentType) {
      outbound.set("Content-Type", contentType);
    }
    init.body = request.body;
    init.duplex = "half";
  }

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

export async function GET(request: Request, context: RouteContext) {
  return proxyExpenditure(request, context);
}

export async function HEAD(request: Request, context: RouteContext) {
  return proxyExpenditure(request, context);
}
