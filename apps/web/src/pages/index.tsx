import { TeamPageContent } from "@/components/team-page-content";

/**
 * 팀 콘솔 메인 (basePath `/teams` → 브라우저 경로 `/teams`).
 */
export default function TeamsConsoleHomePage() {
  return (
    <div className="host-remote-slot space-y-4 p-4">
      <TeamPageContent />
    </div>
  );
}
