import { describe, expect, it } from 'vitest'
import { ORDER_STATUS_OPTIONS, orderStatusTag } from './orderStatus'

describe('orderStatusTag', () => {
  it('maps terminal states to their tags', () => {
    expect(orderStatusTag('COMPLETED')).toBe('success')
    expect(orderStatusTag('CANCELLED')).toBe('info')
    expect(orderStatusTag('REFUNDED')).toBe('danger')
  })

  it('maps in-flight states to warning/primary', () => {
    expect(orderStatusTag('PENDING_PAYMENT')).toBe('warning')
    expect(orderStatusTag('REFUNDING')).toBe('warning')
    expect(orderStatusTag('PAID')).toBe('primary')
    expect(orderStatusTag('SHIPPED')).toBe('primary')
  })

  it('falls back to info for unknown status', () => {
    expect(orderStatusTag('UNKNOWN')).toBe('info')
  })

  it('covers every backend order status in the filter options', () => {
    expect(ORDER_STATUS_OPTIONS.map((o) => o.value)).toEqual([
      'CREATING',
      'CREATE_FAILED',
      'PENDING_PAYMENT',
      'PAID',
      'SHIPPED',
      'COMPLETED',
      'CANCELLED',
      'REFUNDING',
      'REFUNDED',
    ])
  })
})
