import type { GetServerSideProps } from "next";

import { TeamPageContent } from "@/components/team-page-content";

export const getServerSideProps: GetServerSideProps = async () => {
  return { props: {} };
};

/**
 * 팀 콘솔 메인 (basePath `/teams` → 브라우저 경로 `/teams`).
 * MF 진입 지연·rAF 틱은 `team-page-content` 및 `team-mf-remotes`로 일원화(Task37-8).
 */
export default function TeamsConsoleHomePage() {
  return (
    <div className="host-remote-slot flex min-h-0 min-w-0 w-full flex-1 flex-col">
      <TeamPageContent />
    </div>
  );
}
