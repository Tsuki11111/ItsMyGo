import { describe, expect, it } from 'vitest'
import { activityPhase, activityPhaseLabel, formatActivityDate } from './activity'

const activity = {
  status: 1,
  startAt: '2026-12-01T10:00:00',
  endAt: '2026-12-03T10:00:00',
}

function at(value: string): number {
  return new Date(value).getTime()
}

describe('activityPhase', () => {
  it('开始前返回未开始', () => {
    expect(activityPhase(activity, at('2026-11-30T10:00:00'))).toBe('NOT_STARTED')
  })

  it('时间内返回进行中', () => {
    expect(activityPhase(activity, at('2026-12-02T10:00:00'))).toBe('ONGOING')
  })

  it('结束后返回已结束', () => {
    expect(activityPhase(activity, at('2026-12-04T10:00:00'))).toBe('ENDED')
  })

  it('管理端手动结束后即使时间未到也算已结束', () => {
    expect(activityPhase({ ...activity, status: 2 }, at('2026-12-02T10:00:00'))).toBe('ENDED')
  })
})

describe('activityPhaseLabel', () => {
  it('三种状态对应中文', () => {
    expect(activityPhaseLabel('NOT_STARTED')).toBe('未开始')
    expect(activityPhaseLabel('ONGOING')).toBe('进行中')
    expect(activityPhaseLabel('ENDED')).toBe('已结束')
  })
})

describe('formatActivityDate', () => {
  it('只保留日期', () => {
    expect(formatActivityDate('2026-12-01T10:00:00')).toBe('2026-12-01')
  })
})
