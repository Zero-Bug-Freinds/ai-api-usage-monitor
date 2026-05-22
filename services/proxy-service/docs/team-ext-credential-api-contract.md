# Team ext fingerprint — trusted credential API (team-service follow-up)

Proxy-service consumes this API for **external (fingerprint) team API key** relay after `POST /internal/v1/api-keys/lookup` identifies `ownerType=TEAM`.

Implementation: [`TeamKeyCredentialClient`](../src/main/java/com/eevee/proxyservice/key/TeamKeyCredentialClient.java).

## Contract (v1)

| Item | Value |
|------|--------|
| Method / Path | `GET /internal/v1/team-api-keys/{keyId}/credential` |
| Query | `teamId` (required, numeric team PK), `provider` (`openai` / `anthropic` / `google` — same segments as existing team internal key API) |
| Auth | `Authorization: Bearer <token>` — same as `team.internal.api-token` / `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN` |
| 200 body | `{ "plainKey": "<decrypted>", "keyId": "<string>" }` — compatible with [`InternalTeamApiKeyResponse`](../../team-service/src/main/java/com/zerobugfreinds/team_service/dto/InternalTeamApiKeyResponse.java) |
| 403 | Missing/invalid Bearer |
| 404 | No active key for `(teamId, keyId, provider)` or deletion-requested row |

**Do not** return `plainKey` from fingerprint POST lookup. **Do not** reuse membership-checked `GET /internal/api-keys/{provider}?userId=&teamId=` for ext relay.

## Proxy configuration

| Property | Default |
|----------|---------|
| `proxy.team-key-service.credential-path-template` | `/internal/v1/team-api-keys/{keyId}/credential` |
| Env override | `PROXY_TEAM_KEY_CREDENTIAL_PATH_TEMPLATE` |
| Token | `PROXY_TEAM_KEY_SERVICE_INTERNAL_TOKEN` (must match team-service) |

## team-service implementer checklist

- [x] Controller under `/internal/v1/team-api-keys/{keyId}/credential` (no clash with membership `GET /internal/api-keys/{provider}`)
- [x] Validate Bearer against `team.internal.api-token`
- [x] Resolve `TeamApiKeyEntity` by `keyId` + `teamId` + `provider`; reject non-active keys as 404
- [x] Decrypt with existing `EncryptionUtil.decryptAes256Gcm` (same as `TeamInternalApiKeyResolveService`)
- [x] **No** team membership check on this endpoint (trusted proxy-only caller)
- [x] Never log `plainKey` or full fingerprint
- [x] Registrant email in fingerprint POST lookup `userId` — see [`docs/ext-usage-subject-email-followups.md`](../../../docs/ext-usage-subject-email-followups.md)

## Behaviour before / after team API exists

| Phase | ext team AI relay | usage events |
|-------|-------------------|--------------|
| Proxy merged, team API absent | Credential HTTP fails → 502 / connection error | Not published |
| Team API deployed | Relay succeeds | `team_id`, `api_key_source=team`, `user_id` from `X-Ext-User-Id` or lookup `userId` |

Personal and internal proxy paths are unchanged by this feature.
