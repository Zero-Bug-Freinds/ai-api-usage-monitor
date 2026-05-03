import type { NextConfig } from "next"
import path from "path"

const basePath = "/agent"

/** `standalone` + monorepo tracing are for `next build` / Docker only — they can break `next dev` routing. */
const isNextBuild = process.argv.includes("build")

const nextConfig: NextConfig = {
  basePath,
  ...(isNextBuild
    ? {
        output: "standalone" as const,
        outputFileTracingRoot: path.join(__dirname, "..", "..", ".."),
      }
    : {}),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
  },
}

export default nextConfig
