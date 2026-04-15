ARG WEB_NODE_DEPS_IMAGE=deps-local

FROM node:22-alpine AS deps-local
WORKDIR /repo

# corepack 활성화 (pnpm 준비)
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY packages/ui/package.json ./packages/ui/
COPY packages/shell/package.json ./packages/shell/
COPY services/team-service/web/package.json ./services/team-service/web/

# 의존성 설치
RUN pnpm install --frozen-lockfile

FROM ${WEB_NODE_DEPS_IMAGE} AS deps
FROM node:22-alpine AS builder
WORKDIR /repo

# corepack 활성화 (pnpm 준비)
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/pnpm-lock.yaml ./pnpm-lock.yaml
COPY --from=deps /repo/pnpm-workspace.yaml ./pnpm-workspace.yaml
COPY --from=deps /repo/package.json ./package.json
COPY --from=deps /repo/packages/ui/package.json ./packages/ui/package.json
COPY --from=deps /repo/packages/shell/package.json ./packages/shell/package.json
COPY --from=deps /repo/packages/ui/node_modules ./packages/ui/node_modules
COPY --from=deps /repo/packages/shell/node_modules ./packages/shell/node_modules
COPY --from=deps /repo/services/team-service/web/package.json ./services/team-service/web/package.json
COPY --from=deps /repo/services/team-service/web/node_modules ./services/team-service/web/node_modules

# [중요] 리팩터링된 소스 코드 전체 복사
# Tailwind가 packages/shell/src 내의 파일을 스캔해야 하므로 통째로 복사합니다.
COPY packages ./packages
COPY services/team-service/web ./services/team-service/web

# 빌드 진행
WORKDIR /repo/services/team-service/web
ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_PRIVATE_LOCAL_WEBPACK=true
RUN pnpm run build

# 2. 실행 스테이지
FROM node:22-alpine AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

# Standalone 결과물 복사
COPY --from=builder --chown=nextjs:nodejs /repo/services/team-service/web/.next/standalone /app
COPY --from=builder --chown=nextjs:nodejs /repo/services/team-service/web/public /app/services/team-service/web/public
COPY --from=builder --chown=nextjs:nodejs /repo/services/team-service/web/.next/static /app/services/team-service/web/.next/static

USER nextjs
WORKDIR /app/services/team-service/web
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

CMD ["node", "server.js"]