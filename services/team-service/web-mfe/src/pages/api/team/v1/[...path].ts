import type { NextApiRequest, NextApiResponse } from "next";
import { resolveGatewayBaseUrl } from "../../../../lib/env/gateway-base-url";

const ACCESS_TOKEN_COOKIE = "access_token";

function noStore(res: NextApiResponse) {
  res.setHeader("Cache-Control", "no-store");
}

function jsonError(res: NextApiResponse, status: number, message: string) {
  noStore(res);
  res.status(status).json({ success: false, message, data: null });
}

function envGatewayBaseUrl(): string | null {
  const configured = resolveGatewayBaseUrl();
  if (configured) return configured;
  if (process.env.NODE_ENV === "development") {
    console.warn(
      "[team-web-mfe] GATEWAY_URL/WEB_GATEWAY_URL is not set; using http://127.0.0.1:8888. " +
        "Set GATEWAY_URL or WEB_GATEWAY_URL in .env.local when gateway uses a different address.",
    );
    return "http://127.0.0.1:8888";
  }
  return null;
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

function resolveAuthorizationHeader(req: NextApiRequest): string | null {
  const incomingAuth = req.headers.authorization;
  if (typeof incomingAuth === "string" && incomingAuth.trim().length > 0) {
    return incomingAuth;
  }
  const token = getCookieValue(req.headers.cookie, ACCESS_TOKEN_COOKIE);
  return token ? `Bearer ${token}` : null;
}

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  const authorization = resolveAuthorizationHeader(req);
  if (!authorization) return jsonError(res, 401, "로그인이 필요합니다");

  const gatewayBase = envGatewayBaseUrl();
  if (!gatewayBase) return jsonError(res, 500, "서버 설정이 필요합니다 (GATEWAY_URL)");

  const pathParts = req.query.path;
  const encodedPath =
    typeof pathParts === "string"
      ? encodeURIComponent(pathParts)
      : Array.isArray(pathParts)
        ? pathParts.map((s) => encodeURIComponent(String(s))).join("/")
        : "";
  const query = req.url?.includes("?") ? req.url.slice(req.url.indexOf("?")) : "";
  const targetUrl = `${gatewayBase}/api/team/v1/${encodedPath}${query}`;

  const headers = new Headers();
  headers.set("Authorization", authorization);
  headers.set("Accept", req.headers.accept ?? "application/json");
  const contentType = req.headers["content-type"];
  if (typeof contentType === "string" && contentType.length > 0) {
    headers.set("Content-Type", contentType);
  }

  const method = req.method?.toUpperCase() ?? "GET";
  const hasBody = method !== "GET" && method !== "HEAD";
  const init: RequestInit = { method, headers, redirect: "manual" };
  if (hasBody && req.body !== undefined) {
    init.body = typeof req.body === "string" ? req.body : JSON.stringify(req.body);
  }

  let upstream: Response;
  try {
    upstream = await fetch(targetUrl, init);
  } catch {
    return jsonError(res, 502, "팀 서비스에 연결할 수 없습니다");
  }

  noStore(res);
  res.status(upstream.status);
  const ct = upstream.headers.get("content-type");
  if (ct) res.setHeader("Content-Type", ct);
  const text = await upstream.text();
  res.send(text);
}
