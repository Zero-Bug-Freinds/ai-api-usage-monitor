package com.zerobugfreinds.team_service.exception;

/**
 * 동일한 (provider, keyHash) 조합으로 여러 팀의 키가 매칭되어 단일 키로 식별할 수 없을 때 발생한다.
 *
 * <p>팀 API 키 테이블의 유일 제약은 (team_id, provider, key_hash) 이므로,
 * 서로 다른 팀이 동일한 외부 API 키를 등록한 경우 역조회 결과가 2건 이상이 될 수 있다.
 * 이때 호출자(예: proxy-service)가 단일 키를 확정할 수 없으므로 409 Conflict 로 응답한다.</p>
 */
public class AmbiguousTeamApiKeyHashException extends RuntimeException {

    public AmbiguousTeamApiKeyHashException(String message) {
        super(message);
    }
}
