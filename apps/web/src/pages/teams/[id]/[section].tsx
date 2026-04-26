import { useRouter } from "next/router";
import { TeamPageContent, type TeamRouteSection } from "@/components/team-page-content";

const TEAM_ROUTE_SECTIONS: TeamRouteSection[] = ["dashboard", "members", "api-keys"];

function resolveSection(value: string | string[] | undefined): TeamRouteSection {
  const raw = Array.isArray(value) ? value[0] : value;
  if (raw && TEAM_ROUTE_SECTIONS.includes(raw as TeamRouteSection)) {
    return raw as TeamRouteSection;
  }
  return "dashboard";
}

export default function TeamSectionPage() {
  const router = useRouter();
  const section = resolveSection(router.query.section);
  return <TeamPageContent section={section} />;
}
