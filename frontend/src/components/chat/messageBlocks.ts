import type { AgentProductCard } from '@/api/types'

export type AnswerBlock =
  | { type: 'text'; html: string }
  | { type: 'card'; product: AgentProductCard }
  | { type: 'cards'; products: AgentProductCard[] }

interface MessageWithProducts {
  text?: string
  products?: AgentProductCard[]
}

/**
 * 把回答切成「文字块 / 商品卡」交替的序列。
 *
 * 商品链接（/product/{id}）或商品名所在行作为该商品的起点：从这一行开始攒文字，
 * 遇到下一款商品时先把上一款的文字和卡片依次放出，因此卡片总跟在对应介绍之后。
 * 没被正文引用的卡片统一收尾，避免漏卡。
 *
 * `render` 由调用方注入（组件里是 marked + DOMPurify），便于单测直接检查块顺序。
 */
export function messageBlocks(
  message: MessageWithProducts,
  render: (text: string) => string,
): AnswerBlock[] {
  const text = message.text || ''
  const products = message.products || []
  if (!products.length) {
    return text ? [{ type: 'text', html: render(text) }] : []
  }

  const used = new Set<string>()
  const blocks: AnswerBlock[] = []
  const buffer: string[] = []
  let pending: AgentProductCard[] = []

  const flushText = () => {
    const raw = buffer.join('\n').trim()
    if (raw) {
      // 单个列表项被拆成独立文字块时转义行首序号，避免 marked 每块都从 1 重新编号。
      const preserved = raw
        .split('\n')
        .map((line) => line.replace(/^(\s*)(\d+)\.(\s)/, '$1$2\\.$3'))
        .join('\n')
      blocks.push({ type: 'text', html: render(preserved) })
    }
    buffer.length = 0
  }

  const flushCards = () => {
    for (const product of pending) blocks.push({ type: 'card', product })
    pending = []
  }

  for (const line of text.split('\n')) {
    const ids = [...line.matchAll(/\/product\/(\d+)/g)].map((match) => match[1])
    // 一行只挂一张卡：先认显式链接，再认最长的商品名。
    // 否则同一行提到 A 的链接、又提到 B 的名字时，两张卡会一起挂到这里，B 的段落反而没卡。
    let matched: AgentProductCard | undefined
    for (const id of ids) {
      matched = products.find(
        (product) => !used.has(String(product.productId)) && String(product.productId) === id,
      )
      if (matched) break
    }
    if (!matched) {
      matched = products
        .filter(
          (product) =>
            !used.has(String(product.productId)) &&
            product.name &&
            line.includes(product.name),
        )
        .sort((a, b) => b.name.length - a.name.length)[0]
    }
    if (matched) {
      // 上一款介绍收尾 → 先出文字、再出它的卡；然后开始下一款。
      flushText()
      flushCards()
      buffer.push(line)
      used.add(String(matched.productId))
      pending.push(matched)
    } else {
      buffer.push(line)
    }
  }
  flushText()
  flushCards()

  const rest = products.filter((product) => !used.has(String(product.productId)))
  if (rest.length) blocks.push({ type: 'cards', products: rest })
  return blocks
}
