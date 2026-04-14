"use client"

export default function TeamUsageDashboard() {
  return (
    <div className="flex min-h-[16rem] w-full flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border bg-muted/30 p-8 text-center">
      <p className="text-sm font-medium text-foreground">Usage-Service의 대시보드 영역</p>
      <p className="text-xs text-muted-foreground">차트·그래프는 이후 이 컴포넌트에 구현합니다.</p>
    </div>
  )
}
