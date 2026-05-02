import * as React from "react";
import { useRouter } from "next/router";

/**
 * 레거시 경로 `/teams/[id]` → 쿼리 기반 `/teams?viewTeamId=…&tab=dashboard`
 */
export default function LegacyTeamsIdIndexRedirect() {
  const router = useRouter();

  React.useEffect(() => {
    if (!router.isReady) return;
    const raw = router.query.id;
    const id = typeof raw === "string" ? raw : Array.isArray(raw) ? raw[0] : "";
    if (id) {
      void router.replace(`/teams?viewTeamId=${encodeURIComponent(id)}&tab=dashboard`);
    }
  }, [router, router.isReady, router.query.id]);

  return <p className="text-sm text-muted-foreground">팀 페이지로 이동 중…</p>;
}
