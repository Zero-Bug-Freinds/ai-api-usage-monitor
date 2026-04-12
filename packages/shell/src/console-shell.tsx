import type { ReactNode } from "react"

export type ConsoleShellProps = {
  sidebar: ReactNode
  children: ReactNode
}

/**
 * Google AI Studio 스타일의 좌측 내비 + 본문 뼈대.
 */
export function ConsoleShell({ sidebar, children }: ConsoleShellProps) {
  return (
    <div className="flex min-h-screen w-full bg-background">
      {sidebar}
      <main className="min-h-screen min-w-0 flex-1 overflow-x-auto">
        <div className="mx-auto min-h-full max-w-6xl px-4 py-6 sm:px-6 lg:px-8">{children}</div>
      </main>
    </div>
  )
}
