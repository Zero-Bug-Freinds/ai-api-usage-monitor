# Staging verification: billing `usage.recorded` JSR-310 fix (post deploy)

Run after deploying billing-service with `JavaTimeModule` + runtime `jackson-datatype-jsr310`.

## 1. Roll billing-service

Rebuild and recreate the billing-service container after RabbitMQ is healthy.

## 2. Colab

One AI call (`POST /api/v1/ai/google/...`) with JWT + managed personal key.

## 3. RabbitMQ queue

```bash
docker exec aio-host-rabbitmq rabbitmq-diagnostics -q ping
docker exec aio-host-rabbitmq rabbitmqctl list_queues name messages consumers | grep billing-service.queue
```

Expect: **messages=0**, **consumers≥1**.

## 4. Billing logs

```bash
docker logs --since 3m ai-api-usage-monitor-billing-service-1 2>&1 \
  | grep -iE 'InvalidDefinition|UsageRecorded|billing event handling|ERROR'
```

Expect: no `InvalidDefinitionException`, no `billing event handling failed`.

## 5. Optional DB

Confirm a new row in `billing_processed_event` for the Colab `event_id`.

## Stuck message before fix

If one message remained in `billing-service.queue` before deploy, billing restart after fix should reconsume it. Otherwise purge the queue and repeat Colab once.
