import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* Docker(패턴 B): 루트 컨텍스트 빌드, `packages/ui` 트랜스파일 */
  output: "standalone",
  transpilePackages: ["@ai-usage/ui"],
};

export default nextConfig;
