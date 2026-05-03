"use client";

import * as React from "react";

/**
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
