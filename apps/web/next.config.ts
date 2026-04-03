import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* Docker(패턴 B): apps/web/Dockerfile standalone 산출물 사용 */
  output: "standalone",
};

export default nextConfig;
