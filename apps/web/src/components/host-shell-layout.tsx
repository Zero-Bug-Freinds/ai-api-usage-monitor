"use client";

import type { ReactNode } from "react";
import * as React from "react";
import dynamic from "next/dynamic";
import type { NextRouter } from "next/router";
import { ConsoleLayoutOverride } from "@ai-usage/shell/pages";
import { useLogoutCleanup } from "@ai-usage/team-workspace-cache";
import { TeamWorkspaceProvider } from "@/components/team-workspace-context";
import { RemoteErrorBoundary } from "@/components/remote-error-boundary";

const sidebarSkeleton = (
  <aside
    className="h-full min-h-0 w-64 min-w-[240px] max-w-[280px] shrink-0 border-r border-sidebar-border bg-sidebar animate-pulse"
    aria-hidden
  />
);

const TeamSidebarDynamic = dynamic(
  () => import("@ai-usage/shell/pages").then((mod) => mod.ConsoleSidebarPages),
  {
    ssr: false,
    loading: () => sidebarSkeleton,
  }
);

const TeamManagementLazy = dynamic(() => import("team/TeamManagement"), {
  ssr: false,
  loading: () => <p className="p-3 text-sm text-muted-foreground">Team remote loading…</p>,
});

function HostShellInner({ children, router }: { children: ReactNode; router: NextRouter }) {
  useLogoutCleanup();
  const [teamChunkAllowed, setTeamChunkAllowed] = React.useState(false);

  React.useEffect(() => {
    setTeamChunkAllowed(true);
  }, []);

  const primarySidebar = (
    <TeamSidebarDynamic
      profile="team"
      pagesRouter={router}
      showTeamSidebarSection={false}
    />
  );

  const mainSlot =
    router.isReady ? (
      children
    ) : (
      <p className="text-sm text-muted-foreground" aria-live="polite">
        페이지 정보를 불러오는 중…
      </p>
    );

  const secondarySidebar = (
    <aside className="team-team-mf-slot sticky top-0 ml-2 flex h-screen min-h-0 w-80 min-w-[320px] max-w-[420px] shrink-0 flex-col overflow-x-hidden rounded-l-lg border border-sidebar-border bg-sidebar text-sidebar-foreground">
      {teamChunkAllowed ? (
        <RemoteErrorBoundary
          resetKey={`${router.asPath}`}
          fallback={<p className="p-3 text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
        >
          <TeamManagementLazy />
        </RemoteErrorBoundary>
      ) : (
        <p className="p-3 text-sm text-muted-foreground">원격 모듈을 준비하는 중…</p>
      )}
    </aside>
  );

  return (
    <ConsoleLayoutOverride
      primarySidebar={primarySidebar}
      secondarySidebar={secondarySidebar}
      contentClassName="mx-auto min-h-full w-full max-w-none flex-1 px-3 py-4 sm:px-5 lg:px-6"
    >
      {mainSlot}
    </ConsoleLayoutOverride>
  );
}

export function HostShellLayout({ children, router }: { children: ReactNode; router: NextRouter }) {
  return (
    <TeamWorkspaceProvider router={router}>
      <HostShellInner router={router}>{children}</HostShellInner>
    </TeamWorkspaceProvider>
  );
}
