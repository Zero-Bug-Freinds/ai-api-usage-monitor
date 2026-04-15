FROM node:22-alpine AS builder
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages/ui/package.json ./packages/ui/
COPY packages/shell/package.json ./packages/shell/
COPY services/team-service/web-mfe/package.json ./services/team-service/web-mfe/
RUN pnpm install --frozen-lockfile

COPY packages/ui ./packages/ui
COPY packages/shell ./packages/shell
COPY services/team-service/web-mfe ./services/team-service/web-mfe

WORKDIR /repo/services/team-service/web-mfe
ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_PRIVATE_LOCAL_WEBPACK=true
RUN pnpm run build

FROM node:22-alpine AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder --chown=nextjs:nodejs /repo/services/team-service/web-mfe/.next/standalone /app
COPY --from=builder --chown=nextjs:nodejs /repo/services/team-service/web-mfe/.next/static /app/services/team-service/web-mfe/.next/static

USER nextjs
WORKDIR /app/services/team-service/web-mfe
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
