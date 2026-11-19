import type { AgentImage, AgentProductCard } from '@/api/types'

export type AnswerBlock =
  | { type: 'text'; html: string }
  | { type: 'card'; product: AgentProductCard }
  | { type: 'cards'; products: AgentProductCard[] }
  | { type: 'image'; image: AgentImage; index: number }
  | { type: 'images'; images: Array<{ image: AgentImage; index: number }> }

interface MessageWithProducts {
  text?: string
  products?: AgentProductCard[]
  images?: AgentImage[]
  /** 打字机进行中：未引用的图先不展示；最后一行未写完时不插引用图 */
  typing?: boolean
}

/** 正文里的图片引用：兼容「图 3」和「第 3 张图」 */
function imageRefs(line: string): number[] {
  const refs: number[] = []
  for (const match of line.matchAll(/图\s*(\d+)|第\s*(\d+)\s*张图/g)) {
    const index = Number(match[1] || match[2])
    if (Number.isInteger(index) && !refs.includes(index)) refs.push(index)
  }
  return refs
}

/** 最后一行还在打字时，等句末标点出来再插图，避免图先于整句话出现 */
function lineComplete(line: string, isLast: boolean, typing: boolean): boolean {
  if (!typing || !isLast) return true
  return /[。！？!?；;]\s*$/.test(line)
}

/**
 * 把回答切成「文字块 / 商品卡 / 图片」交替的序列。
 *
 * 商品链接（/product/{id}）或商品名所在行作为该商品的起点：从这一行开始攒文字，
 * 遇到下一款商品时先把上一款的文字和卡片依次放出，因此卡片总跟在对应介绍之后。
 * 图片按正文里的「图 N / 第 N 张图」挂到该行之后；同一张图只放一次，没被引用的
 * 图片在最后统一收尾。打字机模式（typing=true）下，未引用的图等打完整段再出现。
 *
 * `render` 由调用方注入（组件里是 marked + DOMPurify），便于单测直接检查块顺序。
 */
export function messageBlocks(
  message: MessageWithProducts,
  render: (text: string) => string,
): AnswerBlock[] {
  const text = message.text || ''
  const products = message.products || []
  const images = message.images || []
  const typing = Boolean(message.typing)
  if (!products.length && !images.length) {
    return text ? [{ type: 'text', html: render(text) }] : []
  }

  const used = new Set<string>()
  const usedImages = new Set<number>()
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

  const flushImages = (refs: number[]) => {
    for (const index of refs) {
      if (index < 1 || index > images.length || usedImages.has(index)) continue
      usedImages.add(index)
      blocks.push({ type: 'image', image: images[index - 1], index })
    }
  }

  const lines = text.split('\n')
  for (const [lineIndex, line] of lines.entries()) {
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

    const refs = imageRefs(line)
    if (refs.length && lineComplete(line, lineIndex === lines.length - 1, typing)) {
      flushText()
      flushCards()
      flushImages(refs)
    }
  }
  flushText()
  flushCards()

  const restProducts = products.filter((product) => !used.has(String(product.productId)))
  if (restProducts.length) blocks.push({ type: 'cards', products: restProducts })

  if (!typing) {
    const rest = images
      .map((image, index) => ({ image, index: index + 1 }))
      .filter((item) => !usedImages.has(item.index))
    if (rest.length) blocks.push({ type: 'images', images: rest })
  }
  return blocks
}
