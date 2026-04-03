# Next.js standalone — docs/architecture.md §10.1 (패턴 B: 프론트 전용 이미지)
# 빌드: docker build -t identity-web:local .
# 실행 시 IDENTITY_SERVICE_URL 등은 compose 또는 -e 로 주입(Identity BFF는 게이트웨이 URL을 쓰지 않음)

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
# 브라우저 번들에 인라인 — 로컬 분리 포트 시 compose build.args 로 주입
ARG NEXT_PUBLIC_USAGE_WEB_ORIGIN=
ENV NEXT_PUBLIC_USAGE_WEB_ORIGIN=$NEXT_PUBLIC_USAGE_WEB_ORIGIN
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
