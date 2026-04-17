package com.zerobugfreinds.team_service.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * 팀 도메인 AMQP 페이로드 공통 필드 + 유형별 확장 필드(하위 호환용 레거시 필드 포함).
 */
public sealed interface TeamDomainOutboundEvent permits
		TeamDomainOutboundEvent.TeamCreatedEvent,
		TeamDomainOutboundEvent.TeamInviteCreatedEvent,
		TeamDomainOutboundEvent.TeamMemberJoinedEvent,
		TeamDomainOutboundEvent.TeamInvitationAcceptedEvent,
		TeamDomainOutboundEvent.TeamInvitationRejectedEvent,
		TeamDomainOutboundEvent.TeamMemberRemovedEvent,
		TeamDomainOutboundEvent.TeamDeletedEvent,
		TeamDomainOutboundEvent.TeamApiKeyRegisteredEvent,
		TeamDomainOutboundEvent.TeamApiKeyUpdatedEvent,
		TeamDomainOutboundEvent.TeamApiKeyDeletedEvent,
		TeamDomainOutboundEvent.TeamApiKeyDeletionScheduledEvent,
		TeamDomainOutboundEvent.TeamApiKeyDeletionCancelledEvent {

	String eventType();

	String teamId();

	@JsonInclude(JsonInclude.Include.NON_NULL)
	String teamName();

	String actorUserId();

	Instant occurredAt();

	List<String> recipientUserIds();

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamCreatedEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds
	) implements TeamDomainOutboundEvent {

		public static TeamCreatedEvent of(String teamId, String teamName, String actorUserId, Instant occurredAt) {
			return new TeamCreatedEvent(
					TeamEventTypes.TEAM_CREATED,
					teamId,
					teamName,
					actorUserId,
					occurredAt,
					List.of(actorUserId)
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamInviteCreatedEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			String invitationId,
			String receiverId,
			String inviterId,
			Instant createdAt
	) implements TeamDomainOutboundEvent {

		public static TeamInviteCreatedEvent of(
				String invitationId,
				String inviteeUserId,
				String inviterUserId,
				Long teamId,
				String teamName,
				Instant invitationCreatedAt
		) {
			return new TeamInviteCreatedEvent(
					TeamEventTypes.TEAM_INVITE_CREATED,
					String.valueOf(teamId),
					teamName,
					inviterUserId,
					invitationCreatedAt,
					List.of(inviteeUserId),
					invitationId,
					inviteeUserId,
					inviterUserId,
					invitationCreatedAt
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamMemberJoinedEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			String receiverId,
			String inviterId,
			Instant createdAt
	) implements TeamDomainOutboundEvent {

		public static TeamMemberJoinedEvent of(
				String joinedUserId,
				String inviterUserId,
				Long teamId,
				String teamName,
				Instant joinedAt
		) {
			return new TeamMemberJoinedEvent(
					TeamEventTypes.TEAM_MEMBER_JOINED,
					String.valueOf(teamId),
					teamName,
					joinedUserId,
					joinedAt,
					List.of(inviterUserId, joinedUserId),
					joinedUserId,
					inviterUserId,
					joinedAt
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamInvitationAcceptedEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			String invitationId
	) implements TeamDomainOutboundEvent {

		public static TeamInvitationAcceptedEvent of(
				String invitationId,
				String accepterUserId,
				String inviterUserId,
				Long teamId,
				String teamName,
				Instant respondedAt
		) {
			return new TeamInvitationAcceptedEvent(
					TeamEventTypes.TEAM_INVITATION_ACCEPTED,
					String.valueOf(teamId),
					teamName,
					accepterUserId,
					respondedAt,
					List.of(inviterUserId),
					invitationId
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamInvitationRejectedEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			String invitationId
	) implements TeamDomainOutboundEvent {

		public static TeamInvitationRejectedEvent of(
				String invitationId,
				String rejectorUserId,
				String inviterUserId,
				Long teamId,
				String teamName,
				Instant respondedAt
		) {
			return new TeamInvitationRejectedEvent(
					TeamEventTypes.TEAM_INVITATION_REJECTED,
					String.valueOf(teamId),
					teamName,
					rejectorUserId,
					respondedAt,
					List.of(inviterUserId),
					invitationId
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamMemberRemovedEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			String removedUserId,
			String receiverId
	) implements TeamDomainOutboundEvent {

		public static TeamMemberRemovedEvent of(
				String actorOwnerUserId,
				String removedUserId,
				Long teamId,
				String teamName,
				Instant occurredAt
		) {
			return new TeamMemberRemovedEvent(
					TeamEventTypes.TEAM_MEMBER_REMOVED,
					String.valueOf(teamId),
					teamName,
					actorOwnerUserId,
					occurredAt,
					List.of(removedUserId),
					removedUserId,
					removedUserId
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamDeletedEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			List<String> memberUserIdsSnapshot
	) implements TeamDomainOutboundEvent {

		public static TeamDeletedEvent of(
				String actorOwnerUserId,
				Long teamId,
				String teamName,
				List<String> memberUserIdsSnapshot,
				Instant occurredAt
		) {
			return new TeamDeletedEvent(
					TeamEventTypes.TEAM_DELETED,
					String.valueOf(teamId),
					teamName,
					actorOwnerUserId,
					occurredAt,
					List.copyOf(memberUserIdsSnapshot),
					List.copyOf(memberUserIdsSnapshot)
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamApiKeyRegisteredEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			long apiKeyId,
			String provider,
			String alias
	) implements TeamDomainOutboundEvent {

		public static TeamApiKeyRegisteredEvent of(
				String actorUserId,
				Long teamId,
				String teamName,
				List<String> recipientUserIds,
				long apiKeyId,
				String provider,
				String alias,
				Instant occurredAt
		) {
			return new TeamApiKeyRegisteredEvent(
					TeamEventTypes.TEAM_API_KEY_REGISTERED,
					String.valueOf(teamId),
					teamName,
					actorUserId,
					occurredAt,
					List.copyOf(recipientUserIds),
					apiKeyId,
					provider,
					alias
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamApiKeyUpdatedEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			long apiKeyId,
			String provider,
			String alias
	) implements TeamDomainOutboundEvent {

		public static TeamApiKeyUpdatedEvent of(
				String actorUserId,
				Long teamId,
				String teamName,
				List<String> recipientUserIds,
				long apiKeyId,
				String provider,
				String alias,
				Instant occurredAt
		) {
			return new TeamApiKeyUpdatedEvent(
					TeamEventTypes.TEAM_API_KEY_UPDATED,
					String.valueOf(teamId),
					teamName,
					actorUserId,
					occurredAt,
					List.copyOf(recipientUserIds),
					apiKeyId,
					provider,
					alias
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamApiKeyDeletedEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			long apiKeyId,
			String provider,
			String alias
	) implements TeamDomainOutboundEvent {

		public static TeamApiKeyDeletedEvent of(
				String actorUserId,
				Long teamId,
				String teamName,
				List<String> recipientUserIds,
				long apiKeyId,
				String provider,
				String alias,
				Instant occurredAt
		) {
			return new TeamApiKeyDeletedEvent(
					TeamEventTypes.TEAM_API_KEY_DELETED,
					String.valueOf(teamId),
					teamName,
					actorUserId,
					occurredAt,
					List.copyOf(recipientUserIds),
					apiKeyId,
					provider,
					alias
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamApiKeyDeletionScheduledEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			long apiKeyId,
			String provider,
			String alias,
			int deletionGraceDays,
			Instant permanentDeletionAt
	) implements TeamDomainOutboundEvent {

		public static TeamApiKeyDeletionScheduledEvent of(
				String actorUserId,
				Long teamId,
				String teamName,
				List<String> recipientUserIds,
				long apiKeyId,
				String provider,
				String alias,
				int deletionGraceDays,
				Instant permanentDeletionAt,
				Instant occurredAt
		) {
			return new TeamApiKeyDeletionScheduledEvent(
					TeamEventTypes.TEAM_API_KEY_DELETION_SCHEDULED,
					String.valueOf(teamId),
					teamName,
					actorUserId,
					occurredAt,
					List.copyOf(recipientUserIds),
					apiKeyId,
					provider,
					alias,
					deletionGraceDays,
					permanentDeletionAt
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record TeamApiKeyDeletionCancelledEvent(
			String eventType,
			String teamId,
			String teamName,
			String actorUserId,
			Instant occurredAt,
			List<String> recipientUserIds,
			long apiKeyId,
			String provider,
			String alias
	) implements TeamDomainOutboundEvent {

		public static TeamApiKeyDeletionCancelledEvent of(
				String actorUserId,
				Long teamId,
				String teamName,
				List<String> recipientUserIds,
				long apiKeyId,
				String provider,
				String alias,
				Instant occurredAt
		) {
			return new TeamApiKeyDeletionCancelledEvent(
					TeamEventTypes.TEAM_API_KEY_DELETION_CANCELLED,
					String.valueOf(teamId),
					teamName,
					actorUserId,
					occurredAt,
					List.copyOf(recipientUserIds),
					apiKeyId,
					provider,
					alias
			);
		}
	}
}
