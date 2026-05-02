import * as React from "react";
import { useRouter } from "next/router";

import type { TeamRouteSection } from "@/components/team-page-content";

function toTab(section: string | undefined): TeamRouteSection {
  if (section === "members" || section === "api-keys") return section;
  return "dashboard";
}

/**
 * 레거시 `/teams/[id]/[section]` → 쿼리 기반 전환.
 */
export default function LegacyTeamsSectionRedirect() {
  const router = useRouter();

  React.useEffect(() => {
    if (!router.isReady) return;
    const rawId = router.query.id;
    const id = typeof rawId === "string" ? rawId : Array.isArray(rawId) ? rawId[0] : "";
    const rawSec = router.query.section;
    const sec = typeof rawSec === "string" ? rawSec : Array.isArray(rawSec) ? rawSec[0] : "";
    if (!id) return;
    const tab = toTab(sec);
    void router.replace({
      pathname: "/",
      query: { viewTeamId: id, tab },
    });
  }, [router, router.isReady, router.query.id, router.query.section]);

  return <p className="text-sm text-muted-foreground">팀 페이지로 이동 중…</p>;
}
