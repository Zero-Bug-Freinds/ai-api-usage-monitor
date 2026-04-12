// Direct `process.env.NEXT_PUBLIC_*` only — Next does not inline dynamic `env.NEXT_PUBLIC_*` lookups.

function usageDashboardPath(): string {
  const raw = (process.env.NEXT_PUBLIC_USAGE_BASE_PATH ?? "/dashboard").replace(/\/$/, "");
  const path = raw.startsWith("/") ? raw : `/${raw}`;
  return path || "/dashboard";
}

/** Identity URL의 origin만 (비교용). */
function identityWebOrigin(): string {
  const raw = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "");
  if (!raw) return "";
  try {
    return new URL(raw).origin;
  } catch {
    return "";
  }
}

/**
 * 사용량 대시보드로 가는 href.
 * Identity(:3000) 뒤에서 `/billing` 로 프록시될 때, 상대 경로나 base 태그 때문에 `/billing/dashboard` 로 틀어지는 것을 막기 위해
 * 같은 오리진이면 `window.location.origin` + 경로로 절대 URL을 만든다.
 */
export function getUsageDashboardHref(): string {
  const path = usageDashboardPath();
  const usageOrigin = (process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const identityOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "");

  if (usageOrigin) {
    try {
      return new URL(path, usageOrigin).href;
    } catch {
      return `${usageOrigin}${path}` || usageOrigin;
    }
  }

  if (typeof window !== "undefined") {
    const idOrigin = identityWebOrigin();
    if (idOrigin && window.location.origin === idOrigin) {
      return new URL(path, window.location.origin).href;
    }
  }

  if (identityOrigin) {
    try {
      return new URL(path, identityOrigin).href;
    } catch {
      return `${identityOrigin}${path}` || identityOrigin;
    }
  }

  return path || "/";
}
