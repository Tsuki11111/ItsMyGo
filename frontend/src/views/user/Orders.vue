<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import ProductImage from '@/components/ProductImage.vue'
import ReviewDialog from './components/ReviewDialog.vue'
import { fetchOrders } from '@/api/order'
import type { OrderItem, OrderView } from '@/api/types'

const route = useRoute()
const router = useRouter()

/**
 * 六个入口本质是**同一个订单列表按状态筛**。
 * 「买过」= 付过款且没取消；「待评价」在没有评价系统前只能映射到已完成订单，页面上会明说。
 */
const TABS = [
  { key: 'all', label: '全部', status: undefined as string | undefined },
  { key: 'pending', label: '待付款', status: 'PENDING_PAYMENT' },
  { key: 'paid', label: '已付款', status: 'PAID,SHIPPED' },
  { key: 'bought', label: '买过', status: 'PAID,SHIPPED,COMPLETED,REFUNDING,REFUNDED' },
  { key: 'aftersale', label: '退款/售后', status: 'REFUNDING,REFUNDED' },
  { key: 'review', label: '待评价', status: 'COMPLETED' },
]

const size = 10
const page = ref(1)
const total = ref(0)
const orders = ref<OrderView[]>([])
const loading = ref(false)
const reviewVisible = ref(false)
const reviewItem = ref<OrderItem | null>(null)

const activeTab = computed(() => {
  const tab = route.query.tab
  return typeof tab === 'string' && TABS.some((t) => t.key === tab) ? tab : 'all'
})
const current = computed(() => TABS.find((t) => t.key === activeTab.value) ?? TABS[0])

async function load() {
  loading.value = true
  try {
    const result = await fetchOrders({
      page: page.value,
      size,
      status: current.value.status,
    })
    orders.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

// Tab 存在 URL 里（刷新能保持）；切 Tab 时从第一页重新来
watch(activeTab, () => {
  page.value = 1
  load()
}, { immediate: true })

function switchTab(key: string) {
  if (key === activeTab.value) return
  router.replace({ query: key === 'all' ? {} : { tab: key } })
}

function onPageChange(next: number) {
  page.value = next
  load()
}

function openReview(item: OrderItem) {
  reviewItem.value = item
  reviewVisible.value = true
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div class="orders-page">
    <div class="block">
      <div class="tabs">
        <button
          v-for="tab in TABS"
          :key="tab.key"
          type="button"
          class="tab"
          :class="{ active: tab.key === activeTab }"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-loading="loading" class="list">
        <el-empty v-if="!loading && orders.length === 0" description="这里还没有订单" />

        <article v-for="order in orders" :key="order.order.id" class="order-card">
          <div class="card-head">
            <span class="order-no">{{ order.order.orderNo }}</span>
            <span class="order-time">{{ formatTime(order.order.createdAt) }}</span>
            <span class="status">{{ order.statusLabel }}</span>
          </div>

          <div class="card-body">
            <div v-for="item in order.items" :key="item.id" class="item-row">
              <div class="item-thumb">
                <ProductImage :src="item.productImage" :name="item.productName" :id="item.productId" />
              </div>
              <div class="item-info">
                <p class="item-name">{{ item.productName }}</p>
                <p v-if="item.specText" class="item-spec">{{ item.specText }}</p>
              </div>
              <span class="item-qty">× {{ item.quantity }}</span>
              <span class="price item-price">
                <span class="price-symbol">¥</span>
                <span class="price-num">{{ item.unitPrice.toFixed(2) }}</span>
              </span>
              <button
                v-if="order.order.status === 'COMPLETED'"
                type="button"
                class="review-btn"
                :class="{ done: item.reviewed }"
                :disabled="item.reviewed"
                @click="openReview(item)"
              >
                {{ item.reviewed ? '已评价' : '评价' }}
              </button>
            </div>
          </div>

          <div class="card-foot">
            <span class="count">共 {{ order.itemCount }} 件</span>
            <span v-if="order.order.discountAmount > 0" class="discount">
              优惠 -¥{{ order.order.discountAmount.toFixed(2) }}
            </span>
            <span class="total">
              合计
              <span class="price">
                <span class="price-symbol">¥</span>
                <span class="price-num total-num">{{ order.order.totalAmount.toFixed(2) }}</span>
              </span>
            </span>
            <RouterLink :to="`/order/${order.order.id}`" class="detail-btn">
              {{ order.order.status === 'PENDING_PAYMENT' ? '去支付' : '查看详情' }}
            </RouterLink>
          </div>
        </article>
      </div>

      <div v-if="total > size" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          background
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>

  <ReviewDialog v-model="reviewVisible" :order-item="reviewItem" @submitted="load" />
</template>

<style scoped>
.block {
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 6px 22px 18px;
}

/* ── Tab ── */
.tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--c-border);
  overflow-x: auto;
}

.tab {
  padding: 14px 0 12px;
  font-size: 14px;
  font-family: inherit;
  white-space: nowrap;
  color: var(--c-text-2);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.tab.active {
  color: var(--c-pink);
  border-bottom-color: var(--c-pink);
  font-weight: 600;
}

/* ── 订单卡片 ── */
.list {
  min-height: 100px;
}

.order-card {
  margin-top: 16px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  overflow: hidden;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 14px;
  background: var(--c-pink-tint);
  font-size: 12.5px;
  color: var(--c-text-2);
}

.order-no {
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  color: var(--c-text);
}

.order-time {
  color: var(--c-text-3);
}

/* 状态用中性色：粉色留给价格和主按钮 */
.status {
  margin-left: auto;
  font-weight: 600;
  color: var(--c-text);
}

.card-body {
  padding: 4px 14px;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--c-border);
}

.item-row:last-child {
  border-bottom: none;
}

.item-thumb {
  width: 52px;
  height: 52px;
  flex: none;
  border-radius: var(--radius-tag);
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 20px;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  margin: 0;
  font-size: 13.5px;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-spec {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.item-qty {
  flex: none;
  font-size: 13px;
  color: var(--c-text-2);
}

.item-price .price-num {
  font-size: 14px;
}

.review-btn {
  flex: none;
  padding: 2px 10px;
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

.discount {
  font-size: 12px;
  color: var(--c-pink);
}

.card-foot {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 14px;
  border-top: 1px solid var(--c-border);
}

.count {
  font-size: 12.5px;
  color: var(--c-text-3);
}

.total {
  margin-left: auto;
  font-size: 13px;
  color: var(--c-text-2);
}

.total-num {
  font-size: 18px;
  color: var(--c-pink);
}

.detail-btn {
  flex: none;
  padding: 7px 18px;
  font-size: 13px;
  color: #fff;
  background: var(--c-pink);
  border-radius: var(--radius-btn);
  text-decoration: none;
  transition: background 0.15s;
}

.detail-btn:hover {
  background: var(--c-pink-hover);
}

.pager {
  display: flex;
  justify-content: center;
  padding: 20px 0 4px;
}

@media (max-width: 720px) {
  .card-foot {
    flex-wrap: wrap;
  }

  .detail-btn {
    width: 100%;
    text-align: center;
  }
}
</style>
