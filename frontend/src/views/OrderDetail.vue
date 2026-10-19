<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import ReviewDialog from '@/views/user/components/ReviewDialog.vue'
import { applyRefund, cancelOrder, confirmOrder, fetchOrder, payOrder } from '@/api/order'
import { startConversation } from '@/api/chat'
import { useUserStore } from '@/stores/user'
import type { OrderItem, OrderView } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const view = ref<OrderView | null>(null)
const loading = ref(true)
const acting = ref(false)
const notFound = ref(false)
const startingChat = ref(false)

const order = computed(() => view.value?.order ?? null)
const payable = computed(() => order.value?.status === 'PENDING_PAYMENT')
/** 待支付 / 已支付可取消；已发货之后只能走退款 */
const cancelable = computed(() =>
  order.value?.status === 'PENDING_PAYMENT' || order.value?.status === 'PAID',
)
const confirmable = computed(() => order.value?.status === 'SHIPPED')
/** 付过钱且还没退过：已支付 / 已发货 / 已完成 */
const refundable = computed(() =>
  ['PAID', 'SHIPPED', 'COMPLETED'].includes(order.value?.status ?? ''),
)

const reviewVisible = ref(false)
const reviewItem = ref<OrderItem | null>(null)

function openReview(item: OrderItem) {
  reviewItem.value = item
  reviewVisible.value = true
}

/** 从订单页联系卖家：新会话会自动附一条订单卡片 */
async function contactSeller() {
  if (!order.value) return
  if (!userStore.isLoggedIn) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  startingChat.value = true
  try {
    const conversation = await startConversation({
      merchantId: order.value.merchantId,
      orderId: order.value.id,
    })
    await router.push(`/user/messages/${conversation.id}`)
  } catch {
    // 拦截器已提示
  } finally {
    startingChat.value = false
  }
}

/** 后端给的是 LocalDateTime（`2026-10-16T18:34:30`），显示时把 T 换掉就行 */
function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

async function load() {
  loading.value = true
  notFound.value = false
  try {
    view.value = await fetchOrder(Number(route.params.id))
  } catch {
    // 不存在 / 不是自己的订单 —— 后端一律当「不存在」返回 404
    view.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

async function onPay() {
  if (!order.value) return
  acting.value = true
  try {
    view.value = await payOrder(order.value.id)
    ElMessage.success('支付成功')
  } catch {
    // 状态已经变了（比如刚好被超时任务取消），拦截器已提示；重新拉一次看真实状态
    await load()
  } finally {
    acting.value = false
  }
}

async function onCancel() {
  if (!order.value) return
  try {
    await ElMessageBox.confirm(
      '取消后库存会回滚；若使用了优惠券且未过期，券会自动退回。确定取消订单吗？',
      '取消订单',
      { type: 'warning', confirmButtonText: '取消订单', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }

  acting.value = true
  try {
    view.value = await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
  } catch {
    await load()
  } finally {
    acting.value = false
  }
}

async function onConfirmReceipt() {
  if (!order.value) return
  try {
    await ElMessageBox.confirm('确认已经收到商品吗？确认后订单将完成。', '确认收货', {
      type: 'warning',
      confirmButtonText: '确认收货',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  acting.value = true
  try {
    view.value = await confirmOrder(order.value.id)
    ElMessage.success('已确认收货')
  } catch {
    await load()
  } finally {
    acting.value = false
  }
}

async function onRefund() {
  if (!order.value) return
  try {
    await ElMessageBox.confirm(
      '申请退款后订单将进入「退款中」，等待商家审核。确定申请吗？',
      '申请退款',
      { type: 'warning', confirmButtonText: '申请退款', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  acting.value = true
  try {
    view.value = await applyRefund(order.value.id)
    ElMessage.success('已提交退款申请')
  } catch {
    await load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="order-page">
    <el-empty v-if="notFound" description="订单不存在">
      <el-button type="primary" @click="router.push('/')">回到首页</el-button>
    </el-empty>

    <template v-else-if="view && order">
      <el-breadcrumb separator="/" class="crumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>订单详情</el-breadcrumb-item>
      </el-breadcrumb>

      <!-- ── 状态 ── -->
      <section class="block status-block">
        <div class="status-main">
          <span class="status-label">{{ view.statusLabel }}</span>
          <span class="shop-name">{{ order.merchantName }}</span>
          <span class="order-no">订单号 {{ order.orderNo }}</span>
        </div>
        <div class="status-actions">
          <button
            v-if="order.merchantId !== 1"
            type="button"
            class="action-btn"
            :disabled="startingChat"
            @click="contactSeller"
          >
            {{ startingChat ? '打开中…' : '联系卖家' }}
          </button>
          <button
            v-if="payable"
            type="button"
            class="pay-btn"
            :disabled="acting"
            @click="onPay"
          >
            {{ acting ? '处理中…' : '立即支付' }}
          </button>
          <button
            v-if="cancelable"
            type="button"
            class="action-btn"
            :disabled="acting"
            @click="onCancel"
          >
            取消订单
          </button>
          <button
            v-if="confirmable"
            type="button"
            class="pay-btn"
            :disabled="acting"
            @click="onConfirmReceipt"
          >
            {{ acting ? '处理中…' : '确认收货' }}
          </button>
          <button
            v-if="refundable"
            type="button"
            class="action-btn"
            :disabled="acting"
            @click="onRefund"
          >
            申请退款
          </button>
        </div>
      </section>

      <!-- ── 收货信息 ── -->
      <section class="block">
        <h2 class="block-title">收货信息</h2>
        <p class="line">
          <span class="line-name">{{ order.receiver }}</span>
          <span class="line-sub">{{ order.phone }}</span>
        </p>
        <p class="line-sub">{{ order.address }}</p>
      </section>

      <!-- ── 商品清单 ── -->
      <section class="block">
        <h2 class="block-title">商品清单</h2>
        <div v-for="item in view.items" :key="item.id" class="goods-row">
          <div class="goods-thumb">
            <ProductImage :src="item.productImage" :name="item.productName" :id="item.productId" />
          </div>
          <div class="goods-info">
            <p class="goods-name">{{ item.productName }}</p>
            <p class="goods-unit">
              <!-- 规格是下单时的快照：商户后来改了规格名，这里也不会跟着变 -->
              <span v-if="item.specText" class="goods-spec">{{ item.specText }}</span>
              ¥{{ item.unitPrice.toFixed(2) }} × {{ item.quantity }}
            </p>
          </div>
          <div class="price goods-subtotal">
            <span class="price-symbol">¥</span>
            <span class="price-num">{{ item.subtotal.toFixed(2) }}</span>
          </div>
          <button
            v-if="order.status === 'COMPLETED'"
            type="button"
            class="review-btn"
            :class="{ done: item.reviewed }"
            :disabled="item.reviewed"
            @click="openReview(item)"
          >
            {{ item.reviewed ? '已评价' : '评价' }}
          </button>
        </div>

        <div v-if="order.discountAmount > 0" class="total-row sub-row">
          <span class="total-label">商品总额</span>
          <span class="original-amount">¥{{ order.originalAmount.toFixed(2) }}</span>
        </div>
        <div v-if="order.discountAmount > 0" class="total-row sub-row">
          <span class="total-label">优惠（{{ order.couponName }}）</span>
          <span class="discount-amount">-¥{{ order.discountAmount.toFixed(2) }}</span>
        </div>

        <div class="total-row">
          <span class="total-label">合计</span>
          <span class="price">
            <span class="price-symbol">¥</span>
            <span class="price-num total-num">{{ order.totalAmount.toFixed(2) }}</span>
          </span>
        </div>
      </section>

      <!-- ── 时间线 ── -->
      <section class="block">
        <h2 class="block-title">订单信息</h2>
        <dl class="meta">
          <dt>下单时间</dt>
          <dd>{{ formatTime(order.createdAt) }}</dd>
          <template v-if="order.paidAt">
            <dt>支付时间</dt>
            <dd>{{ formatTime(order.paidAt) }}</dd>
          </template>
          <template v-if="order.shippedAt">
            <dt>发货时间</dt>
            <dd>{{ formatTime(order.shippedAt) }}</dd>
          </template>
          <template v-if="order.trackingNo">
            <dt>物流单号</dt>
            <dd>{{ order.trackingNo }}</dd>
          </template>
          <template v-if="order.cancelledAt">
            <dt>取消时间</dt>
            <dd>{{ formatTime(order.cancelledAt) }}</dd>
          </template>
        </dl>
      </section>
    </template>

    <ReviewDialog v-model="reviewVisible" :order-item="reviewItem" @submitted="load" />
  </div>
</template>

<style scoped>
.crumb {
  margin-bottom: 16px;
}

.block {
  margin-bottom: 18px;
  padding: 20px 22px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.block-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

/* ── 状态 ── */
.status-block {
  display: flex;
  align-items: center;
  gap: 24px;
}

.status-main {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

/* 状态是「选中态之外最该被看见的东西」，但仍用中性色 —— 粉色留给主按钮 */
.status-label {
  font-size: 20px;
  font-weight: 600;
  color: var(--c-text);
}

.order-no {
  font-size: 12.5px;
  color: var(--c-text-3);
}

.shop-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-2);
}

.status-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.pay-btn {
  flex: none;
  width: 140px;
  padding: 11px 0;
  font-size: 14px;
  font-family: inherit;
  color: #fff;
  background: var(--c-pink);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s;
}

.pay-btn:hover:not(:disabled) {
  background: var(--c-pink-hover);
}

.pay-btn:disabled {
  background: var(--c-text-3);
  cursor: not-allowed;
}

.action-btn {
  flex: none;
  padding: 10px 18px;
  font-size: 14px;
  font-family: inherit;
  color: var(--c-text-2);
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.action-btn:hover:not(:disabled) {
  color: var(--c-pink);
  border-color: var(--c-pink);
}

.action-btn:disabled {
  color: var(--c-text-3);
  cursor: not-allowed;
}

/* ── 收货信息 ── */
.line {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 0 0 4px;
}

.line-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.line-sub {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-2);
}

/* ── 商品行 ── */
.goods-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--c-border);
}

.goods-row:last-of-type {
  border-bottom: none;
}

.goods-thumb {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: var(--radius-tag);
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 20px;
}

.goods-info {
  flex: 1;
  min-width: 0;
}

.goods-name {
  margin: 0;
  font-size: 14px;
  color: var(--c-text);
}

.goods-unit {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.goods-spec {
  margin-right: 8px;
  color: var(--c-text-2);
}

.goods-subtotal .price-num {
  font-size: 15px;
}

.review-btn {
  flex: none;
  padding: 3px 12px;
  font-size: 12px;
  font-family: inherit;
  color: var(--c-pink);
  background: #fff;
  border: 1px solid var(--c-pink);
  border-radius: var(--radius-btn);
  cursor: pointer;
}

.review-btn.done {
  color: var(--c-text-3);
  border-color: var(--c-border);
  cursor: default;
}

.total-row.sub-row {
  margin-top: 8px;
  padding-top: 0;
  border-top: none;
}

.original-amount {
  font-size: 13px;
  color: var(--c-text-3);
  text-decoration: line-through;
}

.discount-amount {
  font-size: 13px;
  color: var(--c-pink);
}

.total-row {
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--c-border);
}

.total-label {
  font-size: 13px;
  color: var(--c-text-2);
}

.total-num {
  font-size: 24px;
}

/* ── 订单信息 ── */
.meta {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}

.meta dt {
  color: var(--c-text-3);
}

.meta dd {
  margin: 0;
  color: var(--c-text-2);
}
</style>
