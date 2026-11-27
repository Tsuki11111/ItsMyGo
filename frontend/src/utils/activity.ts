/** 活动卡片的状态由「管理端状态 + 起止时间」共同决定 */

export type ActivityPhase = 'NOT_STARTED' | 'ONGOING' | 'ENDED'

export interface ActivityTimeLike {
  status: number
  startAt: string
  endAt: string
}

export function activityPhase(activity: ActivityTimeLike, now: number): ActivityPhase {
  if (activity.status === 2) {
    return 'ENDED'
  }
  const startAt = new Date(activity.startAt).getTime()
  const endAt = new Date(activity.endAt).getTime()
  if (now < startAt) {
    return 'NOT_STARTED'
  }
  if (now > endAt) {
    return 'ENDED'
  }
  return 'ONGOING'
}

export function activityPhaseLabel(phase: ActivityPhase): string {
  if (phase === 'NOT_STARTED') return '未开始'
  if (phase === 'ENDED') return '已结束'
  return '进行中'
}

/** 卡片上的日期只显示到天，避免长表格换行 */
export function formatActivityDate(value: string): string {
  return value.replace('T', ' ').slice(0, 10)
}
