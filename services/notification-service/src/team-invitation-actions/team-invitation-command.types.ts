export type TeamInvitationDecision = 'ACCEPT' | 'REJECT';

/**
 * Command envelope for team-service to process invitation decisions.
 * Consumers must use `dedupeKey` (and/or `eventId`) for idempotency.
 */
export type TeamInvitationDecisionCommand = {
  schemaVersion: 1;
  eventType: 'TEAM_INVITATION_DECISION_COMMAND';
  eventId: string;
  occurredAt: string;
  invitationId: string;
  inviteeUserId: string;
  decision: TeamInvitationDecision;
  correlationId?: string;
};

