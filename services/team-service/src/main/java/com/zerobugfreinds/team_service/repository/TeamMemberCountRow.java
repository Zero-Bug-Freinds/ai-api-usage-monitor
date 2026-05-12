package com.zerobugfreinds.team_service.repository;

/**
 * JPQL 집계 투영: {@link TeamMemberRepository#countGroupedByTeamIdIn} 결과 행.
 */
public interface TeamMemberCountRow {

	Long getTeamId();

	Long getMemberCount();
}
