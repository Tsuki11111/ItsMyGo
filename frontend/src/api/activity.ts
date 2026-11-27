import { get } from './http'
import type { ActivityView } from './types'

/** 活动中心列表：全部活动，匿名可看 */
export function fetchActivities(): Promise<ActivityView[]> {
  return get<ActivityView[]>('/activities')
}

/** 活动详情：抽奖页按 activityId 使用 */
export function fetchActivity(activityId: number): Promise<ActivityView> {
  return get<ActivityView>(`/activities/${activityId}`)
}
