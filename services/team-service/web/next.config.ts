import type { NextConfig } from "next"
import path from "path"


const nextConfig: NextConfig = {
  basePath: "/teams",
  output: "standalone",
  // 모노레포 루트 경로를 정확히 추적하도록 설정
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
}

export default nextConfig
