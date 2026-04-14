import type { ReactNode } from "react"

export type ConsoleShellProps = {
  sidebar: ReactNode
  children: ReactNode
  /**
   * Inner wrapper around `children` (default: centered `max-w-6xl`).
   * Host apps that need full-width dashboards can pass e.g. `max-w-none w-full px-4 py-6`.
   */
  contentClassName?: string
}

const defaultContentClassName =
  "mx-auto min-h-full w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:px-8"

/**
 * Google AI Studio 스타일의 좌측 내비 + 본문 뼈대.
 */
export function ConsoleShell({ sidebar, children, contentClassName }: ConsoleShellProps) {
  return (
    <div className="flex min-h-screen w-full min-w-0 bg-background">
      {sidebar}
      <main className="flex min-h-screen min-w-0 flex-1 flex-col overflow-x-auto overflow-y-auto">
        <div className={contentClassName ?? defaultContentClassName}>{children}</div>
      </main>
    </div>
  )
}
