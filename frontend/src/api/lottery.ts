import { get, post, put } from './http'
import type {
  LotteryClaimAddressPayload,
  LotteryClaimView,
  LotteryDrawResponse,
  LotteryMyRecordView,
  LotteryPrizePublicView,
  LotteryPrizeRecordView,
  PageResponse,
} from './types'

/** 奖池匿名可看 */
export function fetchLotteryPrizes(activityId: number): Promise<LotteryPrizePublicView[]> {
  return get<LotteryPrizePublicView[]>(`/lottery/${activityId}/prizes`)
}

/** 我今天的抽奖记录，需要登录 */
export function fetchMyLotteryRecord(activityId: number): Promise<LotteryMyRecordView> {
  return get<LotteryMyRecordView>(`/lottery/${activityId}/me`)
}

/** 抽奖，需要登录；后端幂等，同一天重复调用返回同一条记录 */
export function drawLottery(activityId: number): Promise<LotteryDrawResponse> {
  return post<LotteryDrawResponse>(`/lottery/${activityId}/draw`)
}

/** 我的奖品（只含中奖记录），需要登录 */
export function fetchMyLotteryPrizes(
  page = 1,
  size = 10,
): Promise<PageResponse<LotteryPrizeRecordView>> {
  return get<PageResponse<LotteryPrizeRecordView>>(`/lottery/my-prizes?page=${page}&size=${size}`)
}

/** 实物奖品填写收货信息 */
export function saveLotteryClaimAddress(
  claimId: number,
  payload: LotteryClaimAddressPayload,
): Promise<LotteryClaimView> {
  return put<LotteryClaimView>(`/lottery/claims/${claimId}/address`, payload)
}
