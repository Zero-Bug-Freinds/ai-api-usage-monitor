import type { AppContext, AppInitialProps, AppProps } from "next/app";
import NextApp from "next/app";
import { HostShellLayout } from "@/components/host-shell-layout";
import "@/styles/globals.css";

/**
 * getInitialProps로 전역 앱을 동적 렌더링에 두어 /404·/500 사전렌더 시
 * React 컨텍스트 불일치(useContext null)를 줄인다. 페이지 getServerSideProps와 병행 가능.
 *
 * HostShellLayout은 동기 로드하여 Pages Router Context가 하위 useRouter에 보장되도록 한다.
 * SSR 이중 React 회피: 클라이언트만 MF·react 별칭; 서버는 리모트 스텁 + MF 미적용(next.config.mjs).
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
