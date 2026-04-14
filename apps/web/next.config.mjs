import { createRequire } from "module";
import path from "path";
import { fileURLToPath } from "url";

const require = createRequire(import.meta.url);

if (process.env.NEXT_PRIVATE_LOCAL_WEBPACK !== "false") {
  process.env.NEXT_PRIVATE_LOCAL_WEBPACK = "true";
}

function getNextFederationPlugin() {
  const mf = require("@module-federation/nextjs-mf");
  if (!mf?.NextFederationPlugin) {
    throw new Error("NextFederationPlugin export not found from @module-federation/nextjs-mf");
  }
  return mf.NextFederationPlugin;
}

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const teamRemoteOrigin = process.env.NEXT_PUBLIC_MFE_TEAM_REMOTE_URL ?? "http://localhost:3002/teams";
const usageRemoteOrigin = process.env.NEXT_PUBLIC_MFE_USAGE_REMOTE_URL ?? "http://localhost:3001/dashboard";

/** @type {import("next").NextConfig} */
const nextConfig = {
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  webpack(config, { isServer }) {
    if (isServer) return config;
    const NextFederationPlugin = getNextFederationPlugin();
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
          react: { singleton: true, requiredVersion: false },
          "react-dom": { singleton: true, requiredVersion: false },
        },
      })
    );
    return config;
  },
};

export default nextConfig;
