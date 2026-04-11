// Direct `process.env.NEXT_PUBLIC_*` only — Next does not inline dynamic `env.NEXT_PUBLIC_*` lookups.
export function getUsageDashboardHref(): string {
  const usageOrigin = (process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const identityOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const basePath = (process.env.NEXT_PUBLIC_USAGE_BASE_PATH ?? "/dashboard").replace(/\/$/, "");
  if (usageOrigin) return `${usageOrigin}${basePath}` || usageOrigin;
  if (identityOrigin) return `${identityOrigin}${basePath}` || identityOrigin;
  return basePath || "/";
}
