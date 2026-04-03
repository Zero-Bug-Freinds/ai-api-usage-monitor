"use client"

type RouterLike = { replace: (href: string) => void }

/**
 * `/dashboard`는 Usage `web/`(별도 Next 앱)이 담당하므로 항상 전체 네비게이션으로 이동한다.
 * 로컬에서 포트가 다르면 `NEXT_PUBLIC_USAGE_WEB_ORIGIN`으로 오리진을 붙인다.
 */
export function navigateAfterLogin(nextPath: string, router: RouterLike): void {
  const usageOrigin = (process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ?? "").replace(/\/$/, "")
  if (nextPath.startsWith("/dashboard")) {
    window.location.assign(usageOrigin ? `${usageOrigin}${nextPath}` : nextPath)
    return
  }
  router.replace(nextPath)
}
