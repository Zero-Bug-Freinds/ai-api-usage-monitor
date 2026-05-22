"use client"

import { useRouter } from "next/router"

import { buildTeamSubmenuPublicHref } from "./console-nav"
import { ConsoleSidebarInner, type ConsoleSidebarProps } from "./console-sidebar"

/** Pages Router(web-host·team-web) 전용 사이드바. */
export function ConsoleSidebarPages(props: ConsoleSidebarProps) {
  const router = useRouter()
  const pathname = router.asPath.split("?")[0] ?? ""
  const { profile, buildTeamSubmenuHref, ...rest } = props
  return (
    <ConsoleSidebarInner
      pathname={pathname}
      profile={profile}
      navigationReady={router.isReady}
      buildTeamSubmenuHref={
        buildTeamSubmenuHref ?? (profile === "team" ? buildTeamSubmenuPublicHref : undefined)
      }
      {...rest}
    />
  )
}
