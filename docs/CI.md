# GitHub Actions CI (팀 정본)

`docs/architecture.md` §8.2(비밀 스캔 권장), §10(GitHub Actions 도입) 및 모노레포 구조에 맞춘 CI 요약이다. 워크플로 정의는 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)이 정본이다.

## 트리거·브랜치

- `push`: `main`, `develop`
- `pull_request`: 대상 브랜치가 `main` 또는 `develop`일 때

팀 브랜치 전략은 [`docs/branch-conventions.md`](branch-conventions.md)를 따른다.

## 잡 요약

| 잡 | 설명 |
|----|------|
| **Detect changed paths** | `dorny/paths-filter`로 `services/identity-service/**`, `services/proxy-gateway-service/**`, `libs/usage-events/**`, `.github/workflows/**` 변경 감지 |
| **Secret scan (gitleaks)** | 매 실행마다 저장소 스캔(§8.2) |
| **Validate docker-compose.yml** | `docker compose config`로 문법 검증(§10) |
| **Build identity-service** | Java 21(Temurin), Gradle 캐시, `./gradlew build` — 위 경로 필터 또는 워크플로 변경 시에만 실행 |
| **Build proxy-gateway-service** | 동일 — proxy·`libs/usage-events`·워크플로 변경 시 실행 |
| **CI summary** | gitleaks 성공 필수, 실행된 빌드·Compose 잡이 `failure`/`cancelled`이면 실패. 스킵된 잡은 허용 |

## 브랜치 보호(Branch protection)

[`docs/branch-conventions.md`](branch-conventions.md) §4에 따라 `develop`/`main`에 **상태 검사**를 걸 때, GitHub에서 표시되는 이름이 **`CI summary`** 인 잡을 필수로 지정하면 된다. (워크플로 이름은 `CI`.)

## 로컬에서 워크플로와 동일한 검증

```bash
# Compose
docker compose -f docker-compose.yml config

# Java (각 모듈 디렉터리에서)
./gradlew build
```

## 통합 테스트·브로커/DB (향후)

`docs/architecture.md` §6.2(RabbitMQ), §10(Compose)에 맞춰, 통합 테스트를 CI에 넣을 때는 다음 중 하나를 검토한다.

- GitHub Actions `services:` 로 Postgres·RabbitMQ 컨테이너 기동 후 테스트 실행
- **Testcontainers** 로 테스트 코드에서 컨테이너 기동

WebFlux 프록시에서 블로킹 AMQP 호출은 §6.2 주의사항과 동일하게 테스트 설계에 반영한다.

## CD(배포) — 비목표 전제

`docs/architecture.md` §3.3·§10: **Kubernetes 클러스터 배포는 범위 밖**이다. CD를 도입할 때는 Docker 이미지 빌드·레지스트리 push, 대상 환경에서의 **Docker Compose** 기동, 비밀값은 **GitHub Secrets·환경변수** 등으로 관리하는 방향을 따른다.

## PR에서 Actions 확인

변경을 푸시한 뒤 GitHub 저장소의 **Actions** 탭에서 워크플로 실행 결과를 확인한다. 첫 `gitleaks` 실행에서 과거 커밋의 민감 문자열이 잡히면, 팀 정책에 따라 히스토리 정리·규칙 예외를 검토한다.
