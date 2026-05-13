package com.zerobugfreinds.identity_service.dto;

/**
 * Proxy 등 내부 호출자가 클라이언트가 보낸 외부 API 키의 해시를 기준으로
 * 어떤 사용자/키에 매핑되는지를 역추적할 때 반환하는 응답.
 *
 * <p>평문 키는 절대 포함하지 않는다. 상태값으로 키의 라이프사이클 단계를
 * 명확히 노출하여 호출자가 활성/삭제 예정 등을 직접 판단할 수 있게 한다.</p>
 *
 * @param keyId   외부 API 키 행의 PK
 * @param ownerId 키 소유자(사용자) ID
 * @param status  키 상태값 (ACTIVE / DELETION_REQUESTED / DELETED)
 * @param alias   사용자가 설정한 별칭
 * @param scope   고정값 "USER" — 팀 키와 구분하기 위함
 */
public record InternalApiKeyLookupResponse(
		String keyId,
		Long ownerId,
		String status,
		String alias,
		String scope
) {
	public static final String SCOPE_USER = "USER";
}
