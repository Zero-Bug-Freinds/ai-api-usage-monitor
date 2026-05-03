# syntax=docker/dockerfile:1.7
ARG WEB_NODE_DEPS_IMAGE=deps-local

FROM node:22-alpine AS deps-local
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages/ui/package.json ./packages/ui/
COPY packages/shell/package.json ./packages/shell/
COPY packages/team-workspace-cache/package.json ./packages/team-workspace-cache/
COPY services/usage-service/web/package.json ./services/usage-service/web/
COPY services/usage-service/web-mfe/package.json ./services/usage-service/web-mfe/
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
COPY --from=deps /repo/services/usage-service/web/package.json ./services/usage-service/web/package.json
COPY --from=deps /repo/services/usage-service/web/node_modules ./services/usage-service/web/node_modules
COPY --from=deps /repo/services/usage-service/web-mfe/package.json ./services/usage-service/web-mfe/package.json
COPY --from=deps /repo/services/usage-service/web-mfe/node_modules ./services/usage-service/web-mfe/node_modules

COPY packages/ui ./packages/ui
COPY packages/shell ./packages/shell
COPY packages/team-workspace-cache ./packages/team-workspace-cache
COPY services/usage-service/web ./services/usage-service/web
COPY services/usage-service/web-mfe ./services/usage-service/web-mfe

WORKDIR /repo/services/usage-service/web-mfe
ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_PRIVATE_LOCAL_WEBPACK=true
RUN --mount=type=cache,id=pnpm-store,target=/pnpm/store \
    --mount=type=cache,target=/repo/services/usage-service/web-mfe/.next/cache \
    pnpm config set store-dir /pnpm/store && \
    pnpm run build

FROM node:22-alpine AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder --chown=nextjs:nodejs /repo/services/usage-service/web-mfe/.next/standalone /app
COPY --from=builder --chown=nextjs:nodejs /repo/services/usage-service/web-mfe/.next/static /app/services/usage-service/web-mfe/.next/static

USER nextjs
WORKDIR /app/services/usage-service/web-mfe
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
