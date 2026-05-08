import dynamic from "next/dynamic";
const ConsoleShell = dynamic(
  () => import("@ai-usage/shell").then((m) => m.ConsoleShell),
  { ssr: false }
);
const TeamManagementEntry = dynamic(
  () => import("../components/mf/team-management-entry"),
  { ssr: false }
);
export default function TeamIndexPage() {
  return (
    <ConsoleShell profile="team">
      <TeamManagementEntry />
    </ConsoleShell>
  );
}
