# usage-service `api_key_metadata` — 스코프·복합 키

## 배경 (유실 원인)

과거 설계는 `api_key_metadata`의 **단일 PK `key_id`**였고, identity의 외부 API key PK와 team-service의 `team_api_key` PK가 **동일한 숫자 문자열**이 될 수 있었다. `ApiKeyMetadataSyncService`가 identity 이벤트·팀 이벤트·사용량 이벤트 모두에서 `findById(keyId)`로 **같은 행**을 갱신해, 나중 이벤트가 이전 스코프(개인/팀) 메타를 덮어쓰는 문제가 발생할 수 있다.

## 현재 모델

- **복합 PK**: `(key_id, user_id, key_scope)` — `key_scope`는 `PERSONAL` | `TEAM`.
- **개인 키**: `key_scope = PERSONAL`, `team_id`는 null. identity `keyId` + 소유 `userId`로 유일.
- **팀 키**: `key_scope = TEAM`, `team_id` 필수. 동일 팀 API key id에 대해 **팀원 수만큼** 행이 존재하며, `user_id`는 해당 행을 대시보드·필터에서 쓰는 **멤버(조회 주체)**이다.

팀 키 필터 UI는 `DISTINCT ON (key_id)` 등으로 **논리 키당 한 줄**만 노출한다.

## `usage_recorded_log` 조인

로그는 `api_key_id`(개인)·`team_api_key_id`(팀)를 둘 수 있다. `UsageRecordedLogEntity`는 `personalKeyMetadata` / `teamKeyMetadata` 두 연관을 두고, `getApiKeyMetadata()`는 팀 로그 우선으로 `teamKeyMetadata`를 반환한다.

## 팀 멤버 전파

팀 API key MQ 이벤트 처리 시 `TeamServiceClient`로 멤버 목록을 조회한 뒤 멤버별로 upsert한다. 멤버 API 실패 시에는 **액터 한 명** 행만 유지하는 폴백으로 동기화를 막지 않는다.
