package com.zerobugfreinds.team_service.event;

/**
 * notification-service 등과 공유하는 팀 도메인 이벤트 식별자.
 */
public final class TeamEventTypes {

	public static final String TEAM_CREATED = "TEAM_CREATED";
	public static final String TEAM_INVITE_CREATED = "TEAM_INVITE_CREATED";
	public static final String TEAM_MEMBER_JOINED = "TEAM_MEMBER_JOINED";
	public static final String TEAM_INVITATION_ACCEPTED = "TEAM_INVITATION_ACCEPTED";
	public static final String TEAM_INVITATION_REJECTED = "TEAM_INVITATION_REJECTED";
	public static final String TEAM_MEMBER_REMOVED = "TEAM_MEMBER_REMOVED";
	public static final String TEAM_DELETED = "TEAM_DELETED";
	public static final String TEAM_API_KEY_REGISTERED = "TEAM_API_KEY_REGISTERED";
	public static final String TEAM_API_KEY_UPDATED = "TEAM_API_KEY_UPDATED";
	public static final String TEAM_API_KEY_DELETED = "TEAM_API_KEY_DELETED";
	public static final String TEAM_API_KEY_DELETION_SCHEDULED = "TEAM_API_KEY_DELETION_SCHEDULED";
	public static final String TEAM_API_KEY_DELETION_CANCELLED = "TEAM_API_KEY_DELETION_CANCELLED";
	public static final String TEAM_API_KEY_STATUS_CHANGED = "TEAM_API_KEY_STATUS_CHANGED";

	private TeamEventTypes() {
	}
}
