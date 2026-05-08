import type { NextApiRequest, NextApiResponse } from "next";

const ACCESS_TOKEN_COOKIE = "access_token";
const LOGGED_IN_COOKIE = "is_logged_in";

type ApiResponse<T> = {
  success: boolean;
  message: string;
  data: T | null;
};

type TokenResponse = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
};

function noStore(res: NextApiResponse) {
  res.setHeader("Cache-Control", "no-store");
}

function trimBaseUrl(url: string | undefined): string | null {
  const normalized = (url ?? "").trim();
  if (!normalized) return null;
  return normalized.replace(/\/+$/, "");
}

function resolveSwitchTeamUpstreamUrls(): string[] {
  const urls: string[] = [];
  const gatewayBase = trimBaseUrl(process.env.GATEWAY_URL) ?? trimBaseUrl(process.env.WEB_GATEWAY_URL);
  if (gatewayBase) urls.push(`${gatewayBase}/api/identity/auth/token/switch-team`);
  const identityBase = trimBaseUrl(process.env.IDENTITY_SERVICE_URL);
  if (identityBase) urls.push(`${identityBase}/api/auth/token/switch-team`);
  return urls;
}

function getCookieValue(cookieHeader: string | undefined, name: string): string | null {
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

function isSecureCookie(req: NextApiRequest): boolean {
  const configured = process.env.TEAM_WEB_SECURE_COOKIE?.trim().toLowerCase();
  if (configured === "true") return true;
  if (configured === "false") return false;
  const forwardedProto = req.headers["x-forwarded-proto"];
  if (typeof forwardedProto === "string") {
    return forwardedProto.split(",")[0]?.trim().toLowerCase() === "https";
  }
  return process.env.NODE_ENV === "production";
}

function resolveCookieDomain(req: NextApiRequest): string | undefined {
  const host = req.headers["x-forwarded-host"] ?? req.headers.host;
  const hostValue = Array.isArray(host) ? host[0] : host;
  if (!hostValue) return undefined;
  const hostname = hostValue.split(",")[0]?.trim().split(":")[0]?.toLowerCase();
  return hostname === "localhost" ? "localhost" : undefined;
}

function isTokenData(data: unknown): data is TokenResponse {
  return (
    typeof data === "object" &&
    data !== null &&
    typeof (data as { accessToken?: unknown }).accessToken === "string" &&
    typeof (data as { tokenType?: unknown }).tokenType === "string" &&
    typeof (data as { expiresInSeconds?: unknown }).expiresInSeconds === "number"
  );
}

function toMessage(upstreamJson: unknown, fallback: string): string {
  if (typeof upstreamJson === "object" && upstreamJson !== null) {
    const obj = upstreamJson as Record<string, unknown>;
    if (typeof obj.message === "string" && obj.message.trim() !== "") return obj.message;
    if (typeof obj.error === "string" && obj.error.trim() !== "") return obj.error;
    if (typeof obj.detail === "string" && obj.detail.trim() !== "") return obj.detail;
  }
  return fallback;
}

function decodeJwtUserIdHint(token: string): string | null {
  const parts = token.trim().split(".");
  if (parts.length < 2 || !parts[1]) return null;
  try {
    const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
    const decoded = Buffer.from(padded, "base64").toString("utf8");
    const json = JSON.parse(decoded) as Record<string, unknown>;
    const userId = json.userId;
    if (typeof userId === "string" && userId.trim() !== "") return userId.trim();
    if (typeof userId === "number" && Number.isFinite(userId)) return String(userId);
    const platformUserId = json.platformUserId;
    if (typeof platformUserId === "string" && platformUserId.trim() !== "") return platformUserId.trim();
    if (typeof platformUserId === "number" && Number.isFinite(platformUserId)) return String(platformUserId);
    const sub = json.sub;
    if (typeof sub === "string" && sub.trim() !== "") return sub.trim();
    return null;
  } catch {
    return null;
  }
}

function cookieHeader(name: string, value: string, maxAge: number, secure: boolean, domain?: string): string {
  const domainPart = domain ? `; Domain=${domain}` : "";
  const securePart = secure ? "; Secure" : "";
  return `${name}=${value}; Path=/; Max-Age=${maxAge}; SameSite=Lax${securePart}${domainPart}`;
}

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  if (req.method !== "POST") {
    noStore(res);
    res.status(405).json({ success: false, message: "Method Not Allowed", data: null });
    return;
  }

  const token = getCookieValue(req.headers.cookie, ACCESS_TOKEN_COOKIE);
  if (!token) {
    noStore(res);
    res.status(401).json({ success: false, message: "로그인이 필요합니다", data: null });
    return;
  }

  const upstreamUrls = resolveSwitchTeamUpstreamUrls();
  if (upstreamUrls.length === 0) {
    noStore(res);
    res.status(500).json({ success: false, message: "서버 설정이 필요합니다 (GATEWAY_URL 또는 IDENTITY_SERVICE_URL)", data: null });
    return;
  }

  const requestBody = JSON.stringify(req.body ?? {});
  let upstream: Response | null = null;
  let upstreamJson: unknown = null;
  let upstreamRaw = "";
  const userIdHint = decodeJwtUserIdHint(token);
  for (let i = 0; i < upstreamUrls.length; i += 1) {
    const upstreamUrl = upstreamUrls[i];
    const headers: Record<string, string> = {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      Accept: "application/json",
    };
    if (upstreamUrl.includes("/api/auth/token/switch-team") && userIdHint) {
      headers["X-User-Id"] = userIdHint;
      headers["X-Platform-User-Id"] = userIdHint;
    }
    try {
      upstream = await fetch(upstreamUrl, { method: "POST", headers, body: requestBody });
    } catch {
      upstream = null;
      upstreamJson = null;
      upstreamRaw = "";
      continue;
    }
    try {
      upstreamRaw = await upstream.text();
      upstreamJson = upstreamRaw ? (JSON.parse(upstreamRaw) as unknown) : null;
    } catch {
      upstreamJson = null;
    }
    const isGatewayIdentityCall = upstreamUrl.includes("/api/identity/auth/token/switch-team");
    const isRetriableFailure =
      !upstream.ok &&
      i < upstreamUrls.length - 1 &&
      ([404, 500, 502, 503, 504].includes(upstream.status) || (upstream.status === 400 && isGatewayIdentityCall));
    if (!isRetriableFailure) break;
  }

  if (!upstream) {
    noStore(res);
    res.status(502).json({ success: false, message: "인증 서비스에 연결할 수 없습니다", data: null });
    return;
  }

  if (!upstream.ok) {
    noStore(res);
    res
      .status(upstream.status)
      .json({ success: false, message: toMessage(upstreamJson, `요청 처리에 실패했습니다 (HTTP ${upstream.status})`), data: null });
    return;
  }

  const body = upstreamJson as ApiResponse<TokenResponse>;
  if (!body?.success || !isTokenData(body.data)) {
    noStore(res);
    res.status(502).json({ success: false, message: "팀 전환 응답 형식이 올바르지 않습니다", data: null });
    return;
  }

  const secure = isSecureCookie(req);
  const domain = resolveCookieDomain(req);
  res.setHeader("Set-Cookie", [
    cookieHeader(ACCESS_TOKEN_COOKIE, body.data.accessToken, body.data.expiresInSeconds, secure, domain),
    cookieHeader(LOGGED_IN_COOKIE, "true", body.data.expiresInSeconds, secure, domain),
  ]);
  noStore(res);
  res.status(200).json({ success: true, message: body.message || "팀 전환이 완료되었습니다", data: null });
}
