"use client"

type RouterLike = { replace: (href: string) => void }

/**
 * Usage 웹 앱으로 가는 링크용 href.
 * 단일 도메인(엣지)에서는 `path`만 쓰고, 로컬 등 분리 호스트면 `NEXT_PUBLIC_USAGE_WEB_ORIGIN`을 붙인다.
 */
export function usageAppHref(path: string): string {
  const usageOrigin = (process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ?? "").replace(/\/$/, "")
  const normalized = path.startsWith("/") ? path : `/${path}`
  return usageOrigin ? `${usageOrigin}${normalized}` : normalized
}

/**
 * `/dashboard`는 Usage `web/`(별도 Next 앱)이 담당하므로 항상 전체 네비게이션으로 이동한다.
 * 로컬에서 포트가 다르면 `NEXT_PUBLIC_USAGE_WEB_ORIGIN`으로 오리진을 붙인다.
 */
export function navigateAfterLogin(nextPath: string, router: RouterLike): void {
  if (nextPath.startsWith("/dashboard")) {
    window.location.assign(usageAppHref(nextPath))
    return
  }
  router.replace(nextPath)
}
