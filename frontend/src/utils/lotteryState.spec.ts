import { describe, expect, it } from 'vitest'
import { computeSpinRotation, formatCountdown, resolveLotteryState } from './lotteryState'

const activity = {
  startAt: '2026-12-01T10:00:00',
  endAt: '2026-12-03T10:00:00',
}

function at(value: string): number {
  return new Date(value).getTime()
}

describe('resolveLotteryState', () => {
  it('没有活动时返回 NO_ACTIVITY', () => {
    expect(resolveLotteryState(null, null, at('2026-12-02T10:00:00'), false))
      .toBe('NO_ACTIVITY')
  })

  it('活动开始前返回 NOT_STARTED', () => {
    expect(resolveLotteryState(activity, null, at('2026-11-30T10:00:00'), false))
      .toBe('NOT_STARTED')
  })

  it('活动进行中且未抽时返回 CAN_DRAW', () => {
    expect(resolveLotteryState(activity, { drawn: false }, at('2026-12-02T10:00:00'), false))
      .toBe('CAN_DRAW')
  })

  it('抽奖请求中返回 DRAWING', () => {
    expect(resolveLotteryState(activity, null, at('2026-12-02T10:00:00'), true))
      .toBe('DRAWING')
  })

  it('已经抽过时返回 DRAWN', () => {
    expect(resolveLotteryState(activity, { drawn: true }, at('2026-12-02T10:00:00'), true))
      .toBe('DRAWN')
  })

  it('活动结束后返回 ENDED', () => {
    expect(resolveLotteryState(activity, null, at('2026-12-04T10:00:00'), false))
      .toBe('ENDED')
  })
})

describe('formatCountdown', () => {
  it('小于一天只显示时分秒', () => {
    expect(formatCountdown(65)).toBe('00:01:05')
  })

  it('超过一天显示天数和时分秒', () => {
    expect(formatCountdown(90065)).toBe('1 天 01:01:05')
  })

  it('负数按 0 处理', () => {
    expect(formatCountdown(-1)).toBe('00:00:00')
  })
})

describe('computeSpinRotation', () => {
  it('目标扇区最终停在顶部', () => {
    for (let index = 0; index < 4; index++) {
      const rotation = computeSpinRotation(0, index, 4)
      const normalized = ((rotation % 360) + 360) % 360
      expect(normalized).toBe((360 - index * 90) % 360)
    }
  })

  it('每次至少多转 turns 圈', () => {
    expect(computeSpinRotation(0, 0, 4)).toBe(1800)
    expect(computeSpinRotation(1800, 0, 4)).toBe(3600)
  })

  it('扇区数为 0 时保持原角度', () => {
    expect(computeSpinRotation(120, 0, 0)).toBe(120)
  })
})
