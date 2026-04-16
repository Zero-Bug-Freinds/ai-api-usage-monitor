import type { ReactNode } from "react"

type ConsoleLayoutOverrideProps = {
  primarySidebar: ReactNode
  secondarySidebar?: ReactNode
  children: ReactNode
  rootClassName?: string
  mainClassName?: string
  contentClassName?: string
}

/**
 * MFE 페이지별 레이아웃을 독립적으로 구성하기 위한 공용 뼈대.
 * - primarySidebar: 글로벌(최좌측) 내비 영역
 * - secondarySidebar: 페이지 전용 보조 사이드바(선택)
 * - children: 메인 콘텐츠
 */
export function ConsoleLayoutOverride({
  primarySidebar,
  secondarySidebar,
  children,
  rootClassName = "flex min-h-screen w-full min-w-0 bg-background",
  mainClassName = "flex min-h-screen min-w-0 flex-1 flex-col overflow-x-auto overflow-y-auto",
  contentClassName = "mx-auto min-h-full w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:px-8",
}: ConsoleLayoutOverrideProps) {
  return (
    <div className={rootClassName}>
      {primarySidebar}
      {secondarySidebar}
      <main className={mainClassName}>
        <div className={contentClassName}>{children}</div>
      </main>
    </div>
  )
}
