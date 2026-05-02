import type { AppContext, AppInitialProps, AppProps } from "next/app";
import NextApp from "next/app";
import { HostShellLayout } from "@/components/host-shell-layout";
import "@/styles/globals.css";

/**
 * _app에 getInitialProps를 두면 getStaticProps가 없는 페이지의 automatic static
 * optimization이 꺼지고, 빌드 시 prerender 단계에서 Router/Client 컨텍스트로
 * 터지던 useContext(null) 오류를 피할 수 있다.
 */
export default function WebApp({ Component, pageProps }: AppProps) {
  return (
    <HostShellLayout>
      <div className="host-shell min-h-screen">
        <Component {...pageProps} />
      </div>
    </HostShellLayout>
  );
}

WebApp.getInitialProps = async (appContext: AppContext): Promise<AppInitialProps> => {
  return NextApp.getInitialProps(appContext);
};
