# Next.js standalone host shell image
# Build from repo root:
#   docker build -f apps/web/Dockerfile -t web-host:local .

FROM node:22-alpine AS builder
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages/ui/package.json ./packages/ui/
COPY packages/shell/package.json ./packages/shell/
COPY apps/web/package.json ./apps/web/
RUN pnpm install --frozen-lockfile

COPY packages/ui ./packages/ui
COPY packages/shell ./packages/shell
COPY apps/web ./apps/web

WORKDIR /repo/apps/web
ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_PRIVATE_LOCAL_WEBPACK=true
RUN pnpm run build

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
