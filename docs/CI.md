# GitHub Actions CI (팀 정본)

`docs/architecture.md` §8.2(비밀 스캔 권장), §10(GitHub Actions 도입) 및 모노레포 구조에 맞춘 CI 요약이다. 워크플로 정의는 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)이 정본이다.

## 트리거·브랜치

- `push`: `main`, `develop`
- `pull_request`: 대상 브랜치가 `main` 또는 `develop`일 때

팀 브랜치 전략은 [`docs/branch-conventions.md`](branch-conventions.md)를 따른다.

## 잡 요약

| 잡 | 설명 |
|----|------|
| **Detect changed paths** | `dorny/paths-filter`로 `web_edge`, `api_gateway`, `identity`, `proxy`, `usage`, `billing`, `team`, `notification`, `web_host`, `web_mfe`, `web_shared`, `workflows` 변경 감지 |
| **Secret scan (gitleaks)** | 매 실행마다 저장소 스캔(§8.2). 루트 **`.env.example`** 에는 팀 합의 **로컬 전용 플레이스홀더**(`GATEWAY_SHARED_SECRET` 등)가 문서화되어 있으며, 오탐 방지를 위해 [`.gitleaks.toml`](../.gitleaks.toml) allowlist에 포함한다. |
| **Validate docker-compose.yml** | `docker compose config`로 문법 검증(§10) |
| **Validate web-edge** | `web_edge` 변경 시 `docker compose config` + `nginx -t`(template envsubst 포함) 검증 |
| **Build api-gateway-service** | Java 21 + Gradle build/test + `docker/build-push-action@v6` (`type=gha`, scope `api-gateway-service`) |
| **Build proxy-service** | Java 21 + Gradle build/test + `docker/build-push-action@v6` (`type=gha`, scope `proxy-service`) |
| **Build usage-service** | Java 21 + usage-web lint/test/build + usage-service/usage-web 이미지 빌드(`type=gha`) |
| **Build billing-service** | Java 21 + billing-web lint/test/build + billing-service/billing-web 이미지 빌드(`type=gha`) |
| **Build team-service** | Java 21 + team-service backend build (web-mfe는 별도 web-mfe job에서 검증) |
| **Build notification-service** | Nest build + notification-web lint/test/build + notification-service/notification-web 이미지 빌드(`type=gha`) |
| **Build identity-service** | Java 21 + identity-web lint/test/build + identity-web 이미지 빌드(`type=gha`) |
| **Build web-mfe remotes** | usage/team `web-mfe` lint/typecheck/build + 각 이미지 빌드(`type=gha`) |
| **Build web-host** | 현재 `if: false`로 비활성(긴급 우회) |
| **CI observability metrics** | Actions API로 job duration/결과를 수집해 요약 |
| **CI summary** | gitleaks 성공 필수, 실행된 빌드·Compose 잡이 `failure`/`cancelled`이면 실패. 스킵된 잡은 허용 (`web` 포함) |

## Docker 빌드 캐시 (로컬 vs CI)

- **로컬 (`docker compose build` / `up --build`)**: 루트 `docker-compose.yml`에는 **container registry 기반 `cache_from` / `cache_to`를 두지 않는다**. 즉, **팀원은 GHCR 로그인 없이도** 동일한 Compose 명령으로 개발을 시작할 수 있다. 캐시는 각 서비스 Dockerfile의 `RUN --mount=type=cache`(pnpm store, Gradle, Next `.next/cache` 등)로 처리해 반복 빌드 시간을 줄인다.
- **CI (GitHub Actions)**: 서비스별 job에서 `docker/build-push-action@v6` + BuildKit **`type=gha` cache**를 사용한다. `scope`는 이미지 단위(`api-gateway-service`, `proxy-service`, `usage-service`, `usage-web`, `billing-service`, `billing-web`, `notification-service`, `notification-web`, `identity-web`, `usage-web-mfe`, `team-web-mfe`)로 분리한다.

- **권한 최소화(보안)**: 전역 권한은 `contents: read`, `actions: read`를 유지하고, `cache-to: type=gha`를 사용하는 서비스 빌드 잡에서만 `actions: write`를 잡 단위로 부여한다.

현재 워크플로의 서비스 이미지 빌드는 아래 패턴으로 통일되어 있다.

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

`ci.yml`은 기존 Stage A 공통 이미지(`build-common-*`) 전략을 제거하고, 변경된 서비스만 독립적으로 빌드하도록 정리되어 있다.

### `scope` 네이밍·용량(10GB) 표준

- **네이밍 규칙**: 이미지 태그(`*-web:ci`)와 동일한 문자열을 `scope`로 사용한다.
  - 예: `identity-web` → `scope=identity-web`, `usage-web` → `scope=usage-web`, `billing-web` → `scope=billing-web`, `notification-web` → `scope=notification-web`
  - MFE(remote)도 동일 규칙 적용: `usage-web-mfe` → `scope=usage-web-mfe`, `team-web-mfe` → `scope=team-web-mfe`
  - 현재 `ci.yml` 기준 공통 Stage A 캐시는 사용하지 않고, 서비스별 scope 중심으로 운영한다.
- **용량 정책(무료 티어 10GB)**:
  - 기본값은 **`cache-to: type=gha,mode=min`** (폭증 방지).
  - 캐시 히트율이 낮고 용량 여유가 충분할 때만 특정 scope을 **`mode=max`** 로 상향한다.
- **권한**: `cache-to: type=gha` 를 사용하는 잡은 `actions: write`가 필요하므로 잡 단위로만 부여한다(최소 권한).

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

## `.env` / `hybrid.env` 운용 기준

- 기본 로컬 값은 루트 `.env`(원본 Compose 모드) 기준으로 유지한다.
- `hybrid.env`는 **로컬 오버라이드 변수만** 담는다(예: `GATEWAY_*_URI`, `WEB_IDENTITY_SERVICE_URL`).
- Compose 환경 변수 우선순위는 일반적으로 **셸 환경 변수 > `--env-file` > `.env` > 파일 내 기본값** 순서를 따른다.
- 같은 키를 여러 곳에 중복 정의하면 실행 시점 값이 달라질 수 있으므로, 하이브리드 전용 키만 `hybrid.env`에 둔다.

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
