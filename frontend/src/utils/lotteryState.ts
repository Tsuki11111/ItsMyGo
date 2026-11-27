/** 抽奖页的状态由「活动 + 我的记录 + 当前时间」共同决定，抽成纯函数方便测试 */

export type LotteryPageState =
  | 'NO_ACTIVITY'
  | 'NOT_STARTED'
  | 'CAN_DRAW'
  | 'DRAWING'
  | 'DRAWN'
  | 'ENDED'

export interface ActivityTime {
  startAt: string
  endAt: string
}

export interface LotteryRecordLike {
  drawn: boolean
}

export function resolveLotteryState(
  activity: ActivityTime | null,
  record: LotteryRecordLike | null,
  now: number,
  drawing: boolean,
): LotteryPageState {
  if (!activity) {
    return 'NO_ACTIVITY'
  }
  if (record?.drawn) {
    return 'DRAWN'
  }
  if (drawing) {
    return 'DRAWING'
  }

  const startAt = new Date(activity.startAt).getTime()
  const endAt = new Date(activity.endAt).getTime()
  if (now < startAt) {
    return 'NOT_STARTED'
  }
  if (now > endAt) {
    return 'ENDED'
  }
  return 'CAN_DRAW'
}

/** 把秒数格式化成「2 天 03:12:45」或「03:12:45」 */
export function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60

  const clock = [hours, minutes, rest]
    .map((value) => String(value).padStart(2, '0'))
    .join(':')
  return days > 0 ? `${days} 天 ${clock}` : clock
}

/**
 * 计算转盘最终旋转角度：让第 targetIndex 个扇区的中心停在顶部指针处。
 *
 * <p>扇区 0 的中心在顶部，之后顺时针排列；转盘顺时针旋转，所以目标角度是
 * {@code 360 - index * step}。再额外转 {@code turns} 圈，避免看起来只是轻微抖动。</p>
 */
export function computeSpinRotation(
  current: number,
  targetIndex: number,
  itemCount: number,
  turns = 5,
): number {
  if (itemCount <= 0) {
    return current
  }
  const step = 360 / itemCount
  const target = (360 - targetIndex * step) % 360
  const currentMod = ((current % 360) + 360) % 360
  const delta = (target - currentMod + 360) % 360
  return current + 360 * turns + delta
}
