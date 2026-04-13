package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.domain.TeamInvitationStatus;
import com.zerobugfreinds.team_service.entity.TeamInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitationEntity, Long> {

	boolean existsByTeamIdAndInviteeIdAndStatus(Long teamId, String inviteeId, TeamInvitationStatus status);

	List<TeamInvitationEntity> findAllByInviteeIdAndStatusOrderByCreatedAtDesc(String inviteeId, TeamInvitationStatus status);

	Optional<TeamInvitationEntity> findByIdAndInviteeId(Long id, String inviteeId);
}
