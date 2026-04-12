import { Suspense } from "react"

import { ResetPasswordForm } from "@/components/reset-password/reset-password-form"

export default function ResetPasswordPage() {
  return (
    <div className="flex flex-1 items-center justify-center bg-background px-4 py-16">
      <Suspense fallback={<div className="text-sm text-muted-foreground">불러오는 중…</div>}>
        <ResetPasswordForm />
      </Suspense>
    </div>
  )
}
