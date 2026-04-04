import { Suspense } from "react"

import { LoginForm } from "@/components/login/login-form"

export default function LoginPage() {
  return (
    <div className="flex flex-1 items-center justify-center bg-background px-4 py-16">
      <Suspense fallback={<div className="text-sm text-muted-foreground">불러오는 중…</div>}>
        <LoginForm />
      </Suspense>
    </div>
  )
}
