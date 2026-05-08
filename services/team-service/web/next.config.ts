import type { NextConfig } from "next";
import path from "path";
import type { Configuration as WebpackConfig } from "webpack";
import NextFederationPlugin from "@module-federation/nextjs-mf";

process.env.NEXT_PRIVATE_LOCAL_WEBPACK ??= "true";

const teamAssetPrefix = (process.env.NEXT_PUBLIC_MFE_ASSET_PREFIX ?? "/mfe/team").replace(/\/+$/, "");
const enableStandalone = process.env.NEXT_DISABLE_STANDALONE === "false";

const nextConfig: NextConfig = {
  assetPrefix: teamAssetPrefix,
  output: enableStandalone ? "standalone" : undefined,
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  webpack(config: WebpackConfig) {
    config.plugins = config.plugins ?? [];
    config.plugins.push(
      new NextFederationPlugin({
        name: "team",
        filename: "static/chunks/remoteEntry.js",
        exposes: {
          "./TeamManagement": "./src/components/mf/team-management-entry.tsx",
        },
        shared: {
          react: { singleton: true, strictVersion: true, requiredVersion: "19.2.4", eager: true },
          "react-dom": { singleton: true, strictVersion: true, requiredVersion: "19.2.4", eager: true },
        },
        extraOptions: {},
      })
    );
    return config;
  },
};

export default nextConfig;
