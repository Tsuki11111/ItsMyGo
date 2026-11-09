import type { AgentProductCard } from '@/api/types'

/**
 * 单条消息内按 productId 去重。
 *
 * 每次调用都会新建去重集合，跨消息互不影响：历史回放时不同轮次的推荐
 * 各自保留卡片，否则后面推荐里与前面重复的商品会被整张吞掉。
 */
export function dedupeProductCards(products: AgentProductCard[]): AgentProductCard[] {
  const seen = new Set<string>()
  return (products || []).filter((product) => {
    const productId = String(product.productId)
    if (seen.has(productId)) return false
    seen.add(productId)
    return true
  })
}
