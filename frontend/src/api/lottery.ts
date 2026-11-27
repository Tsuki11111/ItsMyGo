import { get, post } from './http'
import type {
  LotteryActivityPublicView,
  LotteryDrawResponse,
  LotteryMyRecordView,
  LotteryPrizePublicView,
} from './types'

/** 当前进行中的活动；没有活动时后端返回 data: null */
export function fetchActiveLottery(): Promise<LotteryActivityPublicView | null> {
  return get<LotteryActivityPublicView | null>('/lottery/active')
}

/** 奖池匿名可看 */
export function fetchLotteryPrizes(activityId: number): Promise<LotteryPrizePublicView[]> {
  return get<LotteryPrizePublicView[]>(`/lottery/${activityId}/prizes`)
}

/** 我的抽奖记录，需要登录 */
export function fetchMyLotteryRecord(activityId: number): Promise<LotteryMyRecordView> {
  return get<LotteryMyRecordView>(`/lottery/${activityId}/me`)
}

/** 抽奖，需要登录；后端幂等，重复调用返回同一条记录 */
export function drawLottery(activityId: number): Promise<LotteryDrawResponse> {
  return post<LotteryDrawResponse>(`/lottery/${activityId}/draw`)
}
