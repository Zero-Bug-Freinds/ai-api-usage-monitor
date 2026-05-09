package com.zerobugfreinds.team_service.dto;

/**
 * Proxy 등 내부 호출자가 클라이언트에게 받은 팀 API 키의 해시값으로
 * 어느 팀/등록 멤버/키에 매핑되는지 식별할 때 반환하는 응답.
 *
 * <p>평문 키는 절대 포함하지 않는다. 상태값으로 키의 라이프사이클 단계를
 * 명확히 노출하여 호출자가 활성/삭제 예정 등을 직접 판단할 수 있게 한다.</p>
 *
 * @param keyId        팀 API 키 행의 PK
 * @param teamId       소속 팀 ID
 * @param ownerUserId  키를 등록한 멤버의 사용자 ID (legacy 행은 null 일 수 있음)
 * @param status       키 상태값 (ACTIVE / DELETION_REQUESTED / DELETED)
 * @param alias        팀 키 별칭
 * @param scope        고정값 "TEAM" — 개인 키와 구분하기 위함
 */
public record InternalTeamApiKeyLookupResponse(
        String keyId,
        Long teamId,
        String ownerUserId,
        String status,
        String alias,
        String scope
) {
    public static final String SCOPE_TEAM = "TEAM";
}
