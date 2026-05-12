package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.entity.TeamMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {
	List<TeamMemberEntity> findAllByUserId(String userId);
	List<TeamMemberEntity> findAllByUserIdIn(List<String> userIds);
	List<TeamMemberEntity> findAllByTeamId(Long teamId);
	@Query("select distinct teamMember.userId from TeamMemberEntity teamMember")
	List<String> findDistinctUserIds();
	boolean existsByUserId(String userId);

	boolean existsByTeamIdAndUserId(Long teamId, String userId);

	Optional<TeamMemberEntity> findByTeamIdAndUserId(Long teamId, String userId);
	long countByTeamId(Long teamId);

	@Query(
			"select m.teamId as teamId, count(m) as memberCount from TeamMemberEntity m "
					+ "where m.teamId in :teamIds group by m.teamId"
	)
	List<TeamMemberCountRow> countGroupedByTeamIdIn(@Param("teamIds") List<Long> teamIds);

	long deleteByUserId(String userId);

	void deleteAllByTeamId(Long teamId);
}
