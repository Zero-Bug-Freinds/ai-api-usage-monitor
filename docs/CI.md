# GitHub Actions CI (팀 정본)

`docs/architecture.md` §8.2(비밀 스캔 권장), §10(GitHub Actions 도입) 및 모노레포 구조에 맞춘 CI 요약이다. 워크플로 정의는 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)이 정본이다.

## 트리거·브랜치

- `push`: `main`, `develop`
- `pull_request`: 대상 브랜치가 `main` 또는 `develop`일 때

팀 브랜치 전략은 [`docs/branch-conventions.md`](branch-conventions.md)를 따른다.

## 잡 요약

| 잡 | 설명 |
|----|------|
| **Detect changed paths** | `dorny/paths-filter`로 `services/identity-service/**`, `services/proxy-service/**`, `services/api-gateway-service/**`, `services/usage-service/**`, `libs/usage-events/**`, `services/identity-service/web/**`, `services/usage-service/web/**`, `packages/ui/**`, 루트 `pnpm-workspace.yaml`·`package.json`·`pnpm-lock.yaml`, `.github/workflows/**` 변경 감지 |
| **Secret scan (gitleaks)** | 매 실행마다 저장소 스캔(§8.2). 루트 **`.env.example`** 에는 팀 합의 **로컬 전용 플레이스홀더**(`GATEWAY_SHARED_SECRET` 등)가 문서화되어 있으며, 오탐 방지를 위해 [`.gitleaks.toml`](../.gitleaks.toml) allowlist에 포함한다. |
| **Validate docker-compose.yml** | `docker compose config`로 문법 검증(§10) |
| **Build identity-service** | Java 21(Temurin), Gradle 캐시, `./gradlew build` — 위 경로 필터 또는 워크플로 변경 시에만 실행 |
| **Build proxy-gateway-service** | 동일 — proxy·gateway·`libs/usage-events`·워크플로 변경 시 실행 |
| **Build usage-service** | 동일 — usage·`libs/usage-events`·워크플로 변경 시 실행 |
| **Build web (lint/test/build)** | Node 22, **pnpm** 9, 저장소 루트 `pnpm install --frozen-lockfile` 후 각 `web`에 `pnpm --filter …` 로 lint·test·`pnpm run build:web`, **`docker build -f services/…/web/Dockerfile`**(context `.`) 등 — 팀 표준 프론트는 **Next.js 15**·**React 19**(`package.json` 정본). 경로: `services/**/web/**`, `packages/ui/**`, 루트 pnpm·워크플로 변경 시 실행(`web-mfe` 독립 잡은 후속 도입 가능) |
| **CI summary** | gitleaks 성공 필수, 실행된 빌드·Compose 잡이 `failure`/`cancelled`이면 실패. 스킵된 잡은 허용 (`web` 포함) |

## Docker 빌드 캐시 (로컬 vs CI)

- **로컬 (`docker compose build` / `up --build`)**: 루트 `docker-compose.yml`에는 **container registry 기반 `cache_from` / `cache_to`를 두지 않는다**. 팀원별 GHCR 로그인을 요구하지 않으며, 각 서비스 Dockerfile의 `RUN --mount=type=cache`(pnpm store, Gradle, Next `.next/cache` 등)로 반복 빌드 시간을 줄인다.
- **CI (GitHub Actions)**: BuildKit **GitHub Actions cache**(`type=gha`)로 베이스 이미지 등을 캐시한다. 워크플로 잡 **`build-common-docker`**에서 `docker/setup-buildx-action`과 `docker/build-push-action`을 사용하며, **`scope`** 로 캐시를 분리한다(`web-node-deps`, `backend-node-deps`). 해당 잡은 캐시 저장을 위해 **`actions: write`** 권한이 필요하다([`.github/workflows/ci.yml`](../.github/workflows/ci.yml) 참고).

동일 패턴으로 다른 이미지를 빌드할 때 예시는 다음과 같다.

```yaml
- uses: docker/setup-buildx-action@v3
- uses: docker/build-push-action@v6
  with:
    context: .
    file: path/to/Dockerfile
    tags: myimage:ci
    load: true
    cache-from: type=gha,scope=my-scope
    cache-to: type=gha,mode=max,scope=my-scope
```

장기적으로 각 잡의 `docker build` CLI 호출을 위 패턴으로 통일하면 서비스별 **`scope`** 로 GHA 캐시 재사용을 일관되게 적용할 수 있다.

## 브랜치 보호(Branch protection)

[`docs/branch-conventions.md`](branch-conventions.md) §4에 따라 `develop`/`main`에 **상태 검사**를 걸 때, GitHub에서 표시되는 이름이 **`CI summary`** 인 잡을 필수로 지정하면 된다. (워크플로 이름은 `CI`.)

## 로컬에서 워크플로와 동일한 검증

### 지출 API 체인 스모크 (선택)

호스트에서 billing·게이트웨이를 띄운 뒤 저장소 루트에서 `scripts/verify-expenditure-chain.sh`(또는 Windows `scripts/verify-expenditure-chain.ps1`)를 실행한다. `GATEWAY_DEV_MODE=false`일 때는 JWT 검증 경로까지 포함하려면 `.env`에 `EXPENDITURE_VERIFY_GATEWAY_JWT` 또는 identity 로그인용 `EXPENDITURE_VERIFY_LOGIN_*`를 두고, **`GATEWAY_JWT_SECRET`과 `JWT_SECRET`이 일치**하는지 확인한다. 상세는 `docs/billing-service-overview-20260412.md` §6.4.

```bash
# Compose
docker compose -f docker-compose.yml config

# Next (루트 pnpm workspace)
pnpm install --frozen-lockfile
pnpm --filter identity-web run lint
pnpm --filter usage-web run lint
pnpm --filter identity-web test
pnpm --filter usage-web test
pnpm run build:web

# Java (각 모듈 디렉터리에서)
./gradlew build
```

## 통합 테스트·브로커/DB (향후)

`docs/architecture.md` §6.2(RabbitMQ), §10(Compose)에 맞춰, 통합 테스트를 CI에 넣을 때는 다음 중 하나를 검토한다.

- GitHub Actions `services:` 로 Postgres·RabbitMQ 컨테이너 기동 후 테스트 실행
- **Testcontainers** 로 테스트 코드에서 컨테이너 기동

WebFlux 프록시에서 블로킹 AMQP 호출은 §6.2 주의사항과 동일하게 테스트 설계에 반영한다.

## CD(배포) — 비목표 전제

`docs/architecture.md` §3.3·§10: **Kubernetes 클러스터 배포는 범위 밖**이다. CD를 도입할 때는 **`docs/architecture.md` §10.1 패턴 B**에 맞춰 **백엔드·프론트(각 `services/*/web`) 이미지를 각각 빌드**·레지스트리 push하고, 대상 환경에서 **Docker Compose**로 스택 기동, 비밀값은 **GitHub Secrets·환경변수** 등으로 관리하는 방향을 따른다.

## PR에서 Actions 확인

변경을 푸시한 뒤 GitHub 저장소의 **Actions** 탭에서 워크플로 실행 결과를 확인한다. 첫 `gitleaks` 실행에서 과거 커밋의 민감 문자열이 잡히면, 팀 정책에 따라 히스토리 정리·규칙 예외를 검토한다.

## 빌드 및 린트 트러블슈팅 (Troubleshooting)

### 1. 외부 라이브러리 타입 충돌 (예: Recharts Legend)

- **현상**: `next build` 시 라이브러리 내부 타입 정의 문제로 `Property 'payload' does not exist` 등의 에러 발생.
- **해결 원칙**: 라이브러리 타입을 강제로 맞추려 하기보다, **컴포넌트 수준의 타입 우회**를 권장한다. 단, 이는 빌드 블로커가 발생한 특정 컴포넌트에만 국한하여 적용한다.

```tsx
// 추천하는 해결 방식 (필요한 경우에만)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const AnyLegend = Legend as any

// 사용 시
<AnyLegend payload={customData} />
```
