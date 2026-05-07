# Proxy Team Key Internal API Guide

This document defines the internal API contract that `proxy-service` expects when
resolving **team-scoped** provider keys.

## Scope

- Caller: `services/proxy-service`
- Callee: `services/team-service` (internal-only endpoint)
- Purpose: resolve team API key for AI provider requests where `teamId` exists
- Policy: **no fallback** to personal key for team requests

## Request

- Method: `GET`
- Path template: `/internal/api-keys/{provider}`
- Query:
  - `teamId` (required): numeric team id
  - `userId` (required): request user id / subject for membership verification
- Header:
  - `Authorization: Bearer <internal-token>` (required)

### Provider values

- `openai`
- `anthropic`
- `google`
- `gemini` (alias for Google-compatible key registrations)

### Provider normalization

- `gemini` is normalized to `google` in `team-service`.
- Unknown provider values must return `400`.

## Response (200)

```json
{
  "plainKey": "provider-secret-key",
  "keyId": "12345"
}
```

- `plainKey`: decrypted provider key
- `keyId`: team API key primary key (string or numeric-string)

## Error semantics

- `400`: invalid parameter/provider
- `403`: missing/invalid internal token, or requester is not an active team member
- `404`: no active team key registered for provider (or alias set)
- `500`: internal server error

## Security

- Endpoint is internal-only.
- Validate gateway/internal service trust token (`PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN`).
- Never log `plainKey`.

## Team Key Selection Rules

For team requests, `team-service` resolves keys with these fixed rules:

1. Lookup by `teamId`.
2. Normalize provider alias (`gemini` -> `google`).
3. Exclude deletion-pending keys (`deletionRequestedAt IS NULL`).
4. Select only the newest active key (`createdAt DESC`, first row).
5. If no active key exists, return `404`.

Personal key fallback is not allowed for team requests.

## Runtime Configuration

- `proxy-service`
  - `PROXY_TEAM_KEY_SERVICE_BASE_URL`
  - `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN`
  - `PROXY_TEAM_KEY_SERVICE_PATH_TEMPLATE=/internal/api-keys/{provider}`
- `team-service`
  - `team.internal.api-token` (resolved from `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN`)
