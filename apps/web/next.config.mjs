import path from "path";
import { fileURLToPath } from "url";
import NextFederationPlugin from "@module-federation/nextjs-mf";


const __dirname = path.dirname(fileURLToPath(import.meta.url));
const teamRemoteOrigin = process.env.NEXT_PUBLIC_MFE_TEAM_REMOTE_URL ?? "http://localhost:8888/teams";
const usageRemoteOrigin = process.env.NEXT_PUBLIC_MFE_USAGE_REMOTE_URL ?? "http://localhost:8888/dashboard";

/**
 * Module Federation — 호스트가 리모트를 불러오는 URL 템플릿:
 *   `{origin}/_next/static/chunks/remoteEntry.js`
 * 배포 시 반드시 리모트 앱의 공개 origin과 일치해야 합니다.
 * 우측 대시보드가 비면 브라우저에서 위 경로로 remoteEntry.js 요청이 200인지 확인하세요.
 *
 * - NEXT_PUBLIC_MFE_TEAM_REMOTE_URL  → team 리모트 (기본 …/teams)
 * - NEXT_PUBLIC_MFE_USAGE_REMOTE_URL → usage 리모트 (기본 …/dashboard)
 */

/** @type {import("next").NextConfig} */
const nextConfig = {
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell", "@ai-usage/team-workspace-cache"],
  // Avoid bundling MF (and its `node:` imports) on the server; pairs with webpack externals below.
  serverExternalPackages: ["@module-federation/nextjs-mf"],
  webpack(config, { isServer: _isServer }) {
    // `node:` is not a webpack-supported URI scheme. @module-federation/nextjs-mf pulls
    // `import "node:module"` into the graph; if externals only run for `isServer`, the client
    // build still tries to load that URI and fails with UnhandledSchemeError.
    const prevExternals = config.externals;
    config.externals = [
      ...(Array.isArray(prevExternals) ? prevExternals : [prevExternals]),
      ({ request }, callback) => {
        if (request && /^node:/.test(request)) {
          return callback(null, `commonjs ${request}`);
        }
        callback();
      },
    ].filter(Boolean);
    config.plugins = config.plugins ?? [];
    config.plugins.push(
      new NextFederationPlugin({
        name: "host",
        remotes: {
          team: `team@${teamRemoteOrigin.replace(/\/$/, "")}/_next/static/chunks/remoteEntry.js`,
          usage: `usage@${usageRemoteOrigin.replace(/\/$/, "")}/_next/static/chunks/remoteEntry.js`,
        },
        filename: "static/chunks/remoteEntry.js",
        shared: {
          react: { singleton: true, strictVersion: true, requiredVersion: "19.2.4" },
          "react-dom": { singleton: true, strictVersion: true, requiredVersion: "19.2.4" },
        },
      })
    );
    return config;
  },
};

export default nextConfig;
