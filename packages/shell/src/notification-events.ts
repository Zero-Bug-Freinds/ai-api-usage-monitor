/** Browser event: sidebar unread badge should refetch immediately. */
export const AI_USAGE_NOTIFICATIONS_CHANGED_EVENT = "ai-usage:notifications-changed"

export function dispatchNotificationsChanged(): void {
  if (typeof window === "undefined") return
  window.dispatchEvent(new CustomEvent(AI_USAGE_NOTIFICATIONS_CHANGED_EVENT, { bubbles: true }))
}
