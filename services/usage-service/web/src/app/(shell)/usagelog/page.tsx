import { UsageLogPanel } from "@/components/usage/usage-log-panel"

export default function UsageLogPage() {
  return (
    <div className="w-full min-h-full pb-8">
      <header className="mb-6 border-b border-border pb-6">
        <h1 className="text-2xl font-semibold tracking-tight">상세 로그</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          호출 건별 사용 내역입니다. 공급사·API Key·모델로 필터할 수 있습니다.
        </p>
      </header>
      <UsageLogPanel />
    </div>
  )
}
