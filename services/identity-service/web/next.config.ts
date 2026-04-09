import type { NextConfig } from "next";
import path from "path";

function usageOrigin(): string {
  return (
    process.env.USAGE_WEB_INTERNAL_ORIGIN ??
    process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ??
    "http://localhost:3001"
  ).replace(/\/+$/, "");
}

const nextConfig: NextConfig = {
  /* Docker(패턴 B): 루트 컨텍스트 빌드, `packages/ui` 트랜스파일 */
  output: "standalone",
  /* 모노레포: standalone 루트에 server.js (Dockerfile CMD node server.js) */
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui"],
  async rewrites() {
    const origin = usageOrigin();
    return [
      {
        source: "/dashboard",
        destination: `${origin}/dashboard`,
      },
      {
        source: "/dashboard/:path*",
        destination: `${origin}/dashboard/:path*`,
      },
    ];
  },
};

export default nextConfig;
