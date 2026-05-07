"use client"

type RouterLike = { replace: (href: string) => void }

/**
 * Usage 웹 앱으로 가는 링크용 href.
 * 공개 진입은 web-edge에서 `/dashboard` 경로로 분기한다.
 */
export function usageAppHref(path: string): string {
  const normalized = path.startsWith("/") ? path : `/${path}`
  return normalized
}

/**
 * `/dashboard`·`/billing` 은 다른 Next 앱이므로 전체 네비게이션으로 이동한다.
 */
export function navigateAfterLogin(nextPath: string, router: RouterLike): void {
  // Defense-in-depth: only allow same-origin absolute paths to prevent open redirect.
  if (!nextPath.startsWith("/") || nextPath.startsWith("//")) {
    router.replace("/dashboard")
    return
  }

  if (nextPath.startsWith("/dashboard") || nextPath.startsWith("/billing")) {
    window.location.assign(usageAppHref(nextPath))
    return
  }
  router.replace(nextPath)
}
