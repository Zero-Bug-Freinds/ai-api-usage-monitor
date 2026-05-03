"use client";

import type { ReactNode } from "react";
import * as React from "react";
import { useRouter } from "next/router";

type HostRuntimeSafeguardProps = {
  children: ReactNode;
};

/**
 * Task37-7: 클라이언트 마운트 이후에만 셸을 마운트하고, `router.isReady`와 결합해
 * MF loadShare·Pages Router 활성화 순서 경합을 줄인다. `_app`는 동기로 Router 하위에 두고,
 * `useRouter`를 쓰는 `HostShellLayout`만 이 가드 안으로 격리한다.
 */
export function HostRuntimeSafeguard({ children }: HostRuntimeSafeguardProps) {
  const router = useRouter();
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
