# Shared pnpm install layer for Node backend images.
# Build from repository root:
#   docker build -f docker/backend-node-deps.Dockerfile -t backend-node-deps:local .

FROM node:22-bookworm-slim
WORKDIR /repo
RUN apt-get update -y \
  && apt-get install -y --no-install-recommends openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY services/notification-service/package.json ./services/notification-service/

RUN pnpm install --frozen-lockfile --ignore-scripts
