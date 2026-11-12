import { describe, expect, it } from 'vitest'
import type { AgentProductCard } from '@/api/types'
import { messageBlocks } from './messageBlocks'

function card(productId: string, name: string): AgentProductCard {
  return {
    productId,
    name,
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

const render = (text: string) => text

describe('messageBlocks', () => {
  it('商品卡跟在对应介绍之后，不堆到整段回答末尾', () => {
    const products = [card('1', '商品A'), card('2', '商品B'), card('3', '商品C')]
    const blocks = messageBlocks(
      {
        text: [
          '为您挑选了这几款：',
          '1. 商品A ￥100 /product/1',
          'A 很适合家用。',
          '2. 商品B ￥200 /product/2',
          'B 打印稳定。',
          '3. 商品C ￥300 /product/3',
          'C 色彩不错。',
          '还需要我帮您对比吗？',
        ].join('\n'),
        products,
      },
      render,
    )

    const cards = blocks.flatMap((block) =>
      block.type === 'card' ? [block.product.productId] : [],
    )
    expect(cards).toEqual(['1', '2', '3'])
    // 每张卡前面的文字块包含对应商品那一行，而不是其它商品的介绍。
    for (const block of blocks) {
      if (block.type !== 'card') continue
      const index = blocks.indexOf(block)
      const previous = blocks[index - 1]
      expect(previous.type).toBe('text')
      expect(previous.type === 'text' ? previous.html : '').toContain(block.product.name)
    }
  })

  it('五款商品都有引用时五张卡都出现', () => {
    const products = ['1', '2', '3', '4', '5'].map((id) => card(id, `商品${id}`))
    const text = products
      .map((product) => `${product.name} 推荐 /product/${product.productId}`)
      .join('\n')
    const blocks = messageBlocks({ text, products }, render)
    const cards = blocks.flatMap((block) =>
      block.type === 'card' ? [block.product.productId] : [],
    )
    expect(cards).toEqual(['1', '2', '3', '4', '5'])
  })

  it('正文未引用的卡片统一收尾，不会丢失', () => {
    const products = [card('1', '商品A'), card('2', '商品B')]
    const blocks = messageBlocks(
      { text: '推荐商品A /product/1', products },
      render,
    )
    expect(blocks.filter((block) => block.type === 'card')).toHaveLength(1)
    const rest = blocks.find((block) => block.type === 'cards')
    expect(rest?.type === 'cards' ? rest.products.map((item) => item.productId) : [])
      .toEqual(['2'])
  })

  it('同一商品在正文里多次出现只放一张卡', () => {
    const products = [card('1', '商品A')]
    const blocks = messageBlocks(
      {
        text: '商品A 很划算 /product/1\n再看一次商品A /product/1',
        products,
      },
      render,
    )
    expect(blocks.filter((block) => block.type === 'card')).toHaveLength(1)
  })

  it('一行同时提到链接商品和另一款名字时，只给链接商品挂卡', () => {
    const products = [card('1', '商品A'), card('2', '商品B')]
    const blocks = messageBlocks(
      {
        text: '商品A 比商品B 便宜 /product/1\n商品A 结实耐用\n商品B 打印更快 /product/2',
        products,
      },
      render,
    )
    const cards = blocks.flatMap((block) =>
      block.type === 'card' ? [block.product.productId] : [],
    )
    expect(cards).toEqual(['1', '2'])
    // 商品A 的卡片前只应是 A 的介绍，不能把 B 的卡一起挂过来。
    const firstCard = blocks.findIndex((block) => block.type === 'card')
    expect(blocks[firstCard - 1].type === 'text'
      ? (blocks[firstCard - 1] as { html: string }).html
      : '').toContain('商品A')
  })

  it('拆块后列表序号不会全部从 1 重新编号', () => {
    const products = [card('1', '商品A')]
    const blocks = messageBlocks(
      { text: '1. 商品A /product/1\n这是介绍', products },
      render,
    )
    expect(blocks[0].type).toBe('text')
    expect(blocks[0].type === 'text' ? blocks[0].html : '').toContain('1\\. 商品A')
  })

  it('没有商品卡时保持一整段文字', () => {
    const blocks = messageBlocks({ text: '普通回答', products: [] }, render)
    expect(blocks).toEqual([{ type: 'text', html: '普通回答' }])
  })
})
