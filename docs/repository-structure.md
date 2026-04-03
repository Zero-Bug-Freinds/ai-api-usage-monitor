# 저장소·디렉터리 구조 (모노레포)

Gradle/Maven 등 **빌드 도구는 팀 설정을 따른다.** 본 문서는 **폴더 역할과 규칙**만 정의한다. 아래 서비스·모듈 **이름은 예시**이며, 실제로는 `docs/architecture.md`의 도메인(Proxy, Identity, Billing 등)에 맞춰 **팀이 정한 이름**을 사용한다.

---

## 1. 모노레포 원칙

- **단일 Git 저장소**에서 여러 마이크로서비스와 공유 라이브러리를 관리한다.
- 서비스 간 **런타임 결합**은 **HTTP API·메시지 큐(이벤트)** 로만 한다. (상세: `docs/architecture.md`)
- **저장소 구조**는 “소스 트리를 나누는 방식”일 뿐, MSA의 **경계·통신 방식**을 바꾸지 않는다.

---

## 2. 권장 디렉터리 역할 (개념)

```
<repo-root>/
├── services/              # 실행 가능한 마이크로서비스(애플리케이션) — 이름은 도메인별로 부여
│   ├── <service-a>/       # 예: Gradle 루트 + (선택) web/ — UI·BFF가 필요한 서비스만 Next 앱
│   │   └── web/           # 선택: 해당 도메인의 Next.js(App Router, BFF Route Handler)
│   ├── <service-b>/
│   └── ...
├── libs/                  # 공유 라이브러리 모듈(범위는 §3 참고)
│   ├── <shared-lib-1>/    # 예: API 계약, 이벤트 스키마, 공통 유틸 등
│   └── ...
├── packages/              # (선택) npm/pnpm workspace — UI 공유만, §6·§3 취지 준수
│   └── ...
├── apps/                  # (선택) 이행 안내 — 통합 Next는 `services/*/web/`로 이전(`apps/web/README.md`)
├── docs/                  # 설계·컨벤션·아키텍처 문서 (본 문서 포함)
├── scripts/               # (선택) 빌드·로컬 실행 보조 스크립트
├── docker-compose.yml     # 루트: 로컬 의존성(PostgreSQL, RabbitMQ, Redis 등)
├── README.md
├── package.json           # (workspace 도입 시) pnpm/npm workspaces 루트
└── (루트 빌드 설정)       # 예: settings.gradle + build.gradle, 또는 루트 pom.xml 등
```

- **`services/`**: 배포·실행 단위가 되는 **Spring Boot 앱**(또는 동등한 실행 형태)을 둔다. **폴더명 = 예시가 아닌 실제 서비스 이름**으로 정한다. **사용자 대면 UI·BFF가 있는 도메인**은 같은 폴더 아래 **`web/`**(Next.js)로 소유권을 맞춘다(풀스택 담당). API 전용 서비스(예: `proxy-service`, `api-gateway-service`)는 `web/`을 두지 않아도 된다.
- **`libs/`**: 아래 §3의 **허용 범위만** 공유한다(JVM·이벤트·계약 위주).
- **`packages/`** (npm/pnpm workspace, 도입 시): **UI 토큰, 공용 Shadcn 래퍼, Zod 스키마, 얇은 공유 유틸**만. 도메인 비즈니스 로직 공유는 하지 않는다(`docs/repository-structure.md` §3와 동일한 취지).
- **`docs/`**: 팀 문서의 정본.
- **`docker-compose.yml`**: **저장소 루트**에 두어 로컬에서 DB·브로커·캐시 등을 한 번에 기동하기 쉽게 한다. **배포·로컬 스택은 `docs/architecture.md` §10.1 패턴 B(백엔드·프론트 이미지 분리·Compose)** 를 따르며, 애플리케이션은 **서비스별 Spring 이미지 + 해당 서비스 `web/` Next 이미지**로 올리고 Compose·엣지 프록시로 연결한다(`profile: web` → `identity-web`, `usage-web`).
- **로컬 PostgreSQL:** 단일 인스턴스에서 논리 DB를 나눈다. **identity-service** 는 `POSTGRES_DB`(기본 `app`)만, **usage-service** 는 `usage_db` + 전용 DB 사용자(`USAGE_POSTGRES_*`)만 사용한다. 서비스 간 JDBC 교차 접근은 하지 않는다(`docker-compose.yml`, `docker/postgres/init/`, `.env.example` 참고). 두 DB는 **도메인이 다르므로** 테이블·컬럼 구성도 다르다(사용자/계정 데이터 vs AI 사용량 원장). **identity-service** 의 애플리케이션 설정·DB 연결(`POSTGRES_*`)은 usage 쪽 작업에서 **변경하지 않는다.**

---

## 3. `libs/` 공유 범위 (중요)

다음만 **`libs/`** 에 두는 것을 원칙으로 한다.

- **서비스 간 API 계약**(OpenAPI 스펙, 클라이언트용 인터페이스 등)
- **이벤트 페이로드**(메시지 큐에 실리는 DTO·스키마 등)
- **공통 유틸**(순수 유틸·헬퍼, 팀이 합의한 최소 범위)

다음은 **서비스 모듈 간 Gradle/Maven 의존성으로 공유하지 않는다.**

- **타 서비스의 도메인 모델·비즈니스 규칙**을 그대로 가져다 쓰는 코드
- **DB Entity / Repository** 및 **다른 서비스 DB에 대한 직접 접근 코드**

서비스 데이터가 필요하면 **해당 서비스의 API** 또는 **비동기 이벤트·구독**을 통해 다룬다.

---

## 4. Cursor 규칙과 경로

- Java 코드 스타일·경로별 규칙은 `.cursor/rules/project-common-java.mdc` 및 `proxy-webflux.mdc` / `domain-mvc-jpa.mdc`를 따른다.
- Next(App Router)·BFF는 **`.cursor/rules/project-common-nextjs.mdc`**(`services/*/web/**/*` 등). 라우트·미들웨어 소유 경계는 **`docs/contracts/web-split-boundary.md`**.
- **`services/` 아래 실제 폴더명**이 정해지면, 필요 시 위 `.mdc`의 **`globs`** 를 해당 경로에 맞게 수정한다.

---

## 5. 문서 유지

- 최상위 폴더 구조를 바꾸면 **본 문서와 `README.md`의 문서 링크**를 함께 갱신한다.

---

## 6. 웹(Next.js)·BFF — `services/<svc>/web`

개발 조직은 **역할을 “프론트 전담 / 백엔드 전담”으로 나누지 않고**, **도메인 서비스 단위로 풀스택**을 담당한다. 따라서 **화면·BFF·해당 Spring 앱**은 가능한 한 **같은 `services/<service-name>/` 트리** 안에 둔다.

### 6.1 배치

| 영역 | Gradle(Spring) | Next(App Router + Route Handler BFF) | 계약 문서 |
|------|----------------|--------------------------------------|-----------|
| Identity(인증·조직·세션 BFF 등) | `services/identity-service/` | `services/identity-service/web/` | [`web-identity-bff.md`](contracts/web-identity-bff.md) |
| Usage(대시보드·Usage BFF) | `services/usage-service/` | `services/usage-service/web/` | [`web-gateway-bff.md`](contracts/web-gateway-bff.md) |

- **라우트·BFF 경계 표:** [`web-split-boundary.md`](contracts/web-split-boundary.md)
- **공유 프론트 자산:** 루트 **`pnpm`/`npm` workspace** 와 **`packages/*`**(예: `packages/ui`)로 **토큰·공통 컴포넌트·Zod** 정도만 공유한다. 도메인 로직은 §3와 같이 서비스 경계 밖으로 복사하지 않는다.
- **빌드:** 각 서비스 폴더에서 **`./gradlew bootJar`** 와 **`web/`의 `npm run build`**(standalone 산출물)를 CI·이미지 빌드 단계에서 수행한다(순서·Docker 컨텍스트는 `README.md`, `docker-compose.yml` 주석).

### 6.2 과도기: `apps/web`

- **이행 완료:** 소스는 `services/identity-service/web/`, `services/usage-service/web/` 로 이전했다. **`apps/web/`** 에는 안내용 `README.md`만 남길 수 있다.
- **단일 도메인:** 엣지 역프록시로 경로를 합친다(상세: `docs/architecture.md` §10.2).
- **게이트웨이·Proxy:** [`gateway-proxy.md`](contracts/gateway-proxy.md) §1.1·§3·§9 — **게이트웨이 팀·각 `web` BFF 담당**이 `API_GATEWAY_URL` 등을 합의한다.
- **미들웨어·보호 라우트** 정본: [`web-identity-bff.md`](contracts/web-identity-bff.md) §6.2, [`web-split-boundary.md`](contracts/web-split-boundary.md) §3.
