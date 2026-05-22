import dynamic from "next/dynamic";
import * as React from "react";

const ConsoleShellPages = dynamic(
  () => import("@ai-usage/shell/pages").then((m) => m.ConsoleShellPages),
  { ssr: false, loading: () => <p className="p-4 text-sm text-muted-foreground">콘솔을 불러오는 중…</p> }
);
const TeamManagementEntry = dynamic(
  () => import("../components/mf/team-management-entry"),
  { ssr: false, loading: () => <p className="p-4 text-sm text-muted-foreground">팀 화면을 불러오는 중…</p> }
);

class TeamPageErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { error: Error | null }
> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <div className="p-6 text-sm text-destructive" role="alert">
          팀 화면을 표시하지 못했습니다. 잠시 후 새로고침하거나 web-edge(배포 URL)에서 다시 열어 주세요.
        </div>
      );
    }
    return this.props.children;
  }
}

export default function TeamIndexPage() {
  return (
    <TeamPageErrorBoundary>
      <ConsoleShellPages profile="team">
        <TeamManagementEntry />
      </ConsoleShellPages>
    </TeamPageErrorBoundary>
  );
}
