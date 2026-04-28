export type InAppNotification = {
  id: string
  userId: string
  title: string
  body: string
  type: string | null
  readAt: string | null
  createdAt: string
  meta?: unknown | null
}

export type InAppNotificationListResponse = {
  items: InAppNotification[]
  nextCursor: string | null
}

