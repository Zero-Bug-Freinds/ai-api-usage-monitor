// Direct `process.env.NEXT_PUBLIC_*` only — Next does not inline dynamic `env.NEXT_PUBLIC_*` lookups.
export function getUsageDashboardHref(): string {
  const usageOrigin = (process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const identityOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const basePath = (process.env.NEXT_PUBLIC_USAGE_BASE_PATH ?? "/dashboard").replace(/\/$/, "");
  const path = basePath.startsWith("/") ? basePath : `/${basePath}`;

  if (usageOrigin) {
    try {
      return new URL(path, usageOrigin).href;
    } catch {
      return `${usageOrigin}${path}` || usageOrigin;
    }
  }
  if (identityOrigin) {
    try {
      // Absolute `path` ignores a mistaken `/billing` (or other) suffix on the identity URL.
      return new URL(path, identityOrigin).href;
    } catch {
      return `${identityOrigin}${path}` || identityOrigin;
    }
  }
  return path || "/";
}
