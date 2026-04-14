// @ts-nocheck
import type { NextConfig } from "next"
import path from "path"

if (process.env.NEXT_PRIVATE_LOCAL_WEBPACK !== "false") {
  process.env.NEXT_PRIVATE_LOCAL_WEBPACK = "true"
}

// eslint-disable-next-line @typescript-eslint/no-require-imports
const { NextFederationPlugin } = require("@module-federation/nextjs-mf") as {
  NextFederationPlugin: new (options: Record<string, unknown>) => unknown
}

const nextConfig: NextConfig = {
  basePath: "/teams",
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui"],
  webpack(config) {
    config.plugins.push(
      new NextFederationPlugin({
        name: "team",
        filename: "static/chunks/remoteEntry.js",
        exposes: {
          "./TeamManagement": "./src/components/mf/team-management-entry.tsx",
        },
        shared: {
          react: { singleton: true, requiredVersion: false },
          "react-dom": { singleton: true, requiredVersion: false },
        },
      }),
    )
    return config
  },
}

export default nextConfig
