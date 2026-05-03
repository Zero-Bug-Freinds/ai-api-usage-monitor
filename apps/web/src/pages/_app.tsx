import type { AppContext, AppInitialProps, AppProps } from "next/app";
import NextApp from "next/app";
import { HostShellLayout } from "@/components/host-shell-layout";
import "@/styles/globals.css";

/**
 * getInitialProps로 전역 앱을 동적 렌더링에 두어 /404·/500 사전렌더 시
 * React 컨텍스트 불일치(useContext null)를 줄인다. 페이지 getServerSideProps와 병행 가능.
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
