"use client";

import dynamic from "next/dynamic";
import { RemoteErrorBoundary } from "@/components/remote-error-boundary";

export type TeamRouteSection = "dashboard" | "members" | "api-keys";

const TeamManagement = dynamic(() => import("team/TeamManagement"), {
  ssr: false,
  loading: () => <p className="text-sm text-muted-foreground">Team remote loading...</p>,
});

type TeamPageContentProps = {
  section?: TeamRouteSection;
};

export function TeamPageContent({ section = "dashboard" }: TeamPageContentProps) {
  if (section === "dashboard") {
    return (
      <RemoteErrorBoundary
        fallback={<p className="p-4 text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
      >
        <TeamManagement />
      </RemoteErrorBoundary>
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
