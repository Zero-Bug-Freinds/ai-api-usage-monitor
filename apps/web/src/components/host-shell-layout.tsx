"use client";

import type { ReactNode } from "react";
import * as React from "react";
import dynamic from "next/dynamic";
import type { NextRouter } from "next/router";
import { ConsoleLayoutOverride } from "@ai-usage/shell/pages";
import { useLogoutCleanup } from "@ai-usage/team-workspace-cache";
import { TeamWorkspaceProvider } from "@/components/team-workspace-context";

const sidebarSkeleton = (
  <aside
    className="h-full min-h-0 w-64 min-w-[240px] max-w-[280px] shrink-0 border-r border-sidebar-border bg-sidebar animate-pulse"
    aria-hidden
  />
);

const TeamSidebarDynamic = dynamic(() => import("@/components/team-sidebar-lazy"), {
  ssr: false,
  loading: () => sidebarSkeleton,
});

function HostShellInner({ children, router }: { children: ReactNode; router: NextRouter }) {
  useLogoutCleanup();

  const idOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const logoutApiPath = idOrigin ? `${idOrigin}/api/auth/logout` : "/api/auth/logout";
  const logoutRedirectPath = idOrigin ? `${idOrigin}/login` : "/login";

  const primarySidebar = (
    <TeamSidebarDynamic
      profile="team"
      pagesRouter={router}
      showTeamSidebarSection={false}
      logoutApiPath={logoutApiPath}
      logoutRedirectPath={logoutRedirectPath}
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

  return (
    <ConsoleLayoutOverride
      primarySidebar={primarySidebar}
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
