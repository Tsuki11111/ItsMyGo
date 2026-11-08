<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import { fetchAddresses } from '@/api/address'
import { updateOrderAddress } from '@/api/order'
import { sendMessage } from '@/api/chat'
import type {
  Address,
  ChatMessage,
  ChatOrderCard,
  ChatProductCard,
  ChatScope,
} from '@/api/types'

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
  props.message.contentType === 'ORDER' || props.message.contentType === 'ORDER_CONFIRM'
    ? (props.message.card as ChatOrderCard | null)
    : null,
)
const isOrderConfirm = computed(() => props.message.contentType === 'ORDER_CONFIRM')
/** 确认卡只在顾客侧、且订单仍可改（待发货）时给操作入口 */
const canActOnConfirm = computed(() => isOrderConfirm.value
  && props.mode === 'CUSTOMER'
  && orderCard.value?.status === 'PAID')
const confirmed = ref(false)
const addressDialog = ref(false)
const addressLoading = ref(false)
const addresses = ref<Address[]>([])
const selectedAddressId = ref<number | null>(null)
const submitting = ref(false)

async function sendText(text: string) {
  await sendMessage(props.message.conversationId, { type: 'TEXT', text })
}

async function confirmOrderInfo() {
  if (confirmed.value) return
  confirmed.value = true
  try {
    await sendText('订单信息确认无误，请按此信息发货')
  } catch {
    confirmed.value = false
    ElMessage.error('发送失败，请稍后再试')
  }
}

async function openAddressDialog() {
  addressDialog.value = true
  addressLoading.value = true
  try {
    addresses.value = await fetchAddresses()
    selectedAddressId.value = addresses.value.find((item) => item.isDefault === 1)?.id
      ?? addresses.value[0]?.id ?? null
  } catch {
    ElMessage.error('地址加载失败')
  } finally {
    addressLoading.value = false
  }
}

async function submitAddress() {
  const order = orderCard.value
  if (!order || !selectedAddressId.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  submitting.value = true
  try {
    await updateOrderAddress(order.orderId, selectedAddressId.value)
    addressDialog.value = false
    confirmed.value = true
    ElMessage.success('收货地址已修改')
    await sendText('收货地址已修改，请按新地址发货')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '修改失败，订单可能已发货')
  } finally {
    submitting.value = false
  }
}
const isAgent = computed(() => props.message.senderType === 'AGENT')
const isSystem = computed(() => props.message.senderType === 'SYSTEM')

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
  <div v-if="isSystem" class="system-row">
    <span class="system-text">{{ message.content }}</span>
    <span class="system-time">{{ formatTime(message.createdAt) }}</span>
  </div>
  <div v-else class="message-row" :class="{ mine, agent: isAgent }">
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

      <template v-else-if="message.contentType === 'PROMOTION'">
        <p class="promotion-label">智能推荐</p>
        <p class="text">{{ message.content }}</p>
      </template>

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
        <div class="order-confirm-wrap">
          <p v-if="isOrderConfirm" class="confirm-title">请核对订单信息</p>
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
          <p v-if="isOrderConfirm" class="confirm-tip">发货前可修改收货地址</p>
          <div v-if="canActOnConfirm" class="confirm-actions">
            <el-button size="small" :disabled="confirmed" @click="confirmOrderInfo">
              {{ confirmed ? '已确认' : '信息无误' }}
            </el-button>
            <el-button size="small" type="primary" plain @click="openAddressDialog">
              修改地址
            </el-button>
          </div>
        </div>
      </template>

      <!-- 未知/历史消息类型兜底：至少展示文本，不能渲染成只有时间的空气泡 -->
      <p v-else-if="message.content" class="text">{{ message.content }}</p>

      <el-dialog v-model="addressDialog" title="修改收货地址" width="420px" append-to-body>
        <div v-loading="addressLoading" class="address-list">
          <el-radio-group v-model="selectedAddressId" class="address-radio">
            <el-radio v-for="item in addresses" :key="item.id" :value="item.id">
              {{ item.receiver }} {{ item.phone }} — {{ item.province }}{{ item.city
              }}{{ item.district }}{{ item.detail }}
            </el-radio>
          </el-radio-group>
          <el-empty v-if="!addressLoading && !addresses.length" description="还没有收货地址" />
        </div>
        <template #footer>
          <el-button @click="addressDialog = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitAddress">保存</el-button>
        </template>
      </el-dialog>

      <p class="time">{{ formatTime(message.createdAt) }}</p>
    </div>
    <span v-if="mine" class="read-state" :class="{ read }">{{ read ? '已读' : '未读' }}</span>
  </div>
</template>

<style scoped>
.order-confirm-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.confirm-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}

.confirm-tip {
  margin: 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.confirm-actions {
  display: flex;
  gap: 8px;
}

.address-list {
  max-height: 300px;
  overflow-y: auto;
}

.address-radio {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.system-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  margin-bottom: 12px;
}

.system-text {
  max-width: 88%;
  padding: 4px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-2);
  background: var(--c-pink-tint);
  border-radius: 999px;
  text-align: center;
}

.system-time {
  font-size: 10px;
  color: var(--c-text-3);
}

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

.promotion-label {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--c-pink);
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
