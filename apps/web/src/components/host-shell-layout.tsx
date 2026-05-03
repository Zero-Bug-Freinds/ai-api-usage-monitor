"use client";

import type { ReactNode } from "react";
import * as React from "react";
import dynamic from "next/dynamic";
import { useRouter } from "next/router";
import { ConsoleLayoutOverride } from "@ai-usage/shell/pages";
import { useLogoutCleanup } from "@ai-usage/team-workspace-cache";
import { TeamWorkspaceProvider, useTeamWorkspace } from "@/components/team-workspace-context";

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

function HostShellInner({ children }: { children: ReactNode }) {
  useLogoutCleanup();
  const router = useRouter();
  const { teams } = useTeamWorkspace();

  const viewTeamId =
    typeof router.query.viewTeamId === "string"
      ? router.query.viewTeamId
      : Array.isArray(router.query.viewTeamId)
        ? router.query.viewTeamId[0] ?? ""
        : "";

  const tabRaw =
    typeof router.query.tab === "string"
      ? router.query.tab
      : Array.isArray(router.query.tab)
        ? router.query.tab[0] ?? ""
        : "";
  const effectiveTab = tabRaw === "" ? "dashboard" : tabRaw;

  const buildTeamSubmenuHref = React.useCallback((teamId: string, suffix: string) => {
    return `/?viewTeamId=${encodeURIComponent(teamId)}&tab=${encodeURIComponent(suffix)}`;
  }, []);

  const teamSubmenuActive =
    viewTeamId !== "" && (effectiveTab === "dashboard" || effectiveTab === "members" || effectiveTab === "api-keys")
      ? { teamId: viewTeamId, suffix: effectiveTab }
      : null;

  const idOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const logoutApiPath = idOrigin ? `${idOrigin}/api/auth/logout` : "/api/auth/logout";
  const logoutRedirectPath = idOrigin ? `${idOrigin}/login` : "/login";

  const primarySidebar = (
    <TeamSidebarDynamic
      profile="team"
      teams={teams.map((t) => ({ id: t.id, name: t.name }))}
      buildTeamSubmenuHref={buildTeamSubmenuHref}
      teamExpandedTeamId={viewTeamId || null}
      teamSubmenuActive={teamSubmenuActive}
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

  return <ConsoleLayoutOverride primarySidebar={primarySidebar}>{mainSlot}</ConsoleLayoutOverride>;
}

export function HostShellLayout({ children }: { children: ReactNode }) {
  return (
    <TeamWorkspaceProvider>
      <HostShellInner>{children}</HostShellInner>
    </TeamWorkspaceProvider>
  );
}
