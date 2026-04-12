"use client"

import * as React from "react"
import { useRouter } from "next/navigation"

import { Button } from "@ai-usage/ui"
import { apiFetch } from "@/lib/api/client-fetch"

type LogoutButtonProps = {
  className?: string
  variant?: React.ComponentProps<typeof Button>["variant"]
}

export function LogoutButton({ className, variant = "outline" }: LogoutButtonProps) {
  const router = useRouter()
  const [pending, setPending] = React.useState(false)

  async function handleLogout() {
    setPending(true)
    const idOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "")
    try {
      await apiFetch<null>(
        `${idOrigin}/api/auth/logout`,
        { method: "POST", credentials: "include" },
        { authRequired: false }
      )
    } catch {
      // 네트워크 오류여도 로그인 화면으로 보내 재시도 유도
    } finally {
      setPending(false)
    }
    router.replace(`${idOrigin}/login`)
  }

  return (
    <Button type="button" variant={variant} className={className} disabled={pending} onClick={handleLogout}>
      {pending ? "로그아웃 중..." : "로그아웃"}
    </Button>
  )
}
