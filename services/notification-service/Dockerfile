# 패턴 B: 백엔드 전용 이미지 — 저장소 루트에서 빌드:
#   docker build -f services/notification-service/Dockerfile -t notification-service:local .
#
# 런타임은 워크스페이스 루트 node_modules 를 함께 복사한다(pnpm 호이스팅).

# Prisma 엔진(OpenSSL) 호환성 이슈를 피하기 위해 Debian 계열 이미지를 사용한다.
ARG BACKEND_NODE_DEPS_IMAGE=deps-local

FROM node:22-bookworm-slim AS deps-local
WORKDIR /repo
RUN apt-get update -y \
  && apt-get install -y --no-install-recommends openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY services/notification-service/package.json ./services/notification-service/
RUN pnpm install --frozen-lockfile --ignore-scripts

FROM ${BACKEND_NODE_DEPS_IMAGE} AS deps

FROM node:22-bookworm-slim AS builder
WORKDIR /repo
RUN apt-get update -y \
  && apt-get install -y --no-install-recommends openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/package.json ./package.json
COPY --from=deps /repo/pnpm-workspace.yaml ./pnpm-workspace.yaml
COPY --from=deps /repo/pnpm-lock.yaml ./pnpm-lock.yaml
COPY --from=deps /repo/services/notification-service/package.json ./services/notification-service/package.json
COPY --from=deps /repo/services/notification-service/node_modules ./services/notification-service/node_modules

COPY services/notification-service ./services/notification-service
WORKDIR /repo/services/notification-service
RUN pnpm run prisma:generate
RUN pnpm run build

FROM node:22-bookworm-slim AS runner
WORKDIR /repo
ENV NODE_ENV=production
RUN apt-get update -y \
  && apt-get install -y --no-install-recommends openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nestjs

COPY --from=builder /repo/node_modules ./node_modules
COPY --from=builder /repo/package.json ./package.json
COPY --from=builder /repo/pnpm-workspace.yaml ./pnpm-workspace.yaml
COPY --from=builder /repo/pnpm-lock.yaml ./pnpm-lock.yaml
COPY --from=builder /repo/services/notification-service ./services/notification-service

RUN sed -i 's/\r$//' ./services/notification-service/docker-entrypoint.sh \
  && chmod +x ./services/notification-service/docker-entrypoint.sh

# Prisma가 런타임에 engines 디렉터리에 파일을 쓸 수 있어야 한다(권한 이슈 회피).
# pnpm 가상스토어 경로는 버전에 따라 달라질 수 있어 glob 처리.
RUN chown -R nestjs:nodejs /repo/node_modules/.pnpm/@prisma+engines*/node_modules/@prisma/engines 2>/dev/null || true

USER nestjs
WORKDIR /repo/services/notification-service
EXPOSE 8096
CMD ["sh", "./docker-entrypoint.sh"]
