 # Notification Service Gateway Integration Guide (Task 38-2)

## 1) 목적과 범위

- 본 문서는 `notification-service`를 API Gateway 단일 진입점 구조에 정식 편입하기 위한 분석/전환 가이드다.
- 이번 태스크 범위는 **분석 및 제안**이며, 서비스/게이트웨이 로직 변경은 포함하지 않는다.
- 기준 코드:
  - `services/api-gateway-service`
  - `services/notification-service`

## 2) 현재 Gateway 인증/헤더 전파 구조

### 2.1 라우팅

- `api-gateway-service`는 `/api/notification/**`를 `notification-service`로 라우팅한다.
- 현재 route filter는 `Authorization` 헤더를 제거하고 경로를 `/api/${segment}`로 rewrite한다.
  - 예: `/api/notification/in-app-notifications` -> `/api/in-app-notifications`

### 2.2 Security 설정

- `SecurityConfiguration` 기준:
  - `gateway.dev-mode=true`: `/api/notification/**`는 `permitAll`
  - `gateway.dev-mode=false`: `/api/notification/**`도 현재 `permitAll` (인증 유예 상태)

### 2.3 글로벌 필터(ProxyTrustHeadersWebFilter)

- 게이트웨이 표준 전파 헤더(JWT 검증 후 `forwardWithJwt` 적용 경로 기준):
  - `X-User-Id` ← JWT 클레임 **`sub`** (이메일; notification/team/billing/usage 등 서비스 경로에서 사용).
  - `X-Platform-User-Id` ← JWT 클레임 **`userId`** (플랫폼 내부 사용자 ID).
  - JWT에 **`sub`가 없거나 빈 문자열이면** 요청은 조용히 통과하지 않고 **401**으로 처리한다.
  - `X-Org-Id` (`org_id`)
  - `X-Team-Id` (`team_id`)
  - `X-Scope-Type` (`scope_type`, 없으면 `team_id` 기반 추론)
  - `X-Correlation-Id` (존재 시 전달)
  - `X-Gateway-Auth` (공유 시크릿)
- 단, 현재는 `isNotificationPath("/api/notification/**")`에서 필터가 즉시 bypass되어 notification 경로에는 위 헤더 주입이 적용되지 않는다.

## 3) notification-service 현재 상태 진단

## 3.1 백엔드(Nest) 인증 방식

- `notification-service`는 JWT를 직접 검증하지 않는다.
- `InAppAuthGuard`는 아래 방식으로 요청을 허용한다.
  1. `X-User-Id`가 있으면 인증 성공
  2. 없더라도 `X-Notification-Internal-Secret`이 유효하면 내부 호출로 허용
  3. 둘 다 없으면 `401 Missing X-User-Id`
- 즉, 이미 "헤더 기반 신뢰 모델"을 부분적으로 사용 중이다.

## 3.2 사용자 식별 사용 지점

- `InAppNotificationsController`:
  - `req.userId`를 list/read/read-all의 기준 사용자로 사용
  - `test-send`는 `req.auth.isInternal` 여부로 타 사용자 발송 허용 여부를 결정
- `InAppNotificationsService`:
  - DB 쿼리는 `userId` 기준으로 수행
- 요약: 핵심 비즈니스는 `userId` 문자열만 있으면 동작 가능하며, JWT 파싱 의존은 없다.

## 3.3 프런트 BFF 연계 상태

- `services/notification-service/web/src/app/api/notification/[[...path]]/route.ts`는 아직 Gateway 경유가 아닌 **notification-service 직접 호출** 구조다.
- 이 BFF는 `access_token` JWT 페이로드의 **`sub`(이메일)** 을 읽어 `direct` 모드에서 `X-User-Id`로 전달한다.
- Gateway 경로에서도 `X-User-Id`는 **JWT `sub`(이메일)** 이고, 플랫폼 내부 ID는 `X-Platform-User-Id`로 구분된다.

## 4) 전환 전략 (제안만, 미적용)

### 4.1 인증 방식 전환

목표: notification-service는 Gateway가 검증/주입한 공통 헤더를 표준 입력으로 사용.

제안:
- 1단계(호환 단계)
  - `InAppAuthGuard`를 "Gateway 헤더 우선 + 기존 내부 시크릿 fallback" 구조로 명시화
  - 우선 신뢰 헤더: `X-User-Id`, 보조 컨텍스트: `X-Team-Id`, `X-Scope-Type`, `X-Platform-User-Id`
- 2단계(정식 단계)
  - 외부 클라이언트가 직접 `X-User-Id`를 넣어 접근하지 못하도록 네트워크/게이트웨이 경계에서만 허용
  - 필요 시 `X-Gateway-Auth` 검증을 도입해 게이트웨이 경유 요청만 수락

### 4.2 사용자/팀 식별 로직 일원화

목표: 컨트롤러마다 헤더 파싱하지 않고 공통 컨텍스트 객체로 주입.

제안:
- `AuthContext` 형태로 정규화:
  - `userId` (`X-User-Id`)
  - `platformUserId` (`X-Platform-User-Id`, optional — Gateway JWT 경로에서는 현재 구현상 **`userId`와 동일 값**이 내려온다)
  - `teamId` (`X-Team-Id`, optional)
  - `scopeType` (`X-Scope-Type`, USER|TEAM)
  - `isInternal` (internal secret 검증 결과)
- 적용 방식(택1):
  - Nest `Guard + Request 확장` 유지 (현재 구조 최소 변경)
  - 또는 `Custom Decorator`/`Param decorator`로 `@AuthContext()` 주입
- 권장: 현재 코드 일관성을 위해 Guard 기반을 유지하고, 타입(`in-app-notifications.types.ts`)만 확장

### 4.3 Gateway 설정 동기화(Whitelist 해제 시점)

#### 해제 전 선행 조건

1. notification-service가 `X-User-Id` 누락/오염 케이스를 명확히 401 처리
2. notification-web BFF 또는 클라이언트가 Gateway 경유 호출로 전환 완료
3. 통합 테스트에서 `/api/notification/**` 경로의 인증 성공/실패 시나리오 확보

#### 해제 절차

1. `SecurityConfiguration`에서 `/api/notification/**`를 `authenticated()`로 변경(`dev-mode=false`)
2. `ProxyTrustHeadersWebFilter`에서 notification bypass 제거:
   - `isNotificationPath` 우회 삭제
   - notification 경로에도 표준 헤더 주입 활성화
3. 관측:
   - 게이트웨이 로그에서 notification 요청도 `[Gateway] Forwarding ...` 패턴 확인
   - 401/403 비율 및 알림 API 성공률 모니터링

## 5) notification-service 수정 필요 지점 목록(후속 승인 시 대상)

- `src/in-app-notifications/auth/in-app-auth.guard.ts`
  - Gateway 헤더 우선 신뢰 정책, 컨텍스트 확장, (선택) `X-Gateway-Auth` 검증
- `src/in-app-notifications/in-app-notifications.types.ts`
  - `teamId`, `scopeType`, `platformUserId` 필드 확장
- `src/in-app-notifications/in-app-notifications.controller.ts`
  - 공통 인증 컨텍스트 사용으로 반복 체크 정리
- (선택) `src/common/auth/*` 신규 모듈
  - 서비스 전역에서 재사용 가능한 인증 컨텍스트 추출 유틸/데코레이터
- 테스트 코드(신규/기존)
  - 헤더 조합별 인증/인가 케이스 검증

참고(백엔드 외 연계):
- `services/notification-service/web/src/app/api/notification/[[...path]]/route.ts`
  - Gateway 완전 전환 시 업스트림을 Gateway 기준으로 재정렬할지 검토 필요

## 6) identity/team 서비스와 충돌 가능성 점검 결과

- 현재 분석 기준으로 `identity-service`, `team-service` 내부 로직과 직접 충돌하는 지점은 확인되지 않았다.
- 이유:
  - 이번 전환 대상은 notification 경로의 게이트웨이 인증 정책 및 notification 내부 헤더 수용 방식
  - identity/team의 기존 엔드포인트/필터 계약을 변경할 필요가 없음
- 단, 플랫폼 JWT 클레임 스키마(`sub`, `userId`, `team_id`, `scope_type`) 변경이 발생하면 세 서비스 공통 영향이 있으므로 별도 공지/버전 관리가 필요하다. 특히 **`sub`는 이메일 등 로그인 식별자로 남을 수 있으나, 다운스트림 신뢰 헤더 `X-User-Id`는 `userId` 클레임 기준**으로 맞추는 것이 identity/team과의 계약과 일치한다.

## 7) 권장 적용 순서(승인 후)

1. notification-service: 헤더 컨텍스트 확장 + 테스트
2. notification-web BFF/호출 경로 점검(게이트웨이 경유 일관화)
3. api-gateway: notification whitelist 해제 + filter bypass 제거
4. 스테이징 검증 후 운영 반영

