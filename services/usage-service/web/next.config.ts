import type { NextConfig } from "next";

/** 단일 도메인 엣지에서 `/dashboard`로 프록시될 때 정적 자원이 `/_next` 충돌 없이 분리되도록 한다. */
const basePath = "/dashboard";

const nextConfig: NextConfig = {
  basePath,
  output: "standalone",
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
  },
};

export default nextConfig;
