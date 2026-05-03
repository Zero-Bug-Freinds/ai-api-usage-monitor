"use client"

import type { ReactNode } from "react"
import Link from "next/link"
import type { ConsoleNavLinkSpec } from "./console-nav"

export type ConsoleInternalNavLinkProps = {
  spec: ConsoleNavLinkSpec
  /** 예: 팀 메뉴는 항상 풀 페이지 이동으로 고정 */
  forceAnchor?: boolean
  className: string
  children: ReactNode
}

/**
 * 교차 서비스 이동은 `<a>`, 동일 앱 내 SPA만 `next/link` — Router/MF 경계 오류를 줄인다.
 */
export function ConsoleInternalNavLink({ spec, forceAnchor, className, children }: ConsoleInternalNavLinkProps) {
  const useAnchor = Boolean(forceAnchor) || spec.kind !== "next"
  if (useAnchor) {
    return (
      <a href={spec.href} className={className}>
        {children}
      </a>
    )
  }
  return (
    <Link href={spec.href} className={className}>
      {children}
    </Link>
  )
}
