"use client";

import * as React from "react";
import type { NextRouter } from "next/router";

const PagesHostRouterContext = React.createContext<NextRouter | null>(null);

/**
 * `_app`의 `router` prop을 트리 전체에 제공한다.
 * MF `loadShare` 직후 `index` 등 비동기 청크에서 `useRouter()` 컨텍스트가 비어도 동일 인스턴스를 쓴다.
 */
export function PagesHostRouterProvider({
  router,
  children,
}: {
  router: NextRouter;
  children: React.ReactNode;
}) {
  return <PagesHostRouterContext.Provider value={router}>{children}</PagesHostRouterContext.Provider>;
}

export function usePagesHostRouter(): NextRouter {
  const ctx = React.useContext(PagesHostRouterContext);
  if (!ctx) {
    throw new Error("usePagesHostRouter must be used within PagesHostRouterProvider (_app)");
  }
  return ctx;
}
