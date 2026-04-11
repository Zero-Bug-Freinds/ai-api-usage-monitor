import type { NextConfig } from "next";
import path from "path";

/**
 * 단일 오리진(identity :3000 → rewrite)에서 `/billing/` 접두만 billing-web 으로 보낸다.
 * Identity `next.config` 의 `/billing` rewrite 와 경로를 맞춘다.
 */
const basePath = "/billing";

const nextConfig: NextConfig = {
  basePath,
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui"],
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
  },
};

export default nextConfig;
