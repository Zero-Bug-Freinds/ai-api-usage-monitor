import type { AppContext, AppInitialProps, AppProps } from "next/app";
import NextApp from "next/app";
import * as React from "react";
import { ConsoleShellInAppToastClient } from "@ai-usage/shell";
import { HostRuntimeSafeguard } from "@/components/host-runtime-safeguard";
import { HostShellLayout } from "@/components/host-shell-layout";
import { ShellRouterErrorBoundary } from "@/components/shell-router-error-boundary";
import { PagesHostRouterProvider } from "@/context/pages-host-router-context";
import "@/styles/globals.css";

/**
 * getInitialProps로 전역 앱을 동적 렌더링에 두어 /404·/500 사전렌더 시
 * React 컨텍스트 불일치(useContext null)를 줄인다. 페이지 getServerSideProps와 병행 가능.
 *
 * HostShellLayout은 동기 import로 Router 하위에 두되, MF·라우터 경합 완화를 위해
 * `HostRuntimeSafeguard`(클라이언트 마운트 + router.isReady) 안에서만 마운트한다(Task37-3·Task37-6·Task37-7).
 * MF `loadShare` 직후 `useRouter()`가 RouterContext보다 먼저 돌 수 있어, `_app`의 `router`를 prop·`PagesHostRouterProvider`로 내린다.
 * SSR 이중 React 회피: 클라이언트만 MF·react 별칭; 서버는 리모트 스텁 + MF 미적용(next.config.mjs).
 */
export default function WebApp({ Component, pageProps, router }: AppProps) {
  return (
    <PagesHostRouterProvider router={router}>
      <HostRuntimeSafeguard router={router}>
        <ShellRouterErrorBoundary>
          <ConsoleShellInAppToastClient>
            <HostShellLayout router={router}>
              <div className="host-shell min-h-screen">
                <Component {...pageProps} />
              </div>
            </HostShellLayout>
          </ConsoleShellInAppToastClient>
        </ShellRouterErrorBoundary>
      </HostRuntimeSafeguard>
    </PagesHostRouterProvider>
  );
}

WebApp.getInitialProps = async (appContext: AppContext): Promise<AppInitialProps> => {
  return NextApp.getInitialProps(appContext);
};
