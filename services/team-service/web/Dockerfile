ARG WEB_NODE_DEPS_IMAGE=deps-local

FROM node:22-alpine AS deps-local
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages/ui/package.json ./packages/ui/
COPY services/team-service/web/package.json ./services/team-service/web/
RUN pnpm install --frozen-lockfile

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
COPY --from=deps /repo/services/team-service/web/package.json ./services/team-service/web/package.json
COPY --from=deps /repo/services/team-service/web/node_modules ./services/team-service/web/node_modules

COPY packages/ui ./packages/ui
COPY services/team-service/web ./services/team-service/web

WORKDIR /repo/services/team-service/web
ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_PRIVATE_LOCAL_WEBPACK=true
RUN pnpm run build

FROM node:22-alpine AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder --chown=nextjs:nodejs /repo/services/team-service/web/.next/standalone /app
COPY --from=builder --chown=nextjs:nodejs /repo/services/team-service/web/public /app/services/team-service/web/public
COPY --from=builder --chown=nextjs:nodejs /repo/services/team-service/web/.next/static /app/services/team-service/web/.next/static

USER nextjs
WORKDIR /app/services/team-service/web
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
