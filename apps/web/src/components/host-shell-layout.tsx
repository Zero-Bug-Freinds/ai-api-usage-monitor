"use client";

import type { ReactNode } from "react";
import * as React from "react";
import { useRouter } from "next/router";
import { ConsoleLayoutOverride, ConsoleSidebar } from "@ai-usage/shell";
import { useLogoutCleanup } from "@ai-usage/team-workspace-cache";
import { TeamWorkspaceProvider, useTeamWorkspace } from "@/components/team-workspace-context";

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
    return `/teams?viewTeamId=${encodeURIComponent(teamId)}&tab=${encodeURIComponent(suffix)}`;
  }, []);

  const teamSubmenuActive =
    viewTeamId !== "" && (effectiveTab === "dashboard" || effectiveTab === "members" || effectiveTab === "api-keys")
      ? { teamId: viewTeamId, suffix: effectiveTab }
      : null;

  const idOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const logoutApiPath = idOrigin ? `${idOrigin}/api/auth/logout` : "/api/auth/logout";
  const logoutRedirectPath = idOrigin ? `${idOrigin}/login` : "/login";

  return (
    <ConsoleLayoutOverride
      primarySidebar={
        <ConsoleSidebar
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
