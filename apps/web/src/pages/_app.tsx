import type { AppContext, AppInitialProps, AppProps } from "next/app";
import NextApp from "next/app";
import * as React from "react";
import { ConsoleShell, ConsoleShellInAppToastClient } from "@ai-usage/shell";
import { ShellRouterErrorBoundary } from "@/components/shell-router-error-boundary";
import "@/styles/globals.css";

/**
 * getInitialProps로 전역 앱을 동적 렌더링에 두어 /404·/500 사전렌더 시
 * React 컨텍스트 불일치(useContext null)를 줄인다. 페이지 getServerSideProps와 병행 가능.
 *
 * Host 전용 MF 조립 로직을 제거하고 표준 셸만 유지한다.
 * 인앱 알림 토스트 경로(`ConsoleShellInAppToastClient`)는 보존한다.
 */
export default function WebApp({ Component, pageProps }: AppProps) {
  const idOrigin = (process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "").replace(/\/$/, "");
  const logoutApiPath = idOrigin ? `${idOrigin}/api/auth/logout` : "/api/auth/logout";
  const logoutRedirectPath = idOrigin ? `${idOrigin}/login` : "/login";

  return (
    <ShellRouterErrorBoundary>
      <ConsoleShellInAppToastClient>
        <ConsoleShell
          profile="team"
          logoutApiPath={logoutApiPath}
          logoutRedirectPath={logoutRedirectPath}
        >
          <div className="host-shell min-h-screen">
            <Component {...pageProps} />
          </div>
        </ConsoleShell>
      </ConsoleShellInAppToastClient>
    </ShellRouterErrorBoundary>
  );
}

WebApp.getInitialProps = async (appContext: AppContext): Promise<AppInitialProps> => {
  return NextApp.getInitialProps(appContext);
};
