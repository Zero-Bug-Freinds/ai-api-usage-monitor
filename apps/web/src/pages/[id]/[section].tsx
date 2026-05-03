import type { GetServerSideProps } from "next";

import type { TeamRouteSection } from "@/components/team-route-types";

function queryParamFromDynamic(
  value: string | string[] | undefined,
): string {
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return value[0] ?? "";
  return "";
}

function toTab(section: string | undefined): TeamRouteSection {
  if (section === "members" || section === "api-keys" || section === "memberDetail") {
    return "memberDetail";
  }
  return "dashboard";
}

export const getServerSideProps: GetServerSideProps = async (context) => {
  const id = queryParamFromDynamic(context.params?.id);
  const sectionRaw = queryParamFromDynamic(context.params?.section);
  if (!id) {
    return {
      redirect: { destination: "/", permanent: false },
    };
  }
  const tab = toTab(sectionRaw || undefined);
  const q = new URLSearchParams({
    viewTeamId: id,
    tab,
  });
  return {
    redirect: {
      destination: `/?${q.toString()}`,
      permanent: false,
    },
  };
};

/**
 * 레거시 `/teams/[id]/[section]`는 서버 리다이렉트로만 처리한다.
 */
export default function LegacyTeamsSectionRedirect() {
  return <p className="text-sm text-muted-foreground">팀 페이지로 이동 중…</p>;
}
