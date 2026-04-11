import { UsageLogPanel } from "@/components/usage/usage-log-panel"

export default function UsageLogPage() {
  return (
    <div className="w-full min-h-full pb-8">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">상세 로그</h1>
      </header>
      <UsageLogPanel />
    </div>
  )
}
