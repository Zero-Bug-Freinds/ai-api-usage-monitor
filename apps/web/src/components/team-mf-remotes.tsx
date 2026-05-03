"use client";

/** Task37-8 수동 검증: DevTools 스택 최상단이 `next/router`인지 본 청크·리모트 청크인지 구분한다. */

import dynamic from "next/dynamic";
import type { ComponentType } from "react";
import * as React from "react";
import type { CachedTeamItem } from "@ai-usage/team-workspace-cache";
import { Button } from "@ai-usage/ui";
import { RemoteErrorBoundary } from "@/components/remote-error-boundary";

/** Matches `usage/TeamUsageDashboard` in services/usage-service/web-mfe (MF remote typing). */
type TeamUsageDashboardProps = {
  viewTeamIdFromQuery?: string;
  shellTeamList?: CachedTeamItem[];
};

const TeamUsageDashboard = dynamic(
  () =>
    import("usage/TeamUsageDashboard") as Promise<{
      default: ComponentType<TeamUsageDashboardProps>;
    }>,
  {
    ssr: false,
    loading: () => <p className="text-sm text-muted-foreground">Usage remote loading...</p>,
  }
);

/**
 * 리모트 dynamic이 이 청크 평가 시점에만 등록되도록 분리(Task37-8).
 * 한 틱 지연은 `apps/web/src/pages/index.tsx`의 구 예전 `MfRuntimeReadyGate`를 여기로 흡수.
 */
function MfConsumeTickGate({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = React.useState(false);

  React.useEffect(() => {
    const id = requestAnimationFrame(() => {
      queueMicrotask(() => setReady(true));
    });
    return () => cancelAnimationFrame(id);
  }, []);

  if (!ready) {
    return <p className="text-sm text-muted-foreground">콘솔을 준비하는 중…</p>;
  }

  return <>{children}</>;
}

export type TeamMfRemotesProps = {
  viewTeamId: string;
  teams: CachedTeamItem[];
  usageResetKey: string;
};

/**
 * 우측 슬롯(Task37-12): usage-web-mfe만 사용한다. 상단 탭과 무관하게 동일 `TeamUsageDashboard`(탭은 URL·UI 상태용).
 */
export function TeamMfRemotes({ viewTeamId, teams, usageResetKey }: TeamMfRemotesProps) {
  return (
    <MfConsumeTickGate>
      <RemoteErrorBoundary
        resetKey={usageResetKey}
        renderFallback={({ retry }) => (
          <div className="space-y-3 rounded-md border border-border bg-muted/30 p-4">
            <p className="text-sm text-muted-foreground">Usage remote를 불러오지 못했습니다.</p>
            <Button type="button" variant="outline" size="sm" onClick={retry}>
              다시 시도
            </Button>
          </div>
        )}
      >
        <TeamUsageDashboard viewTeamIdFromQuery={viewTeamId} shellTeamList={teams} />
      </RemoteErrorBoundary>
    </MfConsumeTickGate>
  );
}
