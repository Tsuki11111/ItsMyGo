<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import ProductImage from '@/components/ProductImage.vue'
import type { ChatMessage, ChatOrderCard, ChatProductCard, ChatScope } from '@/api/types'

const props = defineProps<{
  message: ChatMessage
  mine: boolean
  read?: boolean
  mode: ChatScope
}>()

const productCard = computed(() =>
  props.message.contentType === 'PRODUCT'
    ? (props.message.card as ChatProductCard | null)
    : null,
)
const orderCard = computed(() =>
  props.message.contentType === 'ORDER'
    ? (props.message.card as ChatOrderCard | null)
    : null,
)
const isAgent = computed(() => props.message.senderType === 'AGENT')

function formatTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()
  const hm = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  if (sameDay) return hm
  const md = `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return `${md} ${hm}`
}
</script>

<template>
  <div class="message-row" :class="{ mine, agent: isAgent }">
    <span v-if="isAgent" class="agent-label">买小购 · 平台客服</span>
    <div class="bubble" :class="{ 'card-bubble': message.contentType !== 'TEXT' }">
      <p v-if="message.contentType === 'TEXT'" class="text">{{ message.content }}</p>

      <el-image
        v-else-if="message.contentType === 'IMAGE'"
        :src="message.content"
        :preview-src-list="[message.content]"
        preview-teleported
        fit="cover"
        class="chat-image"
      />

      <template v-else-if="productCard">
        <RouterLink :to="`/product/${productCard.productId}`" class="card product-card">
          <div class="card-thumb">
            <ProductImage
              :src="productCard.image"
              :name="productCard.name"
              :id="productCard.productId"
            />
          </div>
          <div class="card-info">
            <p class="card-name">{{ productCard.name }}</p>
            <p class="card-price">¥{{ Number(productCard.price).toFixed(2) }}</p>
            <p class="card-tip">点击查看商品</p>
          </div>
        </RouterLink>
      </template>

      <template v-else-if="orderCard">
        <component
          :is="mode === 'CUSTOMER' ? RouterLink : 'div'"
          :to="mode === 'CUSTOMER' ? `/order/${orderCard.orderId}` : undefined"
          class="card order-card"
        >
          <div class="card-info">
            <p class="card-name">订单 {{ orderCard.orderNo }}</p>
            <p class="card-desc">
              {{ orderCard.firstItemName || '订单商品' }}
              <span v-if="orderCard.statusLabel"> · {{ orderCard.statusLabel }}</span>
            </p>
            <p class="card-price">¥{{ Number(orderCard.totalAmount).toFixed(2) }}</p>
            <p v-if="mode === 'CUSTOMER'" class="card-tip">点击查看订单</p>
          </div>
        </component>
      </template>

      <p class="time">{{ formatTime(message.createdAt) }}</p>
    </div>
    <span v-if="mine" class="read-state" :class="{ read }">{{ read ? '已读' : '未读' }}</span>
  </div>
</template>

<style scoped>
.message-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  margin-bottom: 12px;
}

.message-row.mine {
  align-items: flex-end;
}

.message-row.agent {
  align-items: center;
}

.agent-label {
  margin-bottom: 2px;
  font-size: 11px;
  color: var(--c-pink);
}

.message-row.agent .bubble {
  max-width: 82%;
  background: #fff7fa;
  border-color: #f2cede;
  box-shadow: 0 2px 8px rgba(214, 51, 108, 0.06);
}

.bubble {
  position: relative;
  max-width: 72%;
  padding: 9px 12px 7px;
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  word-break: break-word;
}

.mine .bubble {
  background: var(--c-pink-soft);
  border-color: transparent;
}

.bubble.card-bubble {
  padding: 8px;
}

.text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--c-text);
  white-space: pre-wrap;
}

.chat-image {
  display: block;
  width: 220px;
  max-width: 100%;
  border-radius: 8px;
  cursor: zoom-in;
}

.card {
  display: flex;
  gap: 10px;
  width: 250px;
  padding: 6px;
  color: inherit;
  text-decoration: none;
}

.card-thumb {
  width: 64px;
  height: 64px;
  flex: none;
  border-radius: 8px;
  overflow: hidden;
  background: var(--c-pink-tint);
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-price {
  margin: 4px 0 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--c-pink);
}

.card-tip {
  margin: 3px 0 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.time {
  margin: 6px 0 0;
  font-size: 10px;
  color: var(--c-text-3);
  text-align: right;
}

.read-state {
  font-size: 10px;
  color: var(--c-text-3);
}

.read-state.read {
  color: var(--c-pink);
}
</style>
