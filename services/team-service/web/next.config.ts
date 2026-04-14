import type { NextConfig } from "next"
import path from "path"
import type { Configuration as WebpackConfig } from "webpack"
type NextFederationPluginCtor = new (options: Record<string, unknown>) => unknown

if (process.env.NEXT_PRIVATE_LOCAL_WEBPACK !== "false") {
  process.env.NEXT_PRIVATE_LOCAL_WEBPACK = "true"
}

function getNextFederationPlugin(): NextFederationPluginCtor {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const mf = require("@module-federation/nextjs-mf") as { NextFederationPlugin?: NextFederationPluginCtor }
  if (!mf.NextFederationPlugin) {
    throw new Error("NextFederationPlugin export not found from @module-federation/nextjs-mf")
  }
  return mf.NextFederationPlugin
}


const nextConfig: NextConfig = {
  basePath: "/teams",
  output: "standalone",
  // 모노레포 루트 경로를 정확히 추적하도록 설정
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],

  webpack(config: WebpackConfig, options: { isServer: boolean }) {
    if (options.isServer) return config
    const NextFederationPlugin = getNextFederationPlugin()
    config.plugins = config.plugins ?? []
    config.plugins.push(
      new NextFederationPlugin({
        name: "team",
        filename: "static/chunks/remoteEntry.js",
        exposes: {
          "./TeamManagement": "./src/components/mf/team-management-entry.tsx",
        },
        shared: {
          react: { singleton: true,requiredVersion: false },
          "react-dom": { singleton: true, requiredVersion: false },
        },
      })
    )
    return config
  },
}

export default nextConfig
