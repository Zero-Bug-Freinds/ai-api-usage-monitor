# Staging verification: usage `user_id` = email (post proxy deploy)

Run after deploying proxy-service with usage-subject / `usageEventUserId()` fix.

## 1. Colab

1. `login()` — JWT decode: `sub` = email, `userId` = numeric PK (unchanged).
2. One AI call: `POST /api/v1/ai/google/...` with `X-Api-Key-Id` (managed personal).

## 2. RDS (usage DB)

```sql
SELECT user_id, provider, occurred_at
FROM usage_recorded_log
ORDER BY occurred_at DESC
LIMIT 3;
```

Expect newest row: `user_id` = login email (lowercase if normalized), not numeric PK.

## 3. Billing

Match `event_id` between usage row and `billing_processed_event`; billing user key should use the same email subject.

## 4. usage-web

Dashboard scoped to login email should show the new usage row.

## 5. Proxy logs (SSM)

```bash
sudo docker logs ai-api-usage-monitor-proxy-service-1 --since 30m 2>&1 \
  | grep -E "Resolved API key|Usage event"
```

`Resolved API key` may still mask lookup target as numeric PK; `Usage event` / DB `user_id` must be email.

## Historical rows

Rows with `user_id` = numeric PK before this deploy are unchanged. Optional repair: `backfill-usage-recorded-log-user-id-email.sql`.
