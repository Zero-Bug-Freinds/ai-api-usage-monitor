import { redirect } from "next/navigation"

/**
 * team-web(Next BFF) 루트는 UI를 두지 않는다. 팀 콘솔 UI는 web-edge 경유 Main Shell(`…/teams`)에서만 제공한다(Task37-13).
 */
export default function TeamWebRootRedirectPage() {
  const raw = process.env.NEXT_PUBLIC_TEAM_CONSOLE_URL?.trim()
  const target =
    raw && raw.length > 0 ? raw.replace(/\/+$/, "") : "http://localhost:8888/teams"
  redirect(target)
}
