# Next.js standalone — 패턴은 usage-web 과 동일 (저장소 루트에서 빌드).
#   docker build -f services/notification-service/web/Dockerfile -t notification-web:local .

FROM node:22-alpine AS builder
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages/ui/package.json ./packages/ui/
COPY packages/shell/package.json ./packages/shell/
COPY services/notification-service/web/package.json ./services/notification-service/web/
RUN pnpm install --frozen-lockfile

COPY packages/ui ./packages/ui
COPY packages/shell ./packages/shell
COPY services/notification-service/web ./services/notification-service/web

WORKDIR /repo/services/notification-service/web
ENV NEXT_TELEMETRY_DISABLED=1
ARG NEXT_PUBLIC_BASE_PATH=/notifications
ENV NEXT_PUBLIC_BASE_PATH=$NEXT_PUBLIC_BASE_PATH
RUN pnpm run build

FROM node:22-alpine AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder --chown=nextjs:nodejs /repo/services/notification-service/web/.next/standalone /app
COPY --from=builder --chown=nextjs:nodejs /repo/services/notification-service/web/public /app/services/notification-service/web/public
COPY --from=builder --chown=nextjs:nodejs /repo/services/notification-service/web/.next/static /app/services/notification-service/web/.next/static

USER nextjs
WORKDIR /app/services/notification-service/web
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]

