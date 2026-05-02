# syntax=docker/dockerfile:1.7
# Next.js standalone host shell image
# Build from repo root:
#   docker build --build-arg WEB_NODE_DEPS_IMAGE=web-node-deps:local -f apps/web/Dockerfile -t web-host:local .

ARG WEB_NODE_DEPS_IMAGE=deps-local

FROM node:22-alpine AS deps-local
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages/ui/package.json ./packages/ui/
COPY packages/shell/package.json ./packages/shell/
COPY packages/team-workspace-cache/package.json ./packages/team-workspace-cache/
COPY apps/web/package.json ./apps/web/
RUN --mount=type=cache,id=pnpm-store,target=/pnpm/store \
    pnpm config set store-dir /pnpm/store && \
    pnpm install --frozen-lockfile

FROM ${WEB_NODE_DEPS_IMAGE} AS deps

FROM node:22-alpine AS builder
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/pnpm-lock.yaml ./pnpm-lock.yaml
COPY --from=deps /repo/pnpm-workspace.yaml ./pnpm-workspace.yaml
COPY --from=deps /repo/package.json ./package.json
COPY --from=deps /repo/packages/ui/package.json ./packages/ui/package.json
COPY --from=deps /repo/packages/ui/node_modules ./packages/ui/node_modules
COPY --from=deps /repo/packages/shell/package.json ./packages/shell/package.json
COPY --from=deps /repo/packages/shell/node_modules ./packages/shell/node_modules
COPY --from=deps /repo/packages/team-workspace-cache/package.json ./packages/team-workspace-cache/package.json
COPY --from=deps /repo/packages/team-workspace-cache/node_modules ./packages/team-workspace-cache/node_modules
COPY --from=deps /repo/apps/web/package.json ./apps/web/package.json
COPY --from=deps /repo/apps/web/node_modules ./apps/web/node_modules

COPY packages/ui ./packages/ui
COPY packages/shell ./packages/shell
COPY packages/team-workspace-cache ./packages/team-workspace-cache
COPY apps/web ./apps/web

WORKDIR /repo/apps/web
ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_PRIVATE_LOCAL_WEBPACK=true
ARG NEXT_PUBLIC_MFE_TEAM_REMOTE_URL=http://localhost:8888/mfe/team
ARG NEXT_PUBLIC_MFE_USAGE_REMOTE_URL=http://localhost:8888/mfe/usage
ENV NEXT_PUBLIC_MFE_TEAM_REMOTE_URL=${NEXT_PUBLIC_MFE_TEAM_REMOTE_URL}
ENV NEXT_PUBLIC_MFE_USAGE_REMOTE_URL=${NEXT_PUBLIC_MFE_USAGE_REMOTE_URL}
RUN --mount=type=cache,id=pnpm-store,target=/pnpm/store \
    --mount=type=cache,target=/repo/apps/web/.next/cache \
    pnpm config set store-dir /pnpm/store && \
    pnpm run build

FROM node:22-alpine AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder --chown=nextjs:nodejs /repo/apps/web/.next/standalone /app
COPY --from=builder --chown=nextjs:nodejs /repo/apps/web/public /app/apps/web/public
COPY --from=builder --chown=nextjs:nodejs /repo/apps/web/.next/static /app/apps/web/.next/static

USER nextjs
WORKDIR /app/apps/web
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
