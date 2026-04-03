# Next.js standalone — docs/architecture.md §10.1 (패턴 B: 프론트 전용 이미지)
# `packages/ui` workspace 포함. 빌드(저장소 루트에서):
#   docker build -f services/identity-service/web/Dockerfile -t identity-web:local .

FROM node:22-alpine AS builder
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages/ui/package.json ./packages/ui/
COPY services/identity-service/web/package.json ./services/identity-service/web/
RUN pnpm install --frozen-lockfile

COPY packages/ui ./packages/ui
COPY services/identity-service/web ./services/identity-service/web

WORKDIR /repo/services/identity-service/web
ENV NEXT_TELEMETRY_DISABLED=1
ARG NEXT_PUBLIC_USAGE_WEB_ORIGIN=
ENV NEXT_PUBLIC_USAGE_WEB_ORIGIN=$NEXT_PUBLIC_USAGE_WEB_ORIGIN
RUN pnpm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder /repo/services/identity-service/web/public ./public
COPY --from=builder --chown=nextjs:nodejs /repo/services/identity-service/web/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /repo/services/identity-service/web/.next/static ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
