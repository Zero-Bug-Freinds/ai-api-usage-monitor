# Shared pnpm install layer for monorepo web images.
# Build from repository root:
#   docker build -f docker/web-node-deps.Dockerfile -t web-node-deps:local .

FROM node:22-alpine
WORKDIR /repo
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate

COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/web/package.json ./apps/web/
COPY packages/ui/package.json ./packages/ui/
COPY packages/shell/package.json ./packages/shell/
COPY services/identity-service/web/package.json ./services/identity-service/web/
COPY services/usage-service/web/package.json ./services/usage-service/web/
COPY services/usage-service/web-mfe/package.json ./services/usage-service/web-mfe/
COPY services/billing-service/web/package.json ./services/billing-service/web/
COPY services/team-service/web/package.json ./services/team-service/web/
COPY services/team-service/web-mfe/package.json ./services/team-service/web-mfe/
COPY services/notification-service/web/package.json ./services/notification-service/web/

RUN pnpm install --frozen-lockfile
