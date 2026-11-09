import { describe, expect, it } from 'vitest'
import type { AgentProductCard } from '@/api/types'
import { dedupeProductCards } from './productCards'

function card(productId: string): AgentProductCard {
  return {
    productId,
    name: `商品${productId}`,
    image: '',
    price: '0',
    stock: '0',
    sales: '0',
    rating: '',
    merchantName: '',
    specText: '',
    categoryName: '',
  }
}

describe('dedupeProductCards', () => {
  it('同一条消息内同一商品只保留第一张卡', () => {
    expect(dedupeProductCards([card('1722'), card('1722'), card('1724')]).map((item) => item.productId))
      .toEqual(['1722', '1724'])
  })

  it('不同轮次各自去重，后面的推荐不会被前面出现过的商品吞卡', () => {
    const first = dedupeProductCards([card('1722'), card('1722')])
    const second = dedupeProductCards([card('1722'), card('1726')])
    expect(first.map((item) => item.productId)).toEqual(['1722'])
    expect(second.map((item) => item.productId)).toEqual(['1722', '1726'])
  })
})
