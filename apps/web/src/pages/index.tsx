import type { GetServerSideProps } from "next";

import { MfRuntimeReadyGate } from "@/components/mf-runtime-ready-gate";
import { TeamPageContent } from "@/components/team-page-content";

export const getServerSideProps: GetServerSideProps = async () => {
  return { props: {} };
};

/**
 * 팀 콘솔 메인 (basePath `/teams` → 브라우저 경로 `/teams`).
 * MF 리모트 청크는 MfRuntimeReadyGate 이후에만 마운트(Task37-5).
 */
export default function TeamsConsoleHomePage() {
  return (
    <div className="host-remote-slot space-y-4 p-4">
      <MfRuntimeReadyGate>
        <TeamPageContent />
      </MfRuntimeReadyGate>
    </div>
  );
}
