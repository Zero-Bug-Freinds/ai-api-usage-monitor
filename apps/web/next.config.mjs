import { createRequire } from "module";
import path from "path";
import { fileURLToPath } from "url";
import NextFederationPlugin from "@module-federation/nextjs-mf";

const require = createRequire(import.meta.url);

/** pnpm·Docker에서도 동작하도록 하드코딩 ../../node_modules 대신 실제 resolve 경로 사용 */
function resolvePackageRoot(dependencyName) {
  try {
    return path.dirname(require.resolve(path.join(dependencyName, "package.json")));
  } catch {
    return null;
  }
}

/** 클라이언트 빌드: Task37-2 `require.resolve` 별칭(react·react-dom·jsx-runtime)으로 MF·청크 간 단일 React. 서버는 MF 미적용·리모트 스텁으로 동일 목표. */
function safeResolve(specifier) {
  try {
    return require.resolve(specifier);
  } catch {
    return null;
  }
}


const __dirname = path.dirname(fileURLToPath(import.meta.url));
const teamRemoteOrigin =
  process.env.NEXT_PUBLIC_MFE_TEAM_REMOTE_URL ?? "http://localhost:8888/mfe/team";
const usageRemoteOrigin =
  process.env.NEXT_PUBLIC_MFE_USAGE_REMOTE_URL ?? "http://localhost:8888/mfe/usage";

/**
 * Module Federation — 호스트가 리모트를 불러오는 URL 템플릿:
 *   `{origin}/_next/static/chunks/remoteEntry.js`
 * 배포 시 반드시 리모트 앱의 공개 origin과 일치해야 합니다.
 * 우측 대시보드가 비면 브라우저에서 위 경로로 remoteEntry.js 요청이 200인지 확인하세요.
 *
 * - NEXT_PUBLIC_MFE_TEAM_REMOTE_URL  → team MFE (기본 edge …/mfe/team)
 * - NEXT_PUBLIC_MFE_USAGE_REMOTE_URL → usage MFE (기본 edge …/mfe/usage)
 */

/** @type {import("next").NextConfig} */
const nextConfig = {
  basePath: "/teams",
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../.."),
  transpilePackages: ["@ai-usage/ui", "@ai-usage/shell", "@ai-usage/team-workspace-cache"],
  // Avoid bundling MF (and its `node:` imports) on the server; pairs with webpack externals below.
  serverExternalPackages: ["@module-federation/nextjs-mf"],
  webpack(config, { isServer }) {
    const reactRoot = resolvePackageRoot("react");
    const reactDomRoot = resolvePackageRoot("react-dom");
    const mfRemoteStub = path.join(__dirname, "src/stubs/mf-remote-stub.tsx");

    // 서버: MF 플러그인 없이 가상 모듈(team/*, usage/*)만 스텁으로 해석 → shared React 이중 로드·useContext(null) 방지.
    // 클라이언트: NextFederationPlugin + require.resolve react 별칭(Task37-2·MF 단일 React).
    if (isServer) {
      config.resolve.alias = {
        ...config.resolve.alias,
        "team/TeamManagement": mfRemoteStub,
        "usage/TeamUsageDashboard": mfRemoteStub,
      };
    } else if (reactRoot && reactDomRoot) {
      const jsxRuntime = safeResolve("react/jsx-runtime");
      const jsxDevRuntime = safeResolve("react/jsx-dev-runtime");
      config.resolve.alias = {
        ...config.resolve.alias,
        react: reactRoot,
        "react-dom": reactDomRoot,
        ...(jsxRuntime ? { "react/jsx-runtime": jsxRuntime } : {}),
        ...(jsxDevRuntime ? { "react/jsx-dev-runtime": jsxDevRuntime } : {}),
      };
    }

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
    if (!isServer) {
      config.plugins.push(
        new NextFederationPlugin({
          name: "host",
          remotes: {
            team: `team@${teamRemoteOrigin.replace(/\/$/, "")}/_next/static/chunks/remoteEntry.js`,
            usage: `usage@${usageRemoteOrigin.replace(/\/$/, "")}/_next/static/chunks/remoteEntry.js`,
          },
          filename: "static/chunks/remoteEntry.js",
          // strictVersion: false — 리모트 매니페스트와 호스트의 React 메타가 미세하게 달라도
          // loadShare(consumes)가 거부되지 않고 singleton으로 호스트 공유 인스턴스를 쓴다(Task37-5).
          // eager: 호스트 초기 번들에 고정해 loadShare 비동기 소비와 라우터 마운트 타이밍 경합 완화(Task37-7).
          // requiredVersion: false — semver 불일치로 인한 미세 rejection 완화(Task37-8).
          shared: {
            react: { singleton: true, strictVersion: false, requiredVersion: false, eager: true },
            "react-dom": { singleton: true, strictVersion: false, requiredVersion: false, eager: true },
          },
        })
      );
    }
    return config;
  },
};

export default nextConfig;
