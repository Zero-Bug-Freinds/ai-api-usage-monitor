"use client";

import dynamic from "next/dynamic";
import { useRouter } from "next/router";
import { RemoteErrorBoundary } from "@/components/remote-error-boundary";

export type TeamRouteSection = "dashboard" | "members" | "api-keys";

const TeamManagement = dynamic(() => import("team/TeamManagement"), {
  ssr: false,
  loading: () => <p className="text-sm text-muted-foreground">Team remote loading...</p>,
});
const TeamUsageDashboard = dynamic(() => import("usage/TeamUsageDashboard"), {
  ssr: false,
  loading: () => <p className="text-sm text-muted-foreground">Usage remote loading...</p>,
});

function TeamUsageRouteHost() {
  const router = useRouter();
  const raw = router.query.id;
  const teamId = typeof raw === "string" ? raw : Array.isArray(raw) ? raw[0] : "";
  return <TeamUsageDashboard teamId={teamId} />;
}

type TeamPageContentProps = {
  section?: TeamRouteSection;
};

export function TeamPageContent({ section = "dashboard" }: TeamPageContentProps) {
  if (section === "dashboard") {
    return (
      <div className="space-y-4">
        <RemoteErrorBoundary
          fallback={<p className="p-4 text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
        >
          <TeamManagement />
        </RemoteErrorBoundary>
        <RemoteErrorBoundary
          fallback={<p className="p-4 text-sm text-muted-foreground">Usage remote를 불러오지 못했습니다.</p>}
        >
          <TeamUsageRouteHost />
        </RemoteErrorBoundary>
      </div>
    );
  }

  if (section === "members") {
    return (
      <RemoteErrorBoundary
        fallback={<p className="p-4 text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
      >
        <TeamManagement />
      </RemoteErrorBoundary>
    );
  }

  if (section === "api-keys") {
    return (
      <RemoteErrorBoundary
        fallback={<p className="p-4 text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
      >
        <TeamManagement />
      </RemoteErrorBoundary>
    );
  }

  return (
    <RemoteErrorBoundary
      fallback={<p className="p-4 text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
    >
      <TeamManagement />
    </RemoteErrorBoundary>
  );
}
