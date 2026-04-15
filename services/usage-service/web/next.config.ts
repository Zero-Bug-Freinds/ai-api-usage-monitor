import type { NextConfig } from "next";
import path from "path";

/**
 * 단일 도메인 엣지(`docker/web-edge/nginx.conf` → usage-web)에서 `/dashboard/` 접두만 Usage 앱으로 보낸다.
 * Identity 앱은 루트 `/`·`/_next` 를 쓰므로 basePath 로 정적 경로를 나눈다.
 */
const basePath = "/dashboard";

const nextConfig: NextConfig = {
  basePath,
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  // 모노레포 내 공통 패키지 의존성 명시
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
  },
};

export default nextConfig;
