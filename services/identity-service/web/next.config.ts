import type { NextConfig } from "next";
import path from "path";
<<<<<<< HEAD

function usageOrigin(): string {
  return (
    process.env.USAGE_WEB_INTERNAL_ORIGIN ??
    process.env.NEXT_PUBLIC_USAGE_WEB_ORIGIN ??
    "http://usage-web:3000"
  ).replace(/\/+$/, "");
}

function teamOrigin(): string {
  return (
    process.env.TEAM_WEB_INTERNAL_ORIGIN ??
    "http://team-web:3000"
  ).replace(/\/+$/, "");
}

function billingOrigin(): string {
  return (
    process.env.BILLING_WEB_INTERNAL_ORIGIN ??
    process.env.NEXT_PUBLIC_BILLING_WEB_ORIGIN ??
    "http://billing-web:3000"
  ).replace(/\/+$/, "");
}

function notificationOrigin(): string {
  return (
    process.env.NOTIFICATION_WEB_INTERNAL_ORIGIN ??
    process.env.NEXT_PUBLIC_NOTIFICATION_WEB_ORIGIN ??
    "http://notification-web:3000"
  ).replace(/\/+$/, "");
}

function agentOrigin(): string {
  return (
    process.env.AGENT_WEB_INTERNAL_ORIGIN ??
    process.env.NEXT_PUBLIC_AGENT_WEB_ORIGIN ??
    process.env.AI_AGENT_WEB_INTERNAL_ORIGIN ??
    process.env.NEXT_PUBLIC_AI_AGENT_WEB_ORIGIN ??
    "http://host.docker.internal:3005"
  ).replace(/\/+$/, "");
}

=======
>>>>>>> origin/develop
const nextConfig: NextConfig = {
  /* Docker(패턴 B): 루트 컨텍스트 빌드, `packages/ui` 트랜스파일 */
  output: "standalone",
  /* 모노레포: standalone 루트에 server.js (Dockerfile CMD node server.js) */
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
};

export default nextConfig;
