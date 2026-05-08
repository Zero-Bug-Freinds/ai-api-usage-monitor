import type { NextApiRequest, NextApiResponse } from "next";

const ACCESS_TOKEN_COOKIE = "access_token";

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

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  if (req.method !== "GET") {
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
  const identityBase = envIdentityBaseUrl();
  if (!identityBase) {
    noStore(res);
    res.status(500).json({ success: false, message: "서버 설정이 필요합니다 (IDENTITY_SERVICE_URL)", data: null });
    return;
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${identityBase}/api/auth/session`, {
      method: "GET",
      headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
    });
  } catch {
    noStore(res);
    res.status(502).json({ success: false, message: "인증 서비스에 연결할 수 없습니다", data: null });
    return;
  }

  let body: unknown = null;
  try {
    body = await upstream.json();
  } catch {
    body = null;
  }
  noStore(res);
  if (body && typeof body === "object") {
    res.status(upstream.status).json(body);
    return;
  }
  res.status(502).json({ success: false, message: "요청 처리에 실패했습니다", data: null });
}
