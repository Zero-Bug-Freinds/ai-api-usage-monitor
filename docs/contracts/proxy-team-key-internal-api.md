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

### Provider values

- `openai`
- `anthropic`
- `google`
- `gemini` (alias for Google-compatible key registrations)

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
- `403`: requester is not an active team member or not allowed to use the team key
- `404`: no active team key registered for provider (or alias set)

## Security

- Endpoint is internal-only.
- Validate gateway/internal service trust token.
- Never log `plainKey`.
