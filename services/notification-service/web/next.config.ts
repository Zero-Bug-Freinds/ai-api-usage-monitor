import type { NextConfig } from "next"
import path from "path"

/**
 * 단일 도메인 엣지(`docker/web-edge/nginx.conf` → notification-web)에서 `/notifications/` 접두만 이 앱으로 보낸다.
 */
const basePath = "/notifications"

const nextConfig: NextConfig = {
  basePath,
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
  },
}

export default nextConfig

