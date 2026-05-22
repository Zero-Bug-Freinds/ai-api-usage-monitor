import type { NextConfig } from "next";
import path from "path";

const repoRoot = path.join(__dirname, "../../..");
const shellPackageRoot = path.join(repoRoot, "packages/shell/src");
const basePath = "/teams";

const nextConfig: NextConfig = {
  basePath,
  output: "standalone",
  outputFileTracingRoot: repoRoot,
  outputFileTracingIncludes: {
    "/*": ["../../../packages/shell/src/**/*", "../../../packages/ui/**/*"],
  },
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  env: {
    NEXT_PUBLIC_BASE_PATH: basePath,
  },
  webpack(config) {
    config.resolve = config.resolve ?? {};
    config.resolve.alias = {
      ...(config.resolve.alias as Record<string, string> | undefined),
      "@ai-usage/shell/pages": path.join(shellPackageRoot, "pages.ts"),
      "@ai-usage/shell": shellPackageRoot,
    };
    return config;
  },
};

export default nextConfig;
