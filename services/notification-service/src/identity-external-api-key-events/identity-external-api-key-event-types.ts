/** Mirrors `libs/identity-events` `IdentityExternalApiKeyEventTypes` for AMQP routing in Node. */
export const IdentityExternalApiKeyEventTypes = {
  EXTERNAL_API_KEY_DELETED: 'EXTERNAL_API_KEY_DELETED',
  EXTERNAL_API_KEY_BUDGET_CHANGED: 'EXTERNAL_API_KEY_BUDGET_CHANGED',
} as const;
