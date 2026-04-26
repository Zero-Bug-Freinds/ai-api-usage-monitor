package com.zerobugfreinds.team_service.security;

/**
 * 요청 스레드에서 게이트웨이 사용자/팀 헤더 컨텍스트를 보관한다.
 */
public final class TeamContextHolder {

	private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
	private static final ThreadLocal<Long> TEAM_ID_HOLDER = new ThreadLocal<>();

	private TeamContextHolder() {
	}

	public static void setUserId(String userId) {
		USER_ID_HOLDER.set(userId);
	}

	public static String getUserId() {
		return USER_ID_HOLDER.get();
	}

	public static void setTeamId(Long teamId) {
		TEAM_ID_HOLDER.set(teamId);
	}

	public static Long getTeamId() {
		return TEAM_ID_HOLDER.get();
	}

	public static void clear() {
		USER_ID_HOLDER.remove();
		TEAM_ID_HOLDER.remove();
	}
}
