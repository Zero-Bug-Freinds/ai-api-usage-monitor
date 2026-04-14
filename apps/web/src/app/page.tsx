import Link from "next/link"

export default function HomePage() {
  return (
    <div className="space-y-4 p-4">
      <h1 className="text-2xl font-semibold tracking-tight">MFE Host</h1>
      <p className="text-sm text-muted-foreground">
        팀 관리·사용량 대시보드는 Module Federation Remotes를 통해 로드됩니다. 팀 메뉴 또는 아래 링크로 이동하세요.
      </p>
      <Link
        href="/team"
        className="inline-flex h-10 items-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground"
      >
        /team 열기
      </Link>
    </div>
  )
}
