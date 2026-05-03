"use client";

import type { ReactNode } from "react";
import * as React from "react";
import type { NextRouter } from "next/router";

type HostRuntimeSafeguardProps = {
  children: ReactNode;
  /** `_app`의 `router` prop — MF 비동기 청크에서 `useRouter` 컨텍스트가 비어 있을 때 동일 인스턴스를 쓴다. */
  router: NextRouter;
};

/**
 * Task37-7: 클라이언트 마운트 이후에만 셸을 마운트하고, `router.isReady`와 결합해
 * MF loadShare·Pages Router 활성화 순서 경합을 줄인다.
 * `useRouter()` 대신 prop을 쓰면 `loadShare` 직후 스케줄된 렌더에서도 NextRouter 미마운트를 피한다.
 */
export function HostRuntimeSafeguard({ children, router }: HostRuntimeSafeguardProps) {
  const [clientMounted, setClientMounted] = React.useState(false);

  React.useEffect(() => {
    setClientMounted(true);
  }, []);

  const runtimeReady = clientMounted && router.isReady;

  if (!runtimeReady) {
    return (
      <div className="host-shell flex min-h-screen items-center justify-center bg-background px-4">
        <p className="text-sm text-muted-foreground" aria-live="polite">
          셸을 준비하는 중…
        </p>
      </div>
    );
  }

  return <>{children}</>;
}
