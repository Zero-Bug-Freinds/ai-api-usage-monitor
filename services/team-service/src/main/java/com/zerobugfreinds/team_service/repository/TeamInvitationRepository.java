package com.zerobugfreinds.team_service.repository;

import com.zerobugfreinds.team_service.domain.TeamInvitationStatus;
import com.zerobugfreinds.team_service.entity.TeamInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitationEntity, Long> {

	boolean existsByTeamIdAndInviteeIdAndStatus(Long teamId, String inviteeId, TeamInvitationStatus status);
	boolean existsByInviteeIdOrInviterId(String inviteeId, String inviterId);

	List<TeamInvitationEntity> findAllByInviteeIdAndStatusOrderByCreatedAtDesc(String inviteeId, TeamInvitationStatus status);
	List<TeamInvitationEntity> findAllByInviteeIdInAndStatusOrderByCreatedAtDesc(List<String> inviteeIds, TeamInvitationStatus status);
	List<TeamInvitationEntity> findAllByInviteeIdAndStatusInOrderByCreatedAtDesc(String inviteeId, List<TeamInvitationStatus> statuses);
	List<TeamInvitationEntity> findAllByInviteeIdInAndStatusInOrderByCreatedAtDesc(List<String> inviteeIds, List<TeamInvitationStatus> statuses);
	List<TeamInvitationEntity> findAllByStatusAndCreatedAtBefore(TeamInvitationStatus status, Instant createdAt);

	Optional<TeamInvitationEntity> findByIdAndInviteeId(Long id, String inviteeId);

	long deleteByInviteeIdOrInviterId(String inviteeId, String inviterId);

	void deleteAllByTeamId(Long teamId);
	long deleteByStatusInAndRespondedAtBefore(List<TeamInvitationStatus> statuses, Instant respondedAt);
}
