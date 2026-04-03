"use client"

type RouterLike = { replace: (href: string) => void }

/**
 * Usage `web/`가 다른 오리진(로컬 분리 포트 등)일 때 대시보드로만 절대 URL 이동.
 * 단일 도메인 엣지에서는 `NEXT_PUBLIC_USAGE_WEB_ORIGIN`을 비워 동일 오리진 `router.replace`만 쓴다.
 */
export function navigateAfterLogin(nextPath: string, router: RouterLike): void {
  const usageOrigin = (process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ?? "").replace(/\/$/, "")
  if (usageOrigin && nextPath.startsWith("/dashboard")) {
    window.location.assign(`${usageOrigin}${nextPath}`)
    return
  }
  router.replace(nextPath)
}
