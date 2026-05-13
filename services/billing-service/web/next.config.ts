import type { NextConfig } from "next";
import path from "path";

/**
 * 단일 오리진(web-edge)에서 `/billing/` 접두만 billing-web 으로 보낸다.
 * `docker/web-edge/nginx.conf.template` 의 `/billing` 라우팅과 경로를 맞춘다.
 */
const basePath = "/billing";

const isWin32 = process.platform === "win32";

const nextConfig: NextConfig = {
  basePath,
  // On Windows without Developer Mode/admin, creating symlinks for standalone output
  // may fail with EPERM. Keep standalone for Linux/containers, but allow local Windows builds.
  ...(isWin32 ? {} : { output: "standalone" as const }),
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
    // Dev: cross-app links go through web-edge; absolute href avoids `/billing` + `/dashboard` confusion.
    NEXT_PUBLIC_IDENTITY_WEB_ORIGIN:
      process.env.NEXT_PUBLIC_IDENTITY_WEB_ORIGIN ?? "http://localhost:8888",
  },
};

export default nextConfig;
