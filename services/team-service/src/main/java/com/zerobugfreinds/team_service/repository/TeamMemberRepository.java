package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.entity.TeamMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {
	List<TeamMemberEntity> findAllByUserId(String userId);

	boolean existsByTeamIdAndUserId(Long teamId, String userId);

	Optional<TeamMemberEntity> findByTeamIdAndUserId(Long teamId, String userId);
}
