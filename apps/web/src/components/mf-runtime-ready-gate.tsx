"use client";

import * as React from "react";

/**
 * @deprecated Task37-8 — 팀 홈의 MF 틱 지연은 `team-mf-remotes`의 `MfConsumeTickGate`로 옮겼다.
 * 다른 페이지에서 동일 패턴이 필요하면 이 컴포넌트를 재사용하거나 `MfConsumeTickGate`를 공통 추출한다.
 *
 * 리모트 `dynamic(import("team/…"))` 등이 돌기 전에 MF 런타임(consumes/loadShare)이 한 틱 정리될
 * 시간을 준다. `next.config` shared strictVersion 완화와 병행한다(Task37-5).
 */
export function MfRuntimeReadyGate({ children }: { children: React.ReactNode }) {
    const [ready, setReady] = React.useState(false);

    React.useEffect(() => {
        const id = requestAnimationFrame(() => {
            queueMicrotask(() => setReady(true));
        });
        return () => cancelAnimationFrame(id);
    }, []);

    if (!ready) {
        return <p className="text-sm text-muted-foreground">콘솔을 준비하는 중…</p>;
    }

    return <>{children}</>;
}
