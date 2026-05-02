"use client"

import { usePathname } from "next/navigation"

import { ConsoleSidebarInner, type ConsoleSidebarProps } from "./console-sidebar"

/**
 * App Router 앱(usage·billing·identity 등) 전용.
 * `next/navigation`은 Pages Router(web-host) 빌드 번들에서 분리한다(SSG 시 컨텍스트 불일치 방지).
 */
export function ConsoleSidebar(props: ConsoleSidebarProps) {
  const pathname = usePathname() ?? ""
  return <ConsoleSidebarInner pathname={pathname} {...props} />
}
