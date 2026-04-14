import type { NextConfig } from "next";
import path from "path";
import type { Configuration as WebpackConfig } from "webpack";

if (process.env.NEXT_PRIVATE_LOCAL_WEBPACK !== "false") {
  process.env.NEXT_PRIVATE_LOCAL_WEBPACK = "true";
}

// eslint-disable-next-line @typescript-eslint/no-require-imports
const { NextFederationPlugin } = require("@module-federation/nextjs-mf") as {
  NextFederationPlugin: new (options: Record<string, unknown>) => unknown;
};

/**
 * 단일 도메인 엣지(`docker/web-edge/nginx.conf` → usage-web)에서 `/dashboard/` 접두만 Usage 앱으로 보낸다.
 * Identity 앱은 루트 `/`·`/_next` 를 쓰므로 basePath 로 정적 경로를 나눈다.
 */
const basePath = "/dashboard";

const nextConfig: NextConfig = {
  basePath,
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
  },
  webpack(config: WebpackConfig) {
    config.plugins.push(
      new NextFederationPlugin({
        name: "usage",
        filename: "static/chunks/remoteEntry.js",
        exposes: {
          "./TeamUsageDashboard": "./src/components/TeamUsageDashboard.tsx",
        },
        shared: {
          react: { singleton: true, requiredVersion: false },
          "react-dom": { singleton: true, requiredVersion: false },
        },
      }),
    );
    return config;
  },
};

export default nextConfig;
