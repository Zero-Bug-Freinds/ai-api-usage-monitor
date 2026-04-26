import dynamic from "next/dynamic";
import { RemoteErrorBoundary } from "@/components/remote-error-boundary";

const TeamManagement = dynamic(() => import("team/TeamManagement"), {
  ssr: false,
  loading: () => <p className="text-sm text-muted-foreground">Team remote loading...</p>,
});

export default function TeamPage() {
  return (
    <RemoteErrorBoundary
      fallback={<p className="p-4 text-sm text-muted-foreground">Team remote를 불러오지 못했습니다.</p>}
    >
      <TeamManagement />
    </RemoteErrorBoundary>
  );
}
