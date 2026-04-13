# Notification Service (Node / NestJS)

`docs/architecture.md` §12 알림 파이프라인의 **백엔드** 스캐폴딩. 브라우저 UI·BFF는 추후 `web/` 로 분리할 수 있다(`project-common-nextjs.mdc`).

## 로컬 실행

1. 루트에서 `pnpm install` (워크스페이스에 포함됨).
2. PostgreSQL에 DB·유저 준비 후 `.env`에 `DATABASE_URL` 설정 (`.env.example` 참고).
3. `pnpm prisma:migrate:dev` 로 마이그레이션 적용.
4. `pnpm start:dev` (기본 포트 `8096`, billing과 구분).

- Health: `GET /health`
- OpenAPI: `GET /api/docs`

Cursor 규칙: `.cursor/rules/notification-backend-node.mdc`
