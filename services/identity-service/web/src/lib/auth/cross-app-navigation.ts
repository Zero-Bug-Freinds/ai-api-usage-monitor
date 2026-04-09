"use client"

type RouterLike = { replace: (href: string) => void }

/**
 * Usage 웹 앱으로 가는 링크용 href.
 * 현재는 Identity 웹(3000)에서 `/dashboard` 경로로 진입하도록 고정한다.
 */
export function usageAppHref(path: string): string {
  const normalized = path.startsWith("/") ? path : `/${path}`
  return normalized
}

/**
 * `/dashboard`는 앱 내 보호 페이지로 전체 네비게이션으로 이동한다.
 */
export function navigateAfterLogin(nextPath: string, router: RouterLike): void {
  if (nextPath.startsWith("/dashboard")) {
    window.location.assign(usageAppHref(nextPath))
    return
  }
  router.replace(nextPath)
}
