"use client";

import type { ReactNode } from "react";
import * as React from "react";
import { useRouter } from "next/router";
import { ConsoleLayoutOverride, ConsoleSidebarPages } from "@ai-usage/shell";
import { useLogoutCleanup } from "@ai-usage/team-workspace-cache";
import { TeamWorkspaceProvider, useTeamWorkspace } from "@/components/team-workspace-context";

function HostShellInner({ children }: { children: ReactNode }) {
  /** 사이드바·next/link 등은 SSR 정적 생성(/404·/500)에서 컨텍스트 오류를 유발할 수 있어 클라이언트 마운트 후에만 렌더 */
  const [chromeReady, setChromeReady] = React.useState(false);
  React.useEffect(() => setChromeReady(true), []);

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

  if (!chromeReady) {
    return (
      <div className="flex min-h-screen w-full min-w-0 bg-background">
        <main className="flex min-h-screen min-w-0 flex-1 flex-col overflow-x-auto overflow-y-auto">
          <div className="mx-auto min-h-full w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:px-8">{children}</div>
        </main>
      </div>
    );
  }

  return (
    <ConsoleLayoutOverride
      primarySidebar={
        <ConsoleSidebarPages
          profile="team"
          teams={teams.map((t) => ({ id: t.id, name: t.name }))}
          buildTeamSubmenuHref={buildTeamSubmenuHref}
          teamExpandedTeamId={viewTeamId || null}
          teamSubmenuActive={teamSubmenuActive}
          logoutApiPath={logoutApiPath}
          logoutRedirectPath={logoutRedirectPath}
        />
      }
    >
      {children}
    </ConsoleLayoutOverride>
  );
}

export function HostShellLayout({ children }: { children: ReactNode }) {
  return (
    <TeamWorkspaceProvider>
      <HostShellInner>{children}</HostShellInner>
    </TeamWorkspaceProvider>
  );
}
