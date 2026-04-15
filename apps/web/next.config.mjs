import path from "path";
import { fileURLToPath } from "url";
import NextFederationPlugin from "@module-federation/nextjs-mf";


const __dirname = path.dirname(fileURLToPath(import.meta.url));
const teamRemoteOrigin = process.env.NEXT_PUBLIC_MFE_TEAM_REMOTE_URL ?? "http://localhost:3012";
const usageRemoteOrigin = process.env.NEXT_PUBLIC_MFE_USAGE_REMOTE_URL ?? "http://localhost:3011";

/** @type {import("next").NextConfig} */
const nextConfig = {
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell"],
  // Avoid bundling MF (and its `node:` imports) on the server; pairs with webpack externals below.
  serverExternalPackages: ["@module-federation/nextjs-mf"],
  webpack(config) {
    // `node:` is not a webpack-supported URI scheme. @module-federation/nextjs-mf pulls
    // `import "node:module"` into the graph; if externals only run for `isServer`, the client
    // build still tries to load that URI and fails with UnhandledSchemeError.
    const prevExternals = config.externals;
    config.externals = [
      ...(Array.isArray(prevExternals) ? prevExternals : [prevExternals]),
      ({ request }, callback) => {
        if (request && /^node:/.test(request)) {
          return callback(null, `commonjs ${request}`);
        }
        callback();
      },
    ].filter(Boolean);
    config.plugins = config.plugins ?? [];
    config.plugins.push(
      new NextFederationPlugin({
        name: "host",
        remotes: {
          team: `team@${teamRemoteOrigin.replace(/\/$/, "")}/_next/static/chunks/remoteEntry.js`,
          usage: `usage@${usageRemoteOrigin.replace(/\/$/, "")}/_next/static/chunks/remoteEntry.js`,
        },
        filename: "static/chunks/remoteEntry.js",
        shared: {
          react: { singleton: true, strictVersion: true, requiredVersion: "19.2.4", eager: true },
          "react-dom": { singleton: true, strictVersion: true, requiredVersion: "19.2.4", eager: true },
        },
      })
    );
    return config;
  },
};

export default nextConfig;
