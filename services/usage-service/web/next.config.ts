import type { NextConfig } from "next";

/**
 * 단일 도메인 엣지(`docker/web-edge/nginx.conf` → usage-web)에서 `/dashboard/` 접두만 Usage 앱으로 보낸다.
 * Identity 앱은 루트 `/`·`/_next` 를 쓰므로 basePath 로 정적 경로를 나눈다.
 */
const basePath = "/dashboard";

const nextConfig: NextConfig = {
  basePath,
  output: "standalone",
  transpilePackages: ["@ai-usage/ui"],
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
  },
};

export default nextConfig;
