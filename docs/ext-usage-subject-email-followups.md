# Ext usage subject email — follow-up services (out of proxy/gateway scope)

This task aligns `UsageRecordedEvent.userId` with Identity JWT `sub` (lowercase email) for **personal fingerprint (ext)** calls by resolving opaque `u_<pk>` owners in **proxy-service** via existing Identity internal APIs.

## identity-service (recommended follow-up)

| Item | Detail |
|------|--------|
| Contract | Add `principalSub` to `POST /internal/v1/api-keys/lookup` personal response, or return email in `userId` instead of `u_<pk>`. |
| Implementation | `ExternalApiKeyService.lookupByApiKeyFingerprint` → use `principalSubForUser(entity.getUserId())` (same as MQ). |
| Benefit | Removes proxy’s extra Identity HTTP round-trip per fingerprint cache miss. |
| Docs | Update `docs/contracts/proxy-api-key-reverse-lookup-internal-api.md`. |

## team-service (recommended follow-up)

| Item | Detail |
|------|--------|
| Gap | `TeamApiKeyFingerprintLookupService` returns `userId=null` though `TeamApiKeyEntity.createdByUserId` exists. |
| Ask | Include registrant `createdByUserId` (or email via Identity) in TEAM fingerprint lookup response. |
| Until then | Team ext usage `user_id` is set only when clients send **`X-Ext-User-Id`** (email); gateway normalizes email casing on `X-User-Id` / `X-Platform-User-Id`. |

## Data note

Historical `usage_recorded_log.user_id = u_*` rows are not backfilled by this change. New ext personal events use email after deploy.
