# 패턴 B: 백엔드 전용 이미지 — 저장소 루트에서 빌드:
#   docker build -f services/notification-service/Dockerfile -t notification-service:local .
#
# 런타임은 워크스페이스 루트 node_modules 를 함께 복사한다(pnpm 호이스팅).

FROM node:22-alpine AS builder
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY services/notification-service/package.json ./services/notification-service/
RUN pnpm install --frozen-lockfile

COPY services/notification-service ./services/notification-service
WORKDIR /repo/services/notification-service
RUN pnpm run prisma:generate
RUN pnpm run build

FROM node:22-alpine AS runner
WORKDIR /repo
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nestjs

COPY --from=builder /repo/node_modules ./node_modules
COPY --from=builder /repo/package.json ./package.json
COPY --from=builder /repo/pnpm-workspace.yaml ./pnpm-workspace.yaml
COPY --from=builder /repo/pnpm-lock.yaml ./pnpm-lock.yaml
COPY --from=builder /repo/services/notification-service ./services/notification-service

USER nestjs
WORKDIR /repo/services/notification-service
EXPOSE 8096
CMD ["node", "dist/main.js"]
