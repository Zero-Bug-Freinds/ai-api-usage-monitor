import path from "path"
import { fileURLToPath } from "url"
import { createRequire } from "module"

if (process.env.NEXT_PRIVATE_LOCAL_WEBPACK !== "false") {
  process.env.NEXT_PRIVATE_LOCAL_WEBPACK = "true"
}

const require = createRequire(import.meta.url)
const { NextFederationPlugin } = require("@module-federation/nextjs-mf")

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const teamRemoteOrigin = process.env.NEXT_PUBLIC_MFE_TEAM_REMOTE_URL ?? "http://localhost:3002/teams"
const usageRemoteOrigin = process.env.NEXT_PUBLIC_MFE_USAGE_REMOTE_URL ?? "http://localhost:3001/dashboard"

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  webpack(config, { isServer }) {
    if (isServer) {
      return config
    }
    config.plugins.push(
      new NextFederationPlugin({
        name: "host",
        filename: "static/chunks/remoteEntry.js",
        remotes: {
          team: `team@${teamRemoteOrigin.replace(/\/$/, "")}/_next/static/chunks/remoteEntry.js`,
          usage: `usage@${usageRemoteOrigin.replace(/\/$/, "")}/_next/static/chunks/remoteEntry.js`,
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
