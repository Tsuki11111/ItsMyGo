import { get, post } from './http'
import type { NotificationListView } from './types'

/** 最近通知 + 未读数 */
export function fetchNotifications(limit = 20): Promise<NotificationListView> {
  return get<NotificationListView>('/notifications', { limit }, 15000)
}

/** 未读数（铃铛轮询用，轻量） */
export function fetchNotificationUnreadCount(): Promise<number> {
  return get<number>('/notifications/unread-count', undefined, 10000)
}

export function markNotificationRead(id: number): Promise<number> {
  return post<number>(`/notifications/${id}/read`)
}

export function markAllNotificationsRead(): Promise<number> {
  return post<number>('/notifications/read-all')
}
