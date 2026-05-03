import type { GetServerSideProps } from "next";

function queryParamFromDynamic(
  value: string | string[] | undefined,
): string {
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return value[0] ?? "";
  return "";
}

export const getServerSideProps: GetServerSideProps = async (context) => {
  const id = queryParamFromDynamic(context.params?.id);
  if (!id) {
    return {
      redirect: { destination: "/", permanent: false },
    };
  }
  const q = new URLSearchParams({
    viewTeamId: id,
    tab: "dashboard",
  });
  return {
    redirect: {
      destination: `/?${q.toString()}`,
      permanent: false,
    },
  };
};

/**
 * 레거시 `/teams/[id]`는 서버 리다이렉트로만 처리한다 (basePath `/teams`는 Next가 destination에 반영).
 */
export default function LegacyTeamsIdIndexRedirect() {
  return <p className="text-sm text-muted-foreground">팀 페이지로 이동 중…</p>;
}
