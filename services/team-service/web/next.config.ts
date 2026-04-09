import type { NextConfig } from "next"
import path from "path"

const nextConfig: NextConfig = {
  basePath: "/teams",
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui"],
}

export default nextConfig
