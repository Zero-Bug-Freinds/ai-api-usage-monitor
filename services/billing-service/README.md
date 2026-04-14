# billing-service

## Identity 예산(월 예산 USD) 연동 설정

Billing UI/집계 응답에 표시되는 **월 예산(USD)** 은 `billing-service`가 Identity로부터 HTTP로 선택적으로 조회합니다.

- Identity 연동이 **비활성화** 되어 있거나, Identity가 **404** 를 반환하거나, 설정이 비어있으면 예산은 **없음(빈 값)** 으로 처리됩니다.
- 예산이 없더라도 Billing 기능 자체는 동작하며, UI에서는 “예산 없음”으로 표시될 수 있습니다.

### 설정 키 (환경변수)

`services/billing-service/src/main/resources/application.yml` 에서 아래 환경변수를 읽습니다.

- **`BILLING_IDENTITY_ENABLED`**: `true`로 설정 시 예산 조회 활성화 (기본 `false`)
- **`BILLING_IDENTITY_BASE_URL`**: Identity base URL (예: `http://localhost:8081`)
- **`BILLING_IDENTITY_BUDGET_PATH`**: 예산 조회 path 템플릿
  - `{userId}` 토큰을 포함할 수 있습니다. 포함하면 해당 토큰이 실제 userId로 치환됩니다.
  - `{userId}`가 없다면, 그대로 path로 사용됩니다.
  - 선행 `/` 유무는 상관없습니다(자동 정규화).

예시:

```text
BILLING_IDENTITY_ENABLED=true
BILLING_IDENTITY_BASE_URL=http://localhost:8081
BILLING_IDENTITY_BUDGET_PATH=/api/identity/v1/users/{userId}/budget
```

### Identity 응답 형식(중요)

`billing-service`는 Identity 응답 body를 JSON으로 파싱하고, 최상위 필드 `monthlyBudgetUsd` 만 읽습니다.

예시:

```json
{
  "monthlyBudgetUsd": 100.00
}
```

### 주의사항 / 트러블슈팅

- **인증 헤더 미전송**
  - 현재 구현(`IdentityBudgetClient`)은 예산 조회 요청에 별도 인증 헤더/토큰을 붙이지 않습니다.
  - Identity가 인증을 요구하는 엔드포인트라면, 이 연동은 예산을 “없음”으로 처리하게 됩니다.

- **조용히 실패할 수 있음**
  - 404는 정상적으로 “예산 없음”으로 취급합니다.
  - 그 외 상태코드/예외도 예산을 “없음”으로 처리하며, 로그는 `debug` 레벨로만 남습니다.
  - 예산이 계속 안 뜬다면, 우선 `BILLING_IDENTITY_*` 값이 실제로 주입됐는지 확인한 뒤,
    Identity 쪽 예산 엔드포인트가 공개 접근/응답 형식이 맞는지 점검하세요.

