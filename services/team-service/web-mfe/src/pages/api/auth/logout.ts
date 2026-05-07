import type { NextApiRequest, NextApiResponse } from "next";

const ACCESS_TOKEN_COOKIE = "access_token";
const LOGGED_IN_COOKIE = "is_logged_in";

function noStore(res: NextApiResponse) {
  res.setHeader("Cache-Control", "no-store");
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

function envIdentityBaseUrl(): string | null {
  const url = process.env.IDENTITY_SERVICE_URL;
  return url ? url.replace(/\/+$/, "") : null;
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

function clearCookieHeader(name: string, secure: boolean, domain?: string): string {
  const domainPart = domain ? `; Domain=${domain}` : "";
  const securePart = secure ? "; Secure" : "";
  return `${name}=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Lax${securePart}${domainPart}`;
}

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  if (req.method !== "POST") {
    noStore(res);
    res.status(405).json({ success: false, message: "Method Not Allowed", data: null });
    return;
  }

  const token = getCookieValue(req.headers.cookie, ACCESS_TOKEN_COOKIE);
  const identityBaseUrl = envIdentityBaseUrl();
  if (identityBaseUrl) {
    try {
      const headers: Record<string, string> = { Accept: "application/json" };
      if (token) headers.Authorization = `Bearer ${token}`;
      await fetch(`${identityBaseUrl}/api/auth/logout`, { method: "POST", headers });
    } catch {
      // Upstream failure must not block cookie cleanup.
    }
  }

  const secure = isSecureCookie(req);
  const domain = resolveCookieDomain(req);
  res.setHeader("Set-Cookie", [
    clearCookieHeader(ACCESS_TOKEN_COOKIE, secure, domain),
    clearCookieHeader(LOGGED_IN_COOKIE, secure, domain),
  ]);
  noStore(res);
  res.status(200).json({ success: true, message: "로그아웃되었습니다", data: null });
}
