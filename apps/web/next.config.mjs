import path from "path";
import { fileURLToPath } from "url";
import NextFederationPlugin from "@module-federation/nextjs-mf";


const __dirname = path.dirname(fileURLToPath(import.meta.url));
const teamRemoteOrigin = process.env.NEXT_PUBLIC_MFE_TEAM_REMOTE_URL ?? "http://localhost:3012";
const usageRemoteOrigin = process.env.NEXT_PUBLIC_MFE_USAGE_REMOTE_URL ?? "http://localhost:3011";

/** @type {import("next").NextConfig} */
const nextConfig = {
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  webpack(config, { isServer }) {
    if (isServer) {
      if (Array.isArray(config.externals)) {
        config.externals.push("node:module");
      } else if (config.externals === undefined) {
        config.externals = ["node:module"];
      } else {
        config.externals = [config.externals, "node:module"];
      }
    }
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
          react: { singleton: true, strictVersion: true, requiredVersion: "19.2.4", eager: true },
          "react-dom": { singleton: true, strictVersion: true, requiredVersion: "19.2.4", eager: true },
        },
      })
    );
    return config;
  },
};

export default nextConfig;
