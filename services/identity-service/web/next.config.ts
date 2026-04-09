import type { NextConfig } from "next";
import path from "path";

function usageOrigin(): string {
  return (
    process.env.USAGE_WEB_INTERNAL_ORIGIN ??
    process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ??
    "http://host.docker.internal:3001"
  ).replace(/\/+$/, "");
}

function teamOrigin(): string {
  return (
    process.env.TEAM_WEB_INTERNAL_ORIGIN ??
    process.env.NEXT_PUBLIC_TEAM_WEB_ORIGIN ??
    "http://host.docker.internal:3002"
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
    const team = teamOrigin();
    return [
      {
        source: "/dashboard",
        destination: `${origin}/dashboard`,
      },
      {
        source: "/dashboard/:path*",
        destination: `${origin}/dashboard/:path*`,
      },
      {
        source: "/api/team/v1/:path*",
        destination: `${team}/teams/api/team/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
