export default function HomePage() {
  return (
    <div className="space-y-4 p-4">
      <h1 className="text-2xl font-semibold tracking-tight">Main Shell (Host)</h1>
      <p className="text-sm text-muted-foreground">
        Module Federation 기반으로 team-service와 usage-service UI를 조합합니다.
      </p>
      {/* eslint-disable-next-line @next/next/no-html-link-for-pages */}
      <a href="/team" className="inline-flex rounded-md bg-primary px-4 py-2 text-primary-foreground">
        /team 이동
      </a>
    </div>
  );
}
