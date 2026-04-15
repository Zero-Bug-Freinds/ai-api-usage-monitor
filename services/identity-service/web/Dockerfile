# Next.js standalone — docs/architecture.md §10.1 (패턴 B: 프론트 전용 이미지)
# `packages/ui`, `packages/shell` workspace 포함. 빌드(저장소 루트에서):
#   docker build -f services/identity-service/web/Dockerfile -t identity-web:local .

FROM node:22-alpine AS deps-local
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages/ui/package.json ./packages/ui/
COPY packages/shell/package.json ./packages/shell/
COPY services/identity-service/web/package.json ./services/identity-service/web/
RUN pnpm install --frozen-lockfile

ARG WEB_NODE_DEPS_IMAGE=deps-local
FROM ${WEB_NODE_DEPS_IMAGE} AS deps

FROM node:22-alpine AS builder
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/pnpm-lock.yaml ./pnpm-lock.yaml
COPY --from=deps /repo/pnpm-workspace.yaml ./pnpm-workspace.yaml
COPY --from=deps /repo/package.json ./package.json
COPY --from=deps /repo/packages/ui/package.json ./packages/ui/package.json
COPY --from=deps /repo/packages/shell/package.json ./packages/shell/package.json
COPY --from=deps /repo/services/identity-service/web/package.json ./services/identity-service/web/package.json

COPY packages/ui ./packages/ui
COPY packages/shell ./packages/shell
COPY services/identity-service/web ./services/identity-service/web

WORKDIR /repo/services/identity-service/web
ENV NEXT_TELEMETRY_DISABLED=1
ARG NEXT_PUBLIC_USAGE_WEB_ORIGIN=
ENV NEXT_PUBLIC_USAGE_WEB_ORIGIN=$NEXT_PUBLIC_USAGE_WEB_ORIGIN
RUN pnpm run build

FROM node:22-alpine AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

# outputFileTracingRoot=repo root → standalone 에 server.js 가 services/.../web/ 아래에 생김
COPY --from=builder --chown=nextjs:nodejs /repo/services/identity-service/web/.next/standalone /app
COPY --from=builder --chown=nextjs:nodejs /repo/services/identity-service/web/public /app/services/identity-service/web/public
COPY --from=builder --chown=nextjs:nodejs /repo/services/identity-service/web/.next/static /app/services/identity-service/web/.next/static

USER nextjs
WORKDIR /app/services/identity-service/web
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
