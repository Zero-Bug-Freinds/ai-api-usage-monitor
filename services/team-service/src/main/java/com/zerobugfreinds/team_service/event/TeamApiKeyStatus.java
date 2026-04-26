package com.zerobugfreinds.team_service.event;

/**
 * Usage/Billing 동기화를 위한 팀 API Key 라이프사이클 상태.
 */
public enum TeamApiKeyStatus {
    ACTIVE,
    DELETION_REQUESTED,
    DELETED
}
