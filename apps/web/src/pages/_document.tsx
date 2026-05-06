import { Head, Html, Main, NextScript } from "next/document";

import { hostGeistMono, hostGeistSans } from "@/lib/host-document-fonts";

/**
 * App Router 서비스(usage 등)의 Root layout과 동일하게 Geist 변수·antialiased를
 * 초기 HTML(<Html>)에 붙여 공통 사이드바(ConsoleSidebar) 타이포가 첫 페인트부터 일치한다.
 * Pages Router는 `_app`의 useEffect보다 `_document`가 적합하다.
 */
export default function HostDocument() {
  return (
    <Html
      lang="ko"
      className={`${hostGeistSans.variable} ${hostGeistMono.variable} h-full antialiased`}
    >
      <Head />
      <body className="min-h-full flex flex-col">
        <Main />
        <NextScript />
      </body>
    </Html>
  );
}
