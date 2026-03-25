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
│   ├── <service-a>/       # 예: 프록시·식별·과금 등 (실제 이름은 팀 합의)
│   ├── <service-b>/
│   └── ...
├── libs/                  # 공유 라이브러리 모듈(범위는 §3 참고)
│   ├── <shared-lib-1>/    # 예: API 계약, 이벤트 스키마, 공통 유틸 등
│   └── ...
├── docs/                  # 설계·컨벤션·아키텍처 문서 (본 문서 포함)
├── scripts/               # (선택) 빌드·로컬 실행 보조 스크립트
├── docker-compose.yml     # 루트: 로컬 의존성(PostgreSQL, RabbitMQ, Redis 등)
├── README.md
└── (루트 빌드 설정)       # 예: settings.gradle + build.gradle, 또는 루트 pom.xml 등
```

- **`services/`**: 배포·실행 단위가 되는 **Spring Boot 앱**(또는 동등한 실행 형태)을 둔다. **폴더명 = 예시가 아닌 실제 서비스 이름**으로 정한다.
- **`libs/`**: 아래 §3의 **허용 범위만** 공유한다.
- **`docs/`**: 팀 문서의 정본.
- **`docker-compose.yml`**: **저장소 루트**에 두어 로컬에서 DB·브로커·캐시 등을 한 번에 기동하기 쉽게 한다. 애플리케이션 이미지를 함께 올릴지는 팀 선택.
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
- **`services/` 아래 실제 폴더명**이 정해지면, 필요 시 위 `.mdc`의 **`globs`** 를 해당 경로에 맞게 수정한다.

---

## 5. 문서 유지

- 최상위 폴더 구조를 바꾸면 **본 문서와 `README.md`의 문서 링크**를 함께 갱신한다.
