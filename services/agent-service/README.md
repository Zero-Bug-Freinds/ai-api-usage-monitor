# agent-service (MVP)

`agent-service`는 정책 추천 에이전트 API를 제공하는 신규 서비스입니다.

## 목적

- 사용자/팀의 월 예산 사용률을 계산해 정책 결정을 추천한다.
- 초기 MVP는 규칙 기반으로 `ALLOW`, `WARN`, `BLOCK`을 반환한다.

## API

- `POST /api/v1/agents/policy-recommendations`

요청:

```json
{
  "userId": "user@test.com",
  "teamId": "team-1",
  "provider": "OPENAI",
  "model": "gpt-4o",
  "monthlyBudgetUsd": 100,
  "currentSpendUsd": 85
}
```

응답:

```json
{
  "recommendationLevel": "WARN",
  "recommendedAction": "저비용 모델 우선 사용 권장 및 사용자 경고",
  "utilizationRatePercent": 85.00,
  "reasons": [
    "예산 사용률: 85.00%",
    "월 예산 대비 사용량이 80% 이상입니다.",
    "요청 모델: gpt-4o"
  ]
}
```

## 룰

- `< 80%` -> `ALLOW`
- `>= 80% and < 100%` -> `WARN`
- `>= 100%` -> `BLOCK`
